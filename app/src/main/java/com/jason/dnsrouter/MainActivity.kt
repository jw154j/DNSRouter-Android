package com.jason.dnsrouter

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.*
import android.provider.Settings
import android.net.Uri
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private lateinit var systemStatus: TextView
    private lateinit var toggleBtn: Button
    private lateinit var foregroundBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var alwaysOnBtn: Button
    private lateinit var autoStartBtn: Button
    
    private lateinit var profile: EditText
    private lateinit var apiKey: EditText
    private lateinit var device: EditText
    private lateinit var useDeviceNameCb: CheckBox
    private lateinit var apiKeyLayout: LinearLayout
    private lateinit var removeApiKeyBtn: Button

    // Reliability indicators
    private lateinit var alwaysOnCircle: View
    private lateinit var batteryCircle: View
    private lateinit var autoStartCircle: View
    private lateinit var currentNetworkTv: TextView
    private lateinit var wifiExclusionTv: TextView
    private lateinit var wifiExclusionCircle: View
    
    // Security diagnostic indicators
    private lateinit var dnsCheckCircle: View
    private lateinit var browserWarningCircle: View
    private lateinit var connectivityCircle: View
    private lateinit var cloudCircle: View
    private lateinit var lastTestTv: TextView
    
    private val vpnResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == RESULT_OK) startVpn() }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkBatteryOptimizationOnStartup() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = Prefs(this)
        if (p.pinHash == null) setupPin(first = true)
        buildUi()
        
        // Check for updates or initial install
        checkVersionAndReport()
        
        // Sequential setup flow
        requestWifiPermission()
    }

    override fun onResume() { 
        super.onResume()
        updateUi()
    }

    private fun checkVersionAndReport() {
        val currentVersion = try { packageManager.getPackageInfo(packageName, 0).versionCode } catch (_: Exception) { 0 }
        if (p.lastVersionCode != currentVersion) {
            p.lastVersionCode = currentVersion
            showCompatibilityReport(isAuto = true)
        }
    }

    private fun buildUi() {
        val root = ScrollView(this).apply { isFillViewport = true }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 28) }
        root.addView(container)
        
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "DNS Router"; textSize = 30f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        header.addView(Button(this).apply { 
            text = "Report"
            textSize = 12f
            setOnClickListener { showCompatibilityReport(isAuto = false) }
        })
        container.addView(header)
        
        status = TextView(this).apply { textSize = 16f; setPadding(0, 12, 0, 12) }
        container.addView(status)

        // Reliability Section
        container.addView(TextView(this).apply { text = "Reliability Status"; textSize = 18f; setPadding(0, 16, 0, 8) })
        val relCard = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#08000000"))
        }
        
        alwaysOnCircle = View(this); relCard.addView(createReliabilityRow("Always-on VPN", alwaysOnCircle))
        batteryCircle = View(this); relCard.addView(createReliabilityRow("Battery Optimization", batteryCircle))
        autoStartCircle = View(this); relCard.addView(createReliabilityRow("Boot Auto-Start", autoStartCircle))
        
        currentNetworkTv = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 4) }
        relCard.addView(currentNetworkTv)
        
        val wifiExRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        wifiExclusionTv = TextView(this).apply { textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        wifiExclusionCircle = View(this).apply { layoutParams = LinearLayout.LayoutParams(24, 24) }
        wifiExRow.addView(wifiExclusionTv)
        wifiExRow.addView(wifiExclusionCircle)
        relCard.addView(wifiExRow)
        
        container.addView(relCard)

        // Security Diagnostics Section
        container.addView(TextView(this).apply { text = "Security Diagnostics"; textSize = 18f; setPadding(0, 24, 0, 8) })
        val secCard = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#08000000"))
        }
        
        dnsCheckCircle = View(this); secCard.addView(createReliabilityRow("DNS Protection Check", dnsCheckCircle))
        browserWarningCircle = View(this); secCard.addView(createReliabilityRow("Browser DNS Status", browserWarningCircle))
        connectivityCircle = View(this); secCard.addView(createReliabilityRow("NextDNS Connectivity", connectivityCircle))
        cloudCircle = View(this); secCard.addView(createReliabilityRow("Cloud Log Verification", cloudCircle))
        
        lastTestTv = TextView(this).apply { textSize = 12f; setPadding(0, 8, 0, 8); alpha = 0.7f }
        secCard.addView(lastTestTv)
        
        secCard.addView(Button(this).apply { 
            text = "Run Security Tests"
            textSize = 12f
            setOnClickListener { runSecurityTests() }
        })
        
        container.addView(secCard)
        
        // System Status Area (Simplified now that we have reliability circles)
        systemStatus = TextView(this).apply { 
            textSize = 14f; setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#10000000"))
        }
        container.addView(systemStatus)

        container.addView(TextView(this).apply { text = "Setup & Protection"; textSize = 18f; setPadding(0, 24, 0, 8) })
        
        // Buttons Grid (2 per line)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        // Row 1: Start/Stop and Auto-Start
        val row1 = createRow()
        toggleBtn = createGridButton("DNS Protection") { 
            val desired = !p.enabled
            auth { p.enabled = desired; if (desired) requestVpn() else stopVpn(); updateUi() }
        }
        autoStartBtn = createGridButton("Auto-Start") {
            p.autoStart = !p.autoStart
            updateUi()
            toast("Auto-start ${if (p.autoStart) "enabled" else "disabled"}")
        }
        row1.addView(toggleBtn); row1.addView(autoStartBtn)
        grid.addView(row1)

        // Row 2: Foreground and Battery
        val row2 = createRow()
        foregroundBtn = createGridButton("Foreground") {
            p.foregroundService = !p.foregroundService
            updateUi()
            toast("Foreground mode ${if (p.foregroundService) "ON" else "OFF"}")
        }
        batteryBtn = createGridButton("Battery Opt") {
            showBatteryDialog()
        }
        row2.addView(foregroundBtn); row2.addView(batteryBtn)
        grid.addView(row2)
        
        container.addView(TextView(this).apply { 
            text = "Note: Foreground service keeps the DNS protection active but does not mean the app screen is running."; 
            textSize = 11f; alpha = 0.6f; setPadding(8, 0, 8, 0)
        })

        // Row 3: Always-On and Wi-Fi Exclusions
        val row3 = createRow()
        alwaysOnBtn = createGridButton("Always-On VPN") {
            showSetup()
        }
        val wifiBtn = createGridButton("Wi-Fi Excl.") {
            auth { editExclusions() }
        }
        row3.addView(alwaysOnBtn); row3.addView(wifiBtn)
        grid.addView(row3)
        
        container.addView(grid)

        // Configuration Section
        container.addView(TextView(this).apply { text = "NextDNS Configuration"; textSize = 18f; setPadding(0, 32, 0, 8) })
        
        container.addView(TextView(this).apply { 
            text = "Select networks to protect with NextDNS. Unselected networks will bypass the VPN tunnel."; 
            textSize = 12f; alpha = 0.7f; setPadding(0, 0, 0, 8) 
        })
        
        container.addView(CheckBox(this).apply { 
            text = "Protect Wi-Fi Networks"; isChecked = p.protectWifi
            setOnCheckedChangeListener { _, isChecked -> p.protectWifi = isChecked; updateUi() }
        })
        container.addView(CheckBox(this).apply { 
            text = "Protect Mobile/Cellular Data"; isChecked = p.protectMobile
            setOnCheckedChangeListener { _, isChecked -> p.protectMobile = isChecked; updateUi() }
        })
        container.addView(CheckBox(this).apply { 
            text = "Protect Other Networks (Ethernet, USB, etc.)"; isChecked = p.protectOther
            setOnCheckedChangeListener { _, isChecked -> p.protectOther = isChecked; updateUi() }
        })

        container.addView(TextView(this).apply { text = "Profile ID"; setPadding(0, 8, 0, 4) })
        profile = EditText(this).apply { hint = "Profile ID"; setText(p.profile); isSingleLine = true }
        container.addView(profile)

        container.addView(TextView(this).apply { text = "Device Identifier"; setPadding(0, 16, 0, 4) })
        device = EditText(this).apply { 
            hint = "Mobile Device (if blank)"; setText(p.manualDeviceName); isSingleLine = true
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
            text = "Enables cloud analytics and logs. If omitted, only local device counters are shown."; 
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
                    setImageResource(android.R.drawable.ic_delete)
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
        }.apply { setPadding(0, 20, 0, 20) })
        
        container.addView(Button(this).apply { text = "DNS Activity Counters"; setOnClickListener { showStats() } })
        container.addView(Button(this).apply { text = "Change App PIN 🔒"; setOnClickListener { auth { setupPin(first = false) } } })
        
        setContentView(root); updateUi()
    }

    private fun createReliabilityRow(label: String, circle: View): View {
        val row = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(this).apply { text = label; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        circle.layoutParams = LinearLayout.LayoutParams(24, 24)
        row.addView(circle)
        return row
    }

    private fun updateCircleColor(view: View, colorStr: String) {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor(colorStr))
        view.background = shape
    }

    private fun createRow() = LinearLayout(this).apply { 
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun createGridButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 4, 4, 4) }
        setOnClickListener { onClick() }
    }

    private fun updateUi() {
        val ssid = currentSsid()
        val effectiveName = p.getEffectiveDeviceName()
        status.text = "Profile: ${p.profile.ifBlank { "(not set)" }}\nDevice: $effectiveName\nNetwork: ${ssid ?: "Mobile data / Wi-Fi name unavailable"}"
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isBatteryExempt = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(packageName)
        val isAlwaysOn = isAlwaysOnVpnEnabled()
        val isOptimal = p.enabled && p.autoStart && p.foregroundService && isBatteryExempt && isAlwaysOn
        
        systemStatus.text = buildString {
            append("✓ Constant Polling: NO (Efficient)\n")
            append("✓ Permanent NextDNS: NO (Standard DoH)\n")
            append("• Optimal Setup: ${if (isOptimal) "YES" else "NO"}\n")
            if (!isOptimal) {
                append("\n(Follow indicators and buttons to optimize performance)")
            } else {
                append("\nYour DNS protection is configured for maximum reliability.")
            }
        }

        // Reliability Circles
        updateCircleColor(alwaysOnCircle, if (isAlwaysOn) "#4CAF50" else "#F44336")
        updateCircleColor(batteryCircle, if (isBatteryExempt) "#4CAF50" else "#FFC107")
        updateCircleColor(autoStartCircle, if (p.autoStart) "#4CAF50" else "#F44336")
        
        currentNetworkTv.text = "Current Network: ${ssid ?: "Mobile data / Unavailable"}"
        
        val isMobile = ssid == null
        val globallyBypassed = (isMobile && !p.protectMobile) || (ssid != null && !p.protectWifi)
        val isSsidExcluded = ssid != null && p.excluded.any { it.equals(ssid, ignoreCase = true) }
        val isBypassed = globallyBypassed || isSsidExcluded
        
        wifiExclusionTv.text = "Network Protection: ${if (isBypassed) "Bypassed (Excluded)" else "Active"}"
        updateCircleColor(wifiExclusionCircle, if (isBypassed) "#9E9E9E" else "#4CAF50") // Gray if bypassed, Green if active

        // Button Colors
        setBtnStatus(toggleBtn, p.enabled, true)
        setBtnStatus(autoStartBtn, p.autoStart, true)
        setBtnStatus(foregroundBtn, p.foregroundService, true)
        setBtnStatus(batteryBtn, isBatteryExempt, false)
        setBtnStatus(alwaysOnBtn, isAlwaysOn, true)

        // API Key lifecycle logic
        val hasKey = p.apiKey.isNotEmpty()
        apiKey.isEnabled = !hasKey && p.encryptionWorks
        apiKeyLayout.alpha = if (apiKey.isEnabled) 1.0f else 0.5f
        removeApiKeyBtn.visibility = if (hasKey && p.encryptionWorks) View.VISIBLE else View.GONE
        
        if (!p.encryptionWorks) {
            apiKey.hint = "Security initialization failed"
            apiKey.setText("")
        } else if (hasKey) {
            apiKey.setText("********")
            apiKey.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        // Test Timestamp
        if (p.lastSuccessfulTest > 0) {
            val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            lastTestTv.text = "Last DNS Test: ${df.format(Date(p.lastSuccessfulTest))}"
        } else {
            lastTestTv.text = "Last DNS Test: Never"
            // Set initial state for circles
            updateCircleColor(dnsCheckCircle, "#9E9E9E")
            updateCircleColor(browserWarningCircle, "#9E9E9E")
            updateCircleColor(connectivityCircle, "#9E9E9E")
            updateCircleColor(cloudCircle, "#9E9E9E")
        }
    }

    private fun setBtnStatus(btn: Button, ok: Boolean, critical: Boolean) {
        if (ok) {
            btn.setBackgroundColor(Color.parseColor("#4CAF50"))
            btn.setTextColor(Color.WHITE)
        } else {
            btn.setBackgroundColor(if (critical) Color.parseColor("#F44336") else Color.parseColor("#FFC107"))
            btn.setTextColor(if (critical) Color.WHITE else Color.BLACK)
        }
    }

    private fun runSecurityTests() {
        val s = DnsStats(this)
        val before = s.get("test_seen")
        toast("Running diagnostics...")
        
        kotlin.concurrent.thread {
            var connectivityOk = false
            var cloudOk = false
            
            // 1. Local Protection & Leak Check
            try {
                InetAddress.getAllByName("diag.dnsrouter.check")
            } catch (_: Exception) {}
            
            Thread.sleep(1000) // Wait for packet processing
            val after = s.get("test_seen")
            val captured = after > before
            
            // 2. NextDNS Connectivity
            try {
                val conn = URL("https://test.nextdns.io").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                connectivityOk = conn.responseCode == 200
            } catch (_: Exception) {}
            
            // 3. Cloud Verification
            if (p.apiKey.isNotBlank() && p.profile.isNotBlank()) {
                try {
                    val logs = fetchNextDnsStats(p.profile, p.apiKey)
                    cloudOk = !logs.contains("Error")
                } catch (_: Exception) {}
            }
            
            runOnUiThread {
                p.lastSuccessfulTest = System.currentTimeMillis()
                updateCircleColor(dnsCheckCircle, if (p.enabled) "#4CAF50" else "#F44336")
                
                // Browser/Leak logic: 
                // If not enabled -> Gray. 
                // If enabled but NOT captured -> RED (Bypass). 
                // If enabled and captured -> YELLOW (Browser warning).
                if (!p.enabled) {
                    updateCircleColor(browserWarningCircle, "#9E9E9E")
                } else if (!captured) {
                    updateCircleColor(browserWarningCircle, "#F44336") // Leak detected!
                    toast("ALERT: DNS Bypass detected!")
                } else {
                    updateCircleColor(browserWarningCircle, "#FFC107") // Standard DoH warning
                }
                
                updateCircleColor(connectivityCircle, if (connectivityOk) "#4CAF50" else "#F44336")
                updateCircleColor(cloudCircle, if (cloudOk) "#4CAF50" else if (p.apiKey.isEmpty()) "#9E9E9E" else "#F44336")
                
                updateUi()
                toast("Tests complete")
            }
        }
    }

    private fun isAlwaysOnVpnEnabled(): Boolean {
        val alwaysOn = Settings.Secure.getString(contentResolver, "always_on_vpn_app")
        return alwaysOn == packageName
    }

    private fun showCompatibilityReport(isAuto: Boolean) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isBatteryExempt = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(packageName)
        
        val report = buildString {
            append("CORE SERVICES (Required)\n")
            append("• DNS-only VPN: Core Support\n")
            append("• NextDNS DoH: Core Support\n")
            append("• Encrypted Storage: ${if (p.encryptionWorks) "Required (Active)" else "FAILED"}\n\n")
            
            append("RECOMMENDED (For Reliability)\n")
            append("• Always-On VPN: ${if (isAlwaysOnVpnEnabled()) "Active" else "Highly Recommended"}\n")
            append("• Battery Exemption: ${if (isBatteryExempt) "Active" else "Recommended"}\n")
            append("• Boot Auto-Start: ${if (p.autoStart) "Active" else "Use"}\n")
            append("• Foreground Service: ${if (p.foregroundService) "Active" else "Use"}\n\n")
            
            append("OPTIONAL (Features)\n")
            append("• NextDNS API: ${if (p.apiKey.isNotEmpty()) "Configured" else "Optional"}\n")
            append("• Wi-Fi Exclusions: ${if (p.excluded.isNotEmpty()) "In Use (${p.excluded.size})" else "Optional"}\n")
            append("• PIN Protection: ${if (p.pinHash != null) "Active" else "Optional"}")
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (isAuto) "Version Update: Support Report" else "Device Support Report")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkBatteryOptimizationOnStartup() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Performance Warning")
                .setMessage("Android may stop DNS Routing in the background if battery optimization is enabled. For reliable automatic DNS Protection, allow DNS Router to run without battery optimization.")
                .setPositiveButton("Disable") { _, _ -> showBatteryDialog() }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun showBatteryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background Reliability")
            .setMessage("To prevent the system from killing the VPN service, you must disable battery optimization for DNS Router. On the next screen, find this app and select 'Don't optimize' or 'Unrestricted'.")
            .setPositiveButton("Proceed") { _, _ -> openBatterySettings() }
            .setNegativeButton("Cancel", null)
            .show()
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
        val ssid = currentSsid()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 16) }
        
        container.addView(TextView(this).apply { 
            text = "Excluded networks: ${p.excluded.size}"
            setPadding(0, 0, 0, 8)
            textSize = 14f
        })
        
        val e = EditText(this).apply { hint = "One Wi-Fi SSID per line"; setText(p.excluded.sorted().joinToString("\n")); minLines = 5; gravity = Gravity.TOP }
        container.addView(e)
        
        container.addView(Button(this).apply { 
            text = if (ssid != null) "Add Current: $ssid" else "Current Wi-Fi: Wi-Fi name unavailable"
            isEnabled = ssid != null
            setOnClickListener { 
                val current = e.text.toString().lines().toMutableList()
                if (ssid != null && !current.contains(ssid)) {
                    e.append(if (e.text.isEmpty()) ssid else "\n$ssid")
                }
            }
        })
        
        container.addView(TextView(this).apply { 
            text = "Note: SSIDs must match exactly. This app stays running but bypasses routing on these networks.";
            textSize = 12f; setPadding(0, 8, 0, 0)
        })

        AlertDialog.Builder(this).setTitle("Wi-Fi Exclusions").setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> p.excluded = e.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(); toast("Saved"); updateUi() }.show()
    }

    private fun showStats() {
        val s = DnsStats(this)
        val localText = "LOCAL COUNTERS:\nQueries captured: ${s.get("queries")}\nResponses sent: ${s.get("responses")}\nErrors: ${s.get("errors")}\nNXDOMAIN: ${s.get("nxdomain")}\nSERVFAIL: ${s.get("servfail")}"
        
        val d = AlertDialog.Builder(this).setTitle("DNS Activity").setMessage("$localText\n\nFetching NextDNS cloud analytics...").setPositiveButton("OK", null).setNeutralButton("Clear Local") { _, _ -> s.clear(); toast("Local statistics cleared") }.create()
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
        AlertDialog.Builder(this).setTitle("Always-On VPN Protection")
            .setMessage("Highly Recommended: Enabling 'Always-on VPN' in Android settings ensures DNS Router is automatically managed by the system.\n\n" +
                        "Risk: If NOT enabled, DNS queries may bypass NextDNS during network transitions or if the app is stopped.\n\n" +
                        "Optional: 'Block connections without VPN' provides strict protection by disabling internet if the VPN is not connected.")
            .setPositiveButton("Open Settings") { _, _ -> try { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } catch (_: Exception) {} }
            .setNegativeButton("Close", null).show()
    }

    private fun openBatterySettings() { try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName"))) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }

    private fun requestWifiPermission() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            checkBatteryOptimizationOnStartup()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = try {
        val wm = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        wm.connectionInfo.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    } catch (_: Exception) { null }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
