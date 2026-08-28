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
import java.util.concurrent.CompletableFuture
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import org.chromium.net.CronetEngine
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.chromium.net.CronetException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** DNS-only VPN with advanced diagnostics and transport support. */
class DnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.jason.dnsrouter.START"
        const val ACTION_STOP = "com.jason.dnsrouter.STOP"
        private const val TUN_V4 = "10.111.111.2"
        private const val DNS_V4 = "10.111.111.1"
        private const val TUN_V6 = "fd00:111:111::2"
        private const val DNS_V6 = "fd00:111:111::1"
        
        private const val CHAN_ID = "dns_alerts"
        private const val NOTIF_ID = 7
        private const val ALERT_ID = 8
    }

    private val prefs by lazy { Prefs(this) }
    private val stats by lazy { DnsStats(this) }
    private val pool = Executors.newFixedThreadPool(4)
    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var bypass = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private var cronet: CronetEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initCronet()
        Log.i("DnsVpn", "Service created. Transport=${prefs.dnsTransport} IPVer=${prefs.ipVersion}")
    }

    private fun initCronet() {
        try {
            val builder = CronetEngine.Builder(this)
            builder.enableQuic(true)
            builder.enableHttp2(true)
            cronet = builder.build()
        } catch (e: Exception) {
            Log.e("DnsVpn", "Failed to init Cronet", e)
        }
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
        val b = Builder()
            .setSession("DNS Router")
            .setMtu(1500)
            .addAddress(TUN_V4, 32)
            .addAddress(TUN_V6, 128)
            .addRoute(DNS_V4, 32)
            .addRoute(DNS_V6, 128)
        
        // Exclude self to prevent infinite loop for libraries like Cronet
        try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
        
        prefs.excludedApps.forEach { 
            try { b.addDisallowedApplication(it) } catch (_: Exception) {} 
        }
        
        // Respect IP Version settings for DNS Server assignment
        if (!bypass) {
            if (prefs.ipVersion == 0 || prefs.ipVersion == 1 || prefs.ipVersion == 2) b.addDnsServer(DNS_V4)
            if (prefs.ipVersion == 0 || prefs.ipVersion == 1 || prefs.ipVersion == 3) b.addDnsServer(DNS_V6)
        }
        
        val localTun = try { b.establish() } catch (e: Exception) {
            Log.e("DnsVpn", "Failed to establish tunnel", e)
            null
        }
        
        if (localTun == null) {
            stats.inc("errors")
            sendAlert("VPN unavailable", "Could not establish VPN tunnel.")
            return
        }
        tun = localTun
        running = true
        updateForegroundNotification(if (bypass) "Excluded Network: DNS bypass" else "Encrypted DNS protection active")
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
            updateForegroundNotification(if (bypass) "Excluded Network: DNS bypass" else "Encrypted DNS protection active")
        }
    }

    private fun evaluateNetwork() {
        bypass = shouldBypass()
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
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        try { Thread.sleep(300) } catch (_: Exception) {}
        establishTunnel()
    }

    private data class UdpQuery(val packet: ByteArray, val dns: ByteArray, val srcPort: Int, val ipv6: Boolean, val src: ByteArray, val dst: ByteArray)

    private fun packetLoop(localTun: ParcelFileDescriptor) {
        val input = FileInputStream(localTun.fileDescriptor)
        val output = FileOutputStream(localTun.fileDescriptor)
        val buf = ByteArray(65535)
        while (running) {
            try {
                val n = input.read(buf)
                if (n <= 0) break
                val q = parseUdpDns(buf, n) ?: continue
                pool.execute { handleDns(q, output) }
            } catch (e: Exception) {
                if (running) try { Thread.sleep(100) } catch (_: Exception) {}
            }
        }
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
            val udpLen = u16(p, ihl + 4); val dnsLen = udpLen - 8
            if (dnsLen < 12 || ihl + 8 + dnsLen > n) return null
            return UdpQuery(p.copyOf(n), p.copyOfRange(ihl + 8, ihl + 8 + dnsLen), srcPort, false, p.copyOfRange(12, 16), p.copyOfRange(16, 20))
        }
        if (version == 6 && n >= 48) {
            var nextHeader = p[6].toInt() and 0xFF; var offset = 40
            while (nextHeader == 0 || nextHeader == 43 || nextHeader == 60) {
                if (offset + 8 > n) return null
                val extLen = (p[offset + 1].toInt() and 0xFF + 1) * 8
                nextHeader = p[offset].toInt() and 0xFF; offset += extLen
            }
            if (nextHeader != 17 || offset + 8 > n) return null
            val srcPort = u16(p, offset); val dstPort = u16(p, offset + 2)
            if (dstPort != 53) return null
            val dnsLen = u16(p, offset + 4) - 8
            if (dnsLen < 12 || offset + 8 + dnsLen > n) return null
            return UdpQuery(p.copyOf(n), p.copyOfRange(offset + 8, offset + 8 + dnsLen), srcPort, true, p.copyOfRange(8, 24), p.copyOfRange(24, 40))
        }
        return null
    }

    private fun handleDns(q: UdpQuery, output: OutputStream) {
        val domain = parseDomainName(q.dns) ?: "unknown"
        stats.inc("queries")
        
        if (domain.contains("diag.dnsrouter.check")) {
            stats.inc("test_seen")
        }

        try {
            val response = resolveDns(q.dns)
            if (response == null) {
                stats.inc("errors")
                stats.logQuery(domain, "failed")
                runDiagnostics(domain)
                return
            }
            val rcode = if (response.size >= 4) response[3].toInt() and 0x0F else -1
            val status = when (rcode) {
                0 -> "allowed"
                3 -> "blocked"
                else -> "failed"
            }
            if (rcode == 3) stats.inc("nxdomain")
            if (rcode == 2) stats.inc("servfail")
            stats.inc("responses")
            stats.logQuery(domain, status)

            val packet = if (q.ipv6) buildUdp6Response(q, response) else buildUdp4Response(q, response)
            synchronized(output) {
                output.write(packet)
                output.flush()
            }
        } catch (e: Exception) {
            Log.e("DnsVpn", "Error handling DNS", e)
            stats.inc("errors")
            stats.logQuery(domain, "failed")
        }
    }

    private fun resolveDns(dns: ByteArray): ByteArray? {
        val transport = prefs.dnsTransport
        return when (transport) {
            0 -> dohQuery(dns)       // DoH
            1 -> doh3Query(dns)      // DoH3
            2 -> dotQuery(dns)       // DoT
            3 -> doqQuery(dns)       // DoQ
            else -> dohQuery(dns)
        }
    }

    private fun dohQuery(dns: ByteArray): ByteArray? {
        val host = "dns.nextdns.io"
        val path = getDnsPath()
        val ips = getDnsIps()
        
        for (ip in ips) {
            val response = dohQuerySingle(ip, host, path, dns)
            if (response != null) return response
        }
        return null
    }

    private fun dotQuery(dns: ByteArray): ByteArray? {
        val profile = prefs.profile.trim().trim('/')
        if (profile.isEmpty()) return null
        val sniHost = "$profile.dns.nextdns.io"
        val ips = getDnsIps()
        for (ip in ips) {
            try {
                val raw = Socket()
                protect(raw)
                raw.connect(InetSocketAddress(ip, 853), 4000)
                raw.soTimeout = 4000
                val ssl = SSLContext.getDefault().socketFactory.createSocket(raw, sniHost, 853, true) as SSLSocket
                ssl.startHandshake()
                val out = DataOutputStream(ssl.outputStream)
                out.writeShort(dns.size)
                out.write(dns)
                out.flush()
                val ins = DataInputStream(ssl.inputStream)
                val len = ins.readUnsignedShort()
                val response = ByteArray(len)
                ins.readFully(response)
                ssl.close()
                return response
            } catch (e: Exception) {
                Log.w("DnsVpn", "DoT $ip failed: ${e.message}")
            }
        }
        return null
    }

    private fun doh3Query(dns: ByteArray): ByteArray? {
        val engine = cronet ?: return dohQuery(dns)
        val host = "dns.nextdns.io"
        val path = getDnsPath()
        val url = "https://$host$path"
        
        val future = CompletableFuture<ByteArray?>()
        val callback = object : UrlRequest.Callback() {
            private val bytes = ByteArrayOutputStream()
            override fun onRedirectReceived(r: UrlRequest, i: UrlResponseInfo, n: String) { r.followRedirect() }
            override fun onResponseStarted(r: UrlRequest, i: UrlResponseInfo) { r.read(ByteBuffer.allocateDirect(32768)) }
            override fun onReadCompleted(r: UrlRequest, i: UrlResponseInfo, b: ByteBuffer) {
                b.flip(); val chunk = ByteArray(b.remaining()); b.get(chunk); bytes.write(chunk)
                b.clear(); r.read(b)
            }
            override fun onSucceeded(r: UrlRequest, i: UrlResponseInfo) { future.complete(bytes.toByteArray()) }
            override fun onFailed(r: UrlRequest, i: UrlResponseInfo, e: CronetException) { future.complete(null) }
            override fun onCanceled(r: UrlRequest, i: UrlResponseInfo) { future.complete(null) }
        }
        
        val request = engine.newUrlRequestBuilder(url, callback, pool)
            .setHttpMethod("POST")
            .addHeader("Content-Type", "application/dns-message")
            .addHeader("Accept", "application/dns-message")
            .setUploadDataProvider(org.chromium.net.UploadDataProviders.create(dns), pool)
            .build()
        request.start()
        return try { future.get(5, TimeUnit.SECONDS) } catch (_: Exception) { null }
    }

    private fun doqQuery(dns: ByteArray): ByteArray? {
        // DoQ is complex to implement via Cronet as it's not standard HTTPS.
        // Fallback to DoT for now as a safer alternative in this implementation.
        return dotQuery(dns)
    }

    private fun getDnsIps(): List<String> {
        val ipv4 = listOf("45.90.28.0", "45.90.30.0")
        val ipv6 = listOf("2a07:a8c0::", "2a07:a8c1::")
        return when (prefs.ipVersion) {
            1 -> ipv4 + ipv6    // Dual
            2 -> ipv4           // v4 only
            3 -> ipv6           // v6 only
            else -> ipv4 + ipv6 // Auto (tries both)
        }
    }

    private fun getDnsPath(): String {
        val profile = prefs.profile.trim().trim('/')
        if (profile.isEmpty()) return "/unconfigured"
        val deviceName = prefs.getEffectiveDeviceName()
        val device = urlPathSegment(deviceName)
        return if (device.isEmpty()) "/$profile" else "/$profile/$device"
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
            if (status !in 200..299) { ssl.close(); return null }
            val length = Regex("(?im)^Content-Length:\\s*(\\d+)\\s*$").find(header)?.groupValues?.get(1)?.toIntOrNull()
            val response = if (length != null) readExactly(input, length) else input.readBytes()
            ssl.close(); return response
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) {}
            return null
        }
    }

    private fun runDiagnostics(domain: String) {
        thread {
            val internetUp = try { URL("https://www.google.com/generate_204").openConnection().apply { connectTimeout = 3000 }.getInputStream(); true } catch (_: Exception) { false }
            if (internetUp) {
                // Internet is up, but encrypted DNS failed. Try fallback.
                val fallbackWorked = dotQuery(ByteBuffer.allocate(12).apply { putShort(0); putShort(0x0100); putShort(1); putShort(0); putShort(0); putShort(0); put(3); put("com".toByteArray()); put(0) }.array()) != null
                if (!fallbackWorked) {
                    sendAlert("Network Blocking Detected", "The current network may be blocking encrypted DNS. Internet is working, but multiple DNS protocols failed.")
                }
            } else {
                sendAlert("No Internet Connection", "DNS Router lost its encrypted DNS connection. DNS traffic is blocked until protection is restored.")
            }
        }
    }

    private fun sendAlert(title: String, text: String) {
        val n = Notification.Builder(this, CHAN_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_ID, n)
    }

    private fun updateForegroundNotification(text: String) {
        if (!prefs.foregroundService) return
        val n = Notification.Builder(this, "dns")
            .setContentTitle("DNS Router").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIF_ID, n)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("dns", "Service Status", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHAN_ID, "Security Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun parseDomainName(dns: ByteArray): String? {
        if (dns.size < 12) return null
        val sb = StringBuilder(); var pos = 12
        try {
            while (pos < dns.size) {
                val len = dns[pos].toInt() and 0xFF
                if (len == 0) break
                if (pos + 1 + len > dns.size) return null
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dns, pos + 1, len, StandardCharsets.US_ASCII))
                pos += 1 + len
            }
            return sb.toString()
        } catch (_: Exception) { return null }
    }

    private fun readHttpHeaders(input: InputStream): String? {
        val bytes = ByteArrayOutputStream(); var state = 0
        while (bytes.size() < 16384) {
            val c = input.read(); if (c < 0) return null
            bytes.write(c); state = when {
                state == 0 && c == '\r'.code -> 1
                state == 1 && c == '\n'.code -> 2
                state == 2 && c == '\r'.code -> 3
                state == 3 && c == '\n'.code -> 4
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
    private fun u16(a: ByteArray, i: Int) = ((a[i].toInt() and 255) shl 8) or (a[i + 1].toInt() and 255)
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
        put16(o, 40, 53); put16(o, 42, q.srcPort); put16(o, 44, 8 + dns.size); put16(o, 46, udpChecksum6(o, 40, 8 + dns.size)); System.arraycopy(dns, 0, o, 48, dns.size); return o
    }
    private fun put16(a: ByteArray, i: Int, v: Int) { a[i] = (v ushr 8).toByte(); a[i + 1] = v.toByte() }
    private fun checksum(a: ByteArray, off: Int, len: Int): Int { var s = 0L; var i = off; while (i < off + len) { s += u16(a, i); i += 2 }; return fold(s) }
    private fun udpChecksum4(a: ByteArray, off: Int, len: Int): Int { var s = 0L; for (i in 12 until 20 step 2) s += u16(a, i); s += 17; s += len; var i = off; while (i < off + len - 1) { s += u16(a, i); i += 2 }; if (len % 2 == 1) s += (a[off + len - 1].toInt() and 255) shl 8; return fold(s) }
    private fun udpChecksum6(a: ByteArray, off: Int, len: Int): Int { var s = 0L; for (i in 8 until 40 step 2) s += u16(a, i); s += (len ushr 16) and 65535; s += len and 65535; s += 17; var i = off; while (i < off + len - 1) { s += u16(a, i); i += 2 }; if (len % 2 == 1) s += (a[off + len - 1].toInt() and 255) shl 8; return fold(s) }
    private fun fold(value: Long): Int { var s = value; while (s ushr 16 != 0L) s = (s and 65535) + (s ushr 16); return s.inv().toInt() and 65535 }

    @Synchronized
    private fun stopVpn() {
        if (!running) return
        sendAlert("DNS Router protection Stopped", "Encrypted DNS is no longer active.")
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
