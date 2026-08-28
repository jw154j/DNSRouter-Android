package com.jason.dnsrouter

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.net.wifi.WifiInfo
import android.os.*
import android.util.Log
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** DNS-only VPN. It intentionally does not route normal Internet traffic through the TUN. */
class DnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.jason.dnsrouter.START"
        const val ACTION_STOP = "com.jason.dnsrouter.STOP"
        private const val TUN_V4 = "10.111.111.2"
        private const val DNS_V4 = "10.111.111.1"
        private const val TUN_V6 = "fd00:111:111::2"
        private const val DNS_V6 = "fd00:111:111::1"
    }

    private val prefs by lazy { Prefs(this) }
    private val stats by lazy { DnsStats(this) }
    private val pool = Executors.newFixedThreadPool(4)
    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var bypass = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i("DnsVpn", "Service created. Profile=${prefs.profile} Device=${prefs.getEffectiveDeviceName()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> if (prefs.enabled && !running) startVpn()
        }
        return START_STICKY
    }

    @Synchronized
    private fun startVpn() {
        if (running) return
        evaluateNetwork()
        establishTunnel()
        registerNetworkCallback()
    }

    @Synchronized
    private fun establishTunnel() {
        if (running) return
        Log.d("DnsVpn", "Establishing tunnel...")
        val b = Builder()
            .setSession("DNS Router")
            .setMtu(1500)
            .addAddress(TUN_V4, 32)
            .addAddress(TUN_V6, 128)
            .addRoute(DNS_V4, 32)
            .addRoute(DNS_V6, 128)
        if (!bypass) {
            b.addDnsServer(DNS_V4)
            b.addDnsServer(DNS_V6)
        }
        val localTun = try { b.establish() } catch (e: Exception) {
            Log.e("DnsVpn", "Failed to establish tunnel", e)
            null
        }
        if (localTun == null) {
            stats.inc("errors")
            startForegroundCompat("VPN unavailable")
            return
        }
        tun = localTun
        running = true
        startForegroundCompat(if (bypass) "Excluded Network: DNS bypass" else "NextDNS DNS protection active")
        thread(name = "dns-tun") { packetLoop(localTun) }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onNetworkChanged()
        }
        try { cm.registerNetworkCallback(req, networkCallback!!) } catch (_: Exception) { networkCallback = null }
    }

    private fun onNetworkChanged() {
        val newBypass = shouldBypass()
        if (newBypass != bypass) {
            bypass = newBypass
            rebuildTunnel()
        } else {
            startForegroundCompat(if (bypass) "Excluded Network: DNS bypass" else "NextDNS DNS protection active")
        }
    }

    private fun evaluateNetwork() {
        bypass = shouldBypass()
        Log.d("DnsVpn", "Evaluate network: bypass=$bypass")
    }

    private fun shouldBypass(): Boolean {
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            if (!prefs.protectMobile) return true
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            if (!prefs.protectWifi) return true
            val ssid = wifiSsid(caps) ?: return false
            return prefs.excluded.any { it.equals(ssid, ignoreCase = true) }
        } else {
            // Other transports like Ethernet, USB, etc.
            if (!prefs.protectOther) return true
        }
        
        return false
    }

    @Suppress("DEPRECATION")
    private fun wifiSsid(caps: NetworkCapabilities): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 31) caps.transportInfo as? WifiInfo
                else (getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo
            info?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
        } catch (_: SecurityException) { null }
    }

    @Synchronized
    private fun rebuildTunnel() {
        if (!running) return
        running = false
        Log.d("DnsVpn", "Rebuilding tunnel...")
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        // Small sleep to allow old thread to exit cleanly
        try { Thread.sleep(300) } catch (_: Exception) {}
        establishTunnel()
    }

    private data class UdpQuery(val packet: ByteArray, val dns: ByteArray, val srcPort: Int, val ipv6: Boolean, val src: ByteArray, val dst: ByteArray)

    private fun packetLoop(localTun: ParcelFileDescriptor) {
        Log.i("DnsVpn", "Packet loop started for FD ${localTun.fd}")
        val input = FileInputStream(localTun.fileDescriptor)
        val output = FileOutputStream(localTun.fileDescriptor)
        val buf = ByteArray(65535)
        while (running) {
            try {
                val n = input.read(buf)
                if (n < 0) {
                    Log.i("DnsVpn", "TUN read returned -1, exiting loop")
                    break
                }
                if (n == 0) continue
                
                // Very verbose log to see if ANY traffic is arriving
                // Log.v("DnsVpn", "Read $n bytes from TUN")
                
                val q = parseUdpDns(buf, n)
                if (q == null) continue

                Log.d("DnsVpn", "Captured DNS query: ${q.dns.size} bytes (IPv6=${q.ipv6})")
                pool.execute { handleDns(q, output) }
            } catch (e: Exception) {
                if (running) {
                    Log.e("DnsVpn", "Loop error", e)
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                }
            }
        }
        Log.i("DnsVpn", "Packet loop stopped for FD ${localTun.fd}")
        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    private fun parseUdpDns(p: ByteArray, n: Int): UdpQuery? {
        if (n < 28) return null
        val version = (p[0].toInt() ushr 4) and 0xF
        if (version == 4) {
            val ihl = (p[0].toInt() and 0x0F) * 4
            if (n < ihl + 8 || (p[9].toInt() and 0xFF) != 17) return null
            
            val srcPort = u16(p, ihl); val dstPort = u16(p, ihl + 2)
            if (dstPort != 53) return null
            
            val udpLen = u16(p, ihl + 4)
            val dnsLen = udpLen - 8
            if (dnsLen < 12 || ihl + 8 + dnsLen > n) return null
            return UdpQuery(p.copyOf(n), p.copyOfRange(ihl + 8, ihl + 8 + dnsLen), srcPort, false,
                p.copyOfRange(12, 16), p.copyOfRange(16, 20))
        }
        if (version == 6 && n >= 48) {
            var nextHeader = p[6].toInt() and 0xFF
            var offset = 40
            
            while (nextHeader == 0 || nextHeader == 43 || nextHeader == 60) {
                if (offset + 8 > n) return null
                val extLen = (p[offset + 1].toInt() and 0xFF + 1) * 8
                nextHeader = p[offset].toInt() and 0xFF
                offset += extLen
            }
            
            if (nextHeader != 17 || offset + 8 > n) return null
            
            val srcPort = u16(p, offset); val dstPort = u16(p, offset + 2)
            if (dstPort != 53) return null
            
            val dnsLen = u16(p, offset + 4) - 8
            if (dnsLen < 12 || offset + 8 + dnsLen > n) return null
            return UdpQuery(p.copyOf(n), p.copyOfRange(offset + 8, offset + 8 + dnsLen), srcPort, true,
                p.copyOfRange(8, 24), p.copyOfRange(24, 40))
        }
        return null
    }

    private fun handleDns(q: UdpQuery, output: OutputStream) {
        stats.inc("queries")
        // Check for diagnostic test domain: diag.dnsrouter.check
        if (q.dns.size > 30) {
            val s = String(q.dns, StandardCharsets.US_ASCII)
            if (s.contains("diag") && s.contains("dnsrouter") && s.contains("check")) {
                stats.inc("test_seen")
                Log.i("DnsVpn", "Diagnostic test query detected!")
            }
        }
        try {
            val response = dohQuery(q.dns)
            if (response == null) {
                stats.inc("errors")
                return
            }
            val rcode = if (response.size >= 4) response[3].toInt() and 0x0F else -1
            if (rcode == 3) stats.inc("nxdomain")
            if (rcode == 2) stats.inc("servfail")
            stats.inc("responses")
            val packet = if (q.ipv6) buildUdp6Response(q, response) else buildUdp4Response(q, response)
            synchronized(output) {
                output.write(packet)
                output.flush()
            }
            Log.v("DnsVpn", "DNS response sent: RCODE=$rcode")
        } catch (e: Exception) {
            Log.e("DnsVpn", "Error handling DNS", e)
            stats.inc("errors")
        }
    }

    /** Protected TLS socket prevents the DoH connection from being captured by our own VPN. */
    private fun dohQuery(dns: ByteArray): ByteArray? {
        val host = "dns.nextdns.io"
        val profile = prefs.profile.trim().trim('/')
        if (profile.isEmpty()) return null
        val deviceName = prefs.getEffectiveDeviceName()
        val device = urlPathSegment(deviceName)
        val path = if (device.isEmpty()) "/$profile" else "/$profile/$device"
        
        // Use Anycast IPs directly first to avoid resolution loops.
        val ips = listOf("45.90.28.0", "45.90.30.0", "2a07:a8c0::", "2a07:a8c1::")
        for (ip in ips) {
            val response = dohQuerySingle(ip, host, path, dns)
            if (response != null) return response
        }
        
        // Final fallback: try to resolve via underlying network if still failing.
        val underlyingNetwork = cm.allNetworks.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n)
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        if (underlyingNetwork != null) {
            try {
                val resolved = underlyingNetwork.getAllByName(host)
                for (addr in resolved) {
                    val response = dohQuerySingle(addr.hostAddress ?: continue, host, path, dns)
                    if (response != null) return response
                }
            } catch (e: Exception) {
                Log.w("DnsVpn", "Fallback resolution failed: ${e.message}")
            }
        }

        return null
    }

    private fun dohQuerySingle(ip: String, host: String, path: String, dns: ByteArray): ByteArray? {
        val raw = Socket()
        try {
            protect(raw)
            raw.connect(InetSocketAddress(ip, 443), 4000)
            raw.soTimeout = 4000
            val ssl = SSLContext.getDefault().socketFactory.createSocket(raw, host, 443, true) as SSLSocket
            ssl.startHandshake()
            val request = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: $host\r\n")
                append("Content-Type: application/dns-message\r\n")
                append("Accept: application/dns-message\r\n")
                append("Content-Length: ${dns.size}\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            val out = ssl.outputStream
            out.write(request); out.write(dns); out.flush()
            val input = BufferedInputStream(ssl.inputStream)
            val header = readHttpHeaders(input) ?: run { ssl.close(); return null }
            val status = Regex("^HTTP/\\d\\.\\d\\s+(\\d+)", RegexOption.MULTILINE).find(header)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (status !in 200..299) {
                Log.w("DnsVpn", "DoH $ip status $status")
                ssl.close(); return null
            }
            val length = Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$").find(header)?.groupValues?.get(1)?.toIntOrNull()
            val response = if (length != null) readExactly(input, length) else input.readBytes()
            ssl.close()
            Log.d("DnsVpn", "DoH success $ip (${response.size} bytes)")
            return response
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) {}
            return null
        }
    }

    private fun readHttpHeaders(input: InputStream): String? {
        val bytes = ByteArrayOutputStream(); var state = 0
        while (bytes.size() < 16384) {
            val c = input.read(); if (c < 0) return null
            bytes.write(c)
            state = when {
                state == 0 && c == '\r'.code -> 1
                state == 1 && c == '\n'.code -> 2
                state == 2 && c == '\r'.code -> 3
                state == 3 && c == '\n'.code -> 4
                state == 4 -> 4
                else -> 0
            }
            if (state == 4) return bytes.toString(StandardCharsets.US_ASCII.name())
        }
        return null
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length); var off = 0
        while (off < length) { val n = input.read(out, off, length - off); if (n < 0) break; off += n }
        return if (off == length) out else out.copyOf(off)
    }

    private fun urlPathSegment(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun buildUdp4Response(q: UdpQuery, dns: ByteArray): ByteArray {
        val total = 28 + dns.size; val o = ByteArray(total)
        o[0] = 0x45; o[2] = (total ushr 8).toByte(); o[3] = total.toByte(); o[8] = 64; o[9] = 17
        System.arraycopy(q.dst, 0, o, 12, 4); System.arraycopy(q.src, 0, o, 16, 4)
        put16(o, 20, 53); put16(o, 22, q.srcPort); put16(o, 24, 8 + dns.size); System.arraycopy(dns, 0, o, 28, dns.size)
        put16(o, 10, checksum(o, 0, 20)); put16(o, 26, udpChecksum4(o, 20, 8 + dns.size)); return o
    }

    private fun buildUdp6Response(q: UdpQuery, dns: ByteArray): ByteArray {
        val total = 48 + dns.size; val o = ByteArray(total)
        o[0] = 0x60; put16(o, 4, 8 + dns.size); o[6] = 17; o[7] = 64
        System.arraycopy(q.dst, 0, o, 8, 16); System.arraycopy(q.src, 0, o, 24, 16)
        put16(o, 40, 53); put16(o, 42, q.srcPort); put16(o, 44, 8 + dns.size); put16(o, 46, udpChecksum6(o, 40, 8 + dns.size)); System.arraycopy(dns, 0, o, 48, dns.size)
        return o
    }

    private fun u16(a: ByteArray, i: Int) = ((a[i].toInt() and 255) shl 8) or (a[i + 1].toInt() and 255)
    private fun put16(a: ByteArray, i: Int, v: Int) { a[i] = (v ushr 8).toByte(); a[i + 1] = v.toByte() }
    private fun checksum(a: ByteArray, off: Int, len: Int): Int { var s = 0L; var i = off; while (i < off + len) { s += u16(a, i); i += 2 }; return fold(s) }
    private fun udpChecksum4(a: ByteArray, off: Int, len: Int): Int { var s = 0L; for (i in 12 until 20 step 2) s += u16(a, i); s += 17; s += len; var i = off; while (i < off + len - 1) { s += u16(a, i); i += 2 }; if (len % 2 == 1) s += (a[off + len - 1].toInt() and 255) shl 8; return fold(s) }
    private fun udpChecksum6(a: ByteArray, off: Int, len: Int): Int { var s = 0L; for (i in 8 until 40 step 2) s += u16(a, i); s += (len ushr 16) and 65535; s += len and 65535; s += 17; var i = off; while (i < off + len - 1) { s += u16(a, i); i += 2 }; if (len % 2 == 1) s += (a[off + len - 1].toInt() and 255) shl 8; return fold(s) }
    private fun fold(value: Long): Int { var s = value; while (s ushr 16 != 0L) s = (s and 65535) + (s ushr 16); return s.inv().toInt() and 65535 }

    private fun startForegroundCompat(text: String) {
        if (!prefs.foregroundService) {
            Log.w("DnsVpn", "Foreground service disabled by user. VPN may be unstable.")
            return
        }
        val n = Notification.Builder(this, "dns")
            .setContentTitle("DNS Router").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("dns", "DNS Router", NotificationManager.IMPORTANCE_LOW))
    }

    @Synchronized
    private fun stopVpn() {
        if (!running) return
        Log.d("DnsVpn", "Stopping VPN...")
        running = false
        try { networkCallback?.let { cm.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        networkCallback = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopVpn(); pool.shutdownNow(); super.onDestroy() }
}
