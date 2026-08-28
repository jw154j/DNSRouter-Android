package com.jason.dnsrouter

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.*
import android.provider.Settings
import android.net.Uri
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private lateinit var protection: TextView
    private lateinit var toggle: Switch
    private lateinit var profile: EditText
    private lateinit var apiKey: EditText
    private lateinit var device: EditText
    private lateinit var useDeviceNameCb: CheckBox
    private lateinit var apiKeyLayout: LinearLayout
    private lateinit var removeApiKeyBtn: Button
    
    private val vpnResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == RESULT_OK) startVpn() }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUi() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = Prefs(this)
        if (p.pinHash == null) setupPin(first = true)
        buildUi(); requestWifiPermission(); requestBatteryExemption()
    }

    override fun onResume() { super.onResume(); updateUi() }

    private fun buildUi() {
        val root = ScrollView(this).apply { isFillViewport = true }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 28) }
        root.addView(container)
        
        container.addView(TextView(this).apply { text = "DNS Router"; textSize = 30f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, 12, 0, 12) }; container.addView(status)
        protection = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 16) }; container.addView(protection)
        
        toggle = Switch(this).apply {
            text = "DNS protection (PIN required)"; isChecked = p.enabled
            setOnClickListener { val desired = isChecked; auth { p.enabled = desired; if (desired) requestVpn() else stopVpn(); updateUi() } }
        }; container.addView(toggle)

        container.addView(TextView(this).apply { text = "NextDNS Profile ID"; setPadding(0, 16, 0, 4) })
        profile = EditText(this).apply { hint = "e.g. da8163"; setText(p.profile); isSingleLine = true }
        container.addView(profile)

        container.addView(TextView(this).apply { text = "Device Name"; setPadding(0, 16, 0, 4) })
        device = EditText(this).apply { 
            hint = "e.g. My Phone"; setText(p.manualDeviceName); isSingleLine = true
            visibility = if (p.useDeviceName) View.GONE else View.VISIBLE
        }
        container.addView(device)
        
        useDeviceNameCb = CheckBox(this).apply {
            text = "Use this device's name"; isChecked = p.useDeviceName
            setOnCheckedChangeListener { _, isChecked -> 
                device.visibility = if (isChecked) View.GONE else View.VISIBLE
            }
        }
        container.addView(useDeviceNameCb)

        container.addView(TextView(this).apply { text = "API Key (optional)"; setPadding(0, 16, 0, 4) })
        container.addView(TextView(this).apply { 
            text = "Enables cloud analytics and logs in this app. If omitted, only local device counters are shown."; 
            textSize = 12f; alpha = 0.7f; setPadding(0, 0, 0, 8) 
        })
        
        apiKeyLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        apiKey = EditText(this).apply { 
            hint = "NextDNS API Key"; setText(p.apiKey); isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val showKeyBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setOnClickListener {
                if (apiKey.transformationMethod == null) {
                    apiKey.transformationMethod = PasswordTransformationMethod.getInstance()
                    setImageResource(android.R.drawable.ic_menu_view)
                } else {
                    apiKey.transformationMethod = null
                    setImageResource(android.R.drawable.ic_delete) // Using standard icons for simple UI
                }
                apiKey.setSelection(apiKey.text.length)
            }
        }
        apiKeyLayout.addView(apiKey); apiKeyLayout.addView(showKeyBtn)
        container.addView(apiKeyLayout)

        removeApiKeyBtn = Button(this).apply {
            text = "Remove API Key"; visibility = if (p.apiKey.isNotEmpty()) View.VISIBLE else View.GONE
            setOnClickListener { auth { p.apiKey = ""; apiKey.setText(""); updateUi(); toast("API Key removed") } }
        }
        container.addView(removeApiKeyBtn)

        container.addView(Button(this).apply { 
            text = "Save Configuration 🔒"; setOnClickListener { 
                auth { 
                    p.profile = profile.text.toString().trim()
                    p.manualDeviceName = device.text.toString().trim()
                    p.useDeviceName = useDeviceNameCb.isChecked
                    if (apiKey.isEnabled && apiKey.text.isNotEmpty()) {
                        p.apiKey = apiKey.text.toString().trim()
                    }
                    toast("Configuration saved")
                    updateUi()
                } 
            } 
        })
        
        container.addView(Button(this).apply { text = "Wi-Fi exclusions 🔒"; setOnClickListener { auth { editExclusions() } } })
        container.addView(Button(this).apply { text = "DNS activity"; setOnClickListener { showStats() } })
        container.addView(Button(this).apply { text = "Protection setup"; setOnClickListener { showSetup() } })
        container.addView(Button(this).apply { text = "Change PIN 🔒"; setOnClickListener { auth { setupPin(first = false) } } })
        container.addView(Button(this).apply { text = "Battery optimization"; setOnClickListener { openBatterySettings() } })
        
        setContentView(root); updateUi()
    }

    private fun updateUi() {
        val ssid = currentSsid()
        val effectiveName = p.getEffectiveDeviceName()
        status.text = "Profile: ${p.profile.ifBlank { "(not set)" }}\nDevice: $effectiveName\nNetwork: ${ssid ?: "Mobile data / unavailable"}\nConfigured: Always-on VPN must be enabled in Android VPN settings"
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val encryptStatus = if (p.encryptionWorks) "" else "\n• CONFIG ERROR: Encryption failed"
        protection.text = "Protection checks\n• App enabled: ${if (p.enabled) "YES" else "NO"}\n• Battery optimization: ${if (Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(packageName)) "DISABLED" else "ENABLED"}\n• Wi-Fi exclusions: ${p.excluded.size}$encryptStatus"
        
        toggle.isChecked = p.enabled
        
        // API Key lifecycle logic
        val hasKey = p.apiKey.isNotEmpty()
        apiKey.isEnabled = !hasKey && p.encryptionWorks
        apiKeyLayout.alpha = if (apiKey.isEnabled) 1.0f else 0.5f
        removeApiKeyBtn.visibility = if (hasKey && p.encryptionWorks) View.VISIBLE else View.GONE
        
        if (!p.encryptionWorks) {
            apiKey.hint = "Security initialization failed"
            apiKey.setText("")
        } else if (hasKey) {
            apiKey.setText("********") // Mask for security when locked
            apiKey.transformationMethod = PasswordTransformationMethod.getInstance()
        }
    }

    private fun auth(onSuccess: () -> Unit) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "PIN" }
        val d = AlertDialog.Builder(this).setTitle("Enter PIN").setView(input).setNegativeButton("Cancel", null).setPositiveButton("OK", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (p.checkPin(input.text.toString())) { d.dismiss(); onSuccess() } else toast("Incorrect PIN") } }
        d.show()
    }

    private fun setupPin(first: Boolean) {
        val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "4–12 digit PIN" }
        AlertDialog.Builder(this).setTitle(if (first) "Create DNS Router PIN" else "Change PIN").setView(e).setCancelable(false)
            .setPositiveButton("Save") { _, _ -> val s = e.text.toString(); if (s.length in 4..12) { p.setPin(s); toast("PIN saved") } else { toast("PIN must be 4–12 digits") } }.show()
    }

    private fun requestVpn() { val i = VpnService.prepare(this); if (i != null) vpnResult.launch(i) else startVpn() }
    private fun startVpn() { ContextCompat.startForegroundService(this, Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START)) }
    private fun stopVpn() { startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP)) }

    private fun editExclusions() {
        val e = EditText(this).apply { hint = "One Wi-Fi SSID per line"; setText(p.excluded.sorted().joinToString("\n")); minLines = 6 }
        AlertDialog.Builder(this).setTitle("Excluded Wi-Fi networks").setMessage("The VPN service stays running. On these SSIDs it bypasses DNS routing.").setView(e)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> p.excluded = e.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(); toast("Saved"); updateUi() }.show()
    }

    private fun showStats() {
        val s = DnsStats(this)
        val localText = "LOCAL COUNTERS:\nQueries captured: ${s.get("queries")}\nResponses sent: ${s.get("responses")}\nErrors: ${s.get("errors")}\nNXDOMAIN: ${s.get("nxdomain")}\nSERVFAIL: ${s.get("servfail")}"
        
        val d = AlertDialog.Builder(this).setTitle("DNS activity").setMessage("$localText\n\nFetching NextDNS cloud analytics...").setPositiveButton("OK", null).setNeutralButton("Clear Local") { _, _ -> s.clear(); toast("Local statistics cleared") }.create()
        d.show()

        val currentKey = p.apiKey
        val currentProfile = p.profile
        
        if (currentKey.isNotBlank() && currentProfile.isNotBlank()) {
            kotlin.concurrent.thread {
                try {
                    val cloudData = fetchNextDnsStats(currentProfile, currentKey)
                    runOnUiThread { if (d.isShowing) d.setMessage("$localText\n\nNEXTDNS CLOUD (Profile: $currentProfile):\n$cloudData") }
                } catch (_: Exception) {}
            }
        } else {
            d.setMessage("$localText\n\n(API Key and Profile ID required for cloud analytics)")
        }
    }

    private fun fetchNextDnsStats(profile: String, key: String): String {
        return try {
            val url = URL("https://api.nextdns.io/profiles/$profile/analytics/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("X-Api-Key", key)
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            if (conn.responseCode == 200) parseAnalyticsStatus(conn.inputStream.bufferedReader().readText())
            else "Error ${conn.responseCode}: ${conn.responseMessage}"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun parseAnalyticsStatus(json: String): String {
        val total = Regex("\"totalQueries\":\\s*(\\d+)").find(json)?.groupValues?.get(1) ?: "0"
        val blocked = Regex("\"blockedQueries\":\\s*(\\d+)").find(json)?.groupValues?.get(1) ?: "0"
        return "Total Queries: $total\nBlocked: $blocked"
    }

    private fun showSetup() {
        AlertDialog.Builder(this).setTitle("Protection setup").setMessage("1. Authorize the VPN when Android asks.\n2. In Android VPN settings, select DNS Router and enable Always-on VPN.\n3. If desired, enable Block connections without VPN in Android settings for strict fail-closed behavior.\n4. Allow DNS Router to ignore battery optimization.\n5. Allow location access if you want Wi-Fi SSID exclusions to work reliably.").setPositiveButton("VPN settings") { _, _ -> try { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } catch (_: Exception) {} }.setNegativeButton("Close", null).show()
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) openBatterySettings()
        }
    }
    private fun openBatterySettings() { try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName"))) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }

    private fun requestWifiPermission() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = try {
        val wm = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        wm.connectionInfo.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    } catch (_: Exception) { null }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
