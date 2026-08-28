package com.jason.dnsrouter

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.*
import android.provider.Settings
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
    private lateinit var pinManageBtn: Button
    private lateinit var appExclusionBtn: Button
    private lateinit var adminSwitchBtn: Button
    
    private lateinit var profile: EditText
    private lateinit var apiKey: EditText
    private lateinit var device: EditText
    private lateinit var useDeviceNameCb: CheckBox
    private lateinit var apiKeyLayout: LinearLayout
    private lateinit var removeApiKeyBtn: Button
    private lateinit var adminEmailEt: EditText
    private lateinit var adminPhoneEt: EditText
    private lateinit var saveConfigBtn: Button
    private lateinit var wifiExclusionBtn: Button
    
    private lateinit var adminSectionLabel: TextView
    private lateinit var adminEmailLabel: TextView
    private lateinit var adminPhoneLabel: TextView

    private var isAdminMode = false
    private var setupDialog: Dialog? = null

    private lateinit var alwaysOnCircle: View
    private lateinit var batteryCircle: View
    private lateinit var autoStartCircle: View
    private lateinit var currentNetworkTv: TextView
    private lateinit var wifiExclusionTv: TextView
    private lateinit var wifiExclusionCircle: View
    private lateinit var dnsCheckCircle: View
    private lateinit var browserWarningCircle: View
    private lateinit var connectivityCircle: View
    private lateinit var cloudCircle: View
    private lateinit var lastTestTv: TextView
    
    private val vpnResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == RESULT_OK) startVpn() }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkBatteryOptimizationOnStartup() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = Prefs(this)
        applyTheme(p.themeMode)
        isAdminMode = isAppManaged()
        
        // Validation: If Admin mode was chosen but setup was never finished (no PIN or contact info), reset to 0.
        if (p.controlMode == 2 && (p.pinHash == null || p.adminEmail.isBlank() || p.adminPhone.isBlank())) {
            p.controlMode = 0
            p.clearPin()
            p.adminEmail = ""
            p.adminPhone = ""
        }

        if (p.controlMode == 0 && !isAdminMode) {
            showControlModeSelection()
        } else {
            if (isAdminMode) p.controlMode = 2
            startMainApp()
        }
    }

    private fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun startMainApp() {
        buildUi(); checkVersionAndReport(); requestWifiPermission()
    }

    private fun showControlModeSelection() {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dialog = Dialog(this, if (isDark) android.R.style.Theme_Material_NoActionBar_Fullscreen else android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        setupDialog = dialog
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 40, 80, 40)
            setBackgroundColor(if (isDark) "#121212".toColorInt() else Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Welcome to DNS Router"; textSize = 28f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20); setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        })

        root.addView(TextView(this).apply {
            text = "Select your setup type to continue."; textSize = 16f
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 100); setTextColor(if (isDark) Color.LTGRAY else Color.GRAY)
        })

        // User Setup Option
        val userBtn = Button(this).apply {
            text = "USER SETUP\n(I control this device)"; textSize = 18f
            setPadding(40, 60, 40, 60); isAllCaps = true
            val btnColor = if (isDark) "#2C2C2C" else "#E0E0E0"
            setBackgroundColor(btnColor.toColorInt())
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            setOnClickListener {
                p.controlMode = 1
                dialog.dismiss()
                startMainApp()
            }
        }
        root.addView(userBtn)
        root.addView(TextView(this).apply {
            text = "You control DNS Router settings on this device."; textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, 10, 0, 80); setTextColor(if (isDark) Color.GRAY else Color.DKGRAY)
        })

        // Admin Setup Option
        val adminBtn = Button(this).apply {
            text = "ADMIN SETUP\n(Managed Device)"; textSize = 18f
            setPadding(40, 60, 40, 60); isAllCaps = true
            val btnColor = if (isDark) "#1976D2" else "#BBDEFB"
            setBackgroundColor(btnColor.toColorInt())
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            setOnClickListener {
                startAdminSetupFlow(dialog)
            }
        }
        root.addView(adminBtn)
        root.addView(TextView(this).apply {
            text = "A network administrator controls settings. PIN and contact info required."; textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, 10, 0, 0); setTextColor(if (isDark) Color.GRAY else Color.DKGRAY)
        })

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun startAdminSetupFlow(parentDialog: Dialog) {
        // Step 1: Set PIN
        showSetPinDialog {
            // Step 2: Mandatory Contact Info
            showAdminContactSetupDialog {
                p.controlMode = 2
                parentDialog.dismiss()
                startMainApp()
                toast("Admin Setup Complete")
            }
        }
    }

    private fun showAdminContactSetupDialog(onComplete: () -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 40)
        }
        container.addView(TextView(this).apply { text = "Administrator Contact Info"; textSize = 20f; setPadding(0, 0, 0, 20) })
        container.addView(TextView(this).apply { text = "Both email and phone are mandatory for Admin Mode."; textSize = 14f; setPadding(0, 0, 0, 40) })

        val emailEt = EditText(this).apply { hint = "Admin Email"; isSingleLine = true; inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val phoneEt = EditText(this).apply { hint = "Admin Phone"; isSingleLine = true; inputType = InputType.TYPE_CLASS_PHONE }
        container.addView(emailEt); container.addView(phoneEt)

        AlertDialog.Builder(this)
            .setTitle("Step 2: Admin Contact")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Finish Setup", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val e = emailEt.text.toString().trim()
                        val ph = phoneEt.text.toString().trim()
                        if (e.isEmpty() || ph.isEmpty()) {
                            toast("Both fields are required")
                        } else {
                            p.adminEmail = e
                            p.adminPhone = ph
                            dismiss()
                            onComplete()
                        }
                    }
                }
            }.show()
    }

    override fun onResume() { 
        super.onResume()
        if (p.controlMode != 0 || isAdminMode) updateUi() 
    }

    private fun checkVersionAndReport() {
        val currentVersion = try { packageManager.getPackageInfo(packageName, 0).longVersionCode } catch (_: Exception) { 0L }
        if (p.lastVersionCode != currentVersion.toInt()) { p.lastVersionCode = currentVersion.toInt(); showCompatibilityReport(isAuto = true) }
    }

    private fun buildUi() {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val cardBg = if (isDark) Color.parseColor("#1FFFFFFF") else Color.parseColor("#08000000")
        val statusBg = if (isDark) Color.parseColor("#2FFFFFFF") else Color.parseColor("#10000000")
        
        val root = ScrollView(this).apply { isFillViewport = true }
        val container = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 120 * (resources.displayMetrics.density).toInt())
        }
        root.addView(container)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "DNS Router"; textSize = 30f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        if (p.controlMode == 2 || isAdminMode) header.addView(Button(this).apply { text = "Support"; textSize = 12f; setOnClickListener { showSupportOptions() } })
        header.addView(Button(this).apply { text = "Report"; textSize = 12f; setOnClickListener { showCompatibilityReport(isAuto = false) } })
        container.addView(header)
        if (isAdminMode || p.controlMode == 2) container.addView(TextView(this).apply { text = "NETWORK ADMIN MODE ACTIVE"; setTextColor(Color.RED); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 8) })
        status = TextView(this).apply { textSize = 16f; setPadding(0, 12, 0, 12) }; container.addView(status)
        container.addView(TextView(this).apply { text = "Reliability Status"; textSize = 18f; setPadding(0, 16, 0, 8) })
        val relCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16); setBackgroundColor(cardBg) }
        alwaysOnCircle = View(this); relCard.addView(createReliabilityRow("Always-on VPN", alwaysOnCircle))
        batteryCircle = View(this); relCard.addView(createReliabilityRow("Battery Optimization", batteryCircle))
        autoStartCircle = View(this); relCard.addView(createReliabilityRow("Boot Auto-Start", autoStartCircle))
        currentNetworkTv = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 4) }; relCard.addView(currentNetworkTv)
        val wifiExRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        wifiExclusionTv = TextView(this).apply { textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        wifiExclusionCircle = View(this).apply { layoutParams = LinearLayout.LayoutParams(24, 24) }
        wifiExRow.addView(wifiExclusionTv); wifiExRow.addView(wifiExclusionCircle); relCard.addView(wifiExRow); container.addView(relCard)
        container.addView(TextView(this).apply { text = "Security Diagnostics"; textSize = 18f; setPadding(0, 24, 0, 8) })
        val secCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16); setBackgroundColor(cardBg) }
        dnsCheckCircle = View(this); secCard.addView(createReliabilityRow("DNS Protection Check", dnsCheckCircle))
        browserWarningCircle = View(this); secCard.addView(createReliabilityRow("Browser DNS Status", browserWarningCircle))
        connectivityCircle = View(this); secCard.addView(createReliabilityRow("NextDNS Connectivity", connectivityCircle))
        cloudCircle = View(this); secCard.addView(createReliabilityRow("Cloud Log Verification", cloudCircle))
        lastTestTv = TextView(this).apply { textSize = 12f; setPadding(0, 8, 0, 8); alpha = 0.7f }; secCard.addView(lastTestTv)
        secCard.addView(Button(this).apply { text = "Run Security Tests"; textSize = 12f; setOnClickListener { runSecurityTests() } }); container.addView(secCard)
        systemStatus = TextView(this).apply { textSize = 14f; setPadding(24, 16, 24, 16); setBackgroundColor(statusBg) }; container.addView(systemStatus)
        container.addView(TextView(this).apply { text = "Setup & Protection"; textSize = 18f; setPadding(0, 24, 0, 8) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = createRow()
        toggleBtn = createGridButton("DNS Protection") { val desired = !p.enabled; auth { p.enabled = desired; if (desired) requestVpn() else stopVpn(); updateUi() } }
        autoStartBtn = createGridButton("Auto-Start") { if (isAdminMode || p.controlMode == 2) { toast("Managed by Administrator"); return@createGridButton }; p.autoStart = !p.autoStart; updateUi(); toast("Auto-start ${if (p.autoStart) "enabled" else "disabled"}") }
        row1.addView(toggleBtn); row1.addView(autoStartBtn); grid.addView(row1)
        val row2 = createRow()
        foregroundBtn = createGridButton("Foreground") { if (isAdminMode || p.controlMode == 2) { toast("Managed by Administrator"); return@createGridButton }; p.foregroundService = !p.foregroundService; updateUi(); toast("Foreground mode ${if (p.foregroundService) "ON" else "OFF"}") }
        batteryBtn = createGridButton("Battery Opt") { showBatteryDialog() }
        row2.addView(foregroundBtn); row2.addView(batteryBtn); grid.addView(row2)
        container.addView(TextView(this).apply { text = "Note: Foreground service keeps the DNS protection active but does not mean the app screen is running."; textSize = 11f; alpha = 0.6f; setPadding(8, 0, 8, 0) })
        val row3 = createRow()
        alwaysOnBtn = createGridButton("Always-On VPN") { showSetup() }
        wifiExclusionBtn = createGridButton("Wi-Fi Excl.") { if (isAdminMode || p.controlMode == 2) { toast("Managed by Administrator"); return@createGridButton }; auth { editExclusions() } }
        row3.addView(alwaysOnBtn); row3.addView(wifiExclusionBtn); grid.addView(row3); container.addView(grid)
        
        appExclusionBtn = Button(this).apply { text = "App Exclusions 🔒"; setOnClickListener { auth { manageAppExclusions() } } }
        container.addView(appExclusionBtn)

        container.addView(TextView(this).apply { text = "NextDNS Configuration"; textSize = 18f; setPadding(0, 32, 0, 8) })
        container.addView(TextView(this).apply { text = "Select networks to protect with NextDNS. Unselected networks will bypass the VPN tunnel."; textSize = 12f; alpha = 0.7f; setPadding(0, 0, 0, 8) })
        container.addView(CheckBox(this).apply { text = "Protect Wi-Fi Networks"; isChecked = p.protectWifi; isEnabled = !(isAdminMode || p.controlMode == 2); setOnCheckedChangeListener { _, isChecked -> p.protectWifi = isChecked; updateUi() } })
        container.addView(CheckBox(this).apply { text = "Protect Mobile/Cellular Data"; isChecked = p.protectMobile; setOnCheckedChangeListener { _, isChecked -> p.protectMobile = isChecked; updateUi() } })
        container.addView(CheckBox(this).apply { text = "Protect Other Networks (Ethernet, USB, etc.)"; isChecked = p.protectOther; isEnabled = !(isAdminMode || p.controlMode == 2); setOnCheckedChangeListener { _, isChecked -> p.protectOther = isChecked; updateUi() } })
        container.addView(TextView(this).apply { text = "Profile ID"; setPadding(0, 8, 0, 4) })
        profile = EditText(this).apply { hint = "Profile ID"; setText(p.profile); isSingleLine = true; isEnabled = !(isAdminMode || p.controlMode == 2); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
        container.addView(profile)
        container.addView(TextView(this).apply { text = "Device Identifier"; setPadding(0, 16, 0, 4) })
        device = EditText(this).apply { hint = "Mobile Device (if blank)"; setText(p.manualDeviceName); isSingleLine = true; visibility = if (p.useDeviceName) View.GONE else View.VISIBLE; isEnabled = !(isAdminMode || p.controlMode == 2) }
        container.addView(device)
        useDeviceNameCb = CheckBox(this).apply { text = "Use this device's name"; isChecked = p.useDeviceName; isEnabled = !(isAdminMode || p.controlMode == 2); setOnCheckedChangeListener { _, isChecked -> device.visibility = if (isChecked) View.GONE else View.VISIBLE } }
        container.addView(useDeviceNameCb)
        container.addView(TextView(this).apply { text = "API Key (optional)"; setPadding(0, 16, 0, 4) })
        container.addView(TextView(this).apply { text = "Enables cloud analytics and logs. If omitted, only local device counters are shown."; textSize = 12f; alpha = 0.7f; setPadding(0, 0, 0, 8) })
        apiKeyLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        apiKey = EditText(this).apply { hint = "NextDNS API Key"; setText(p.apiKey); isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); isEnabled = !(isAdminMode || p.controlMode == 2) }
        val showKeyBtn = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_view); isEnabled = !(isAdminMode || p.controlMode == 2)
            setOnClickListener { if (apiKey.transformationMethod == null) { apiKey.transformationMethod = PasswordTransformationMethod.getInstance(); setImageResource(android.R.drawable.ic_menu_view) } else { apiKey.transformationMethod = null; setImageResource(android.R.drawable.ic_delete) }; apiKey.setSelection(apiKey.text.length) }
        }
        apiKeyLayout.addView(apiKey); apiKeyLayout.addView(showKeyBtn); container.addView(apiKeyLayout)
        removeApiKeyBtn = Button(this).apply { text = "Remove API Key"; visibility = if (p.apiKey.isNotEmpty() && !(isAdminMode || p.controlMode == 2)) View.VISIBLE else View.GONE; setOnClickListener { auth { p.apiKey = ""; apiKey.setText(""); updateUi(); toast("API Key removed") } } }
        container.addView(removeApiKeyBtn)
        
        adminSectionLabel = TextView(this).apply { text = "Administrator Settings"; textSize = 18f; setPadding(0, 24, 0, 8) }
        container.addView(adminSectionLabel)
        adminEmailLabel = TextView(this).apply { text = "Administrator Email (Required for support)"; setPadding(0, 8, 0, 4) }
        container.addView(adminEmailLabel)
        adminEmailEt = EditText(this).apply { hint = "admin@example.com"; setText(p.adminEmail); isSingleLine = true; isEnabled = false; setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
            setOnClickListener { if (!isEnabled && !isAdminMode && p.controlMode == 2) auth { isEnabled = true; adminPhoneEt.isEnabled = true; requestFocus(); val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager; imm.showSoftInput(this, 0) } } }
        container.addView(adminEmailEt)
        adminPhoneLabel = TextView(this).apply { text = "Administrator Phone (Required for support)"; setPadding(0, 16, 0, 4) }
        container.addView(adminPhoneLabel)
        adminPhoneEt = EditText(this).apply { hint = "+1234567890"; setText(p.adminPhone); isSingleLine = true; inputType = InputType.TYPE_CLASS_PHONE; isEnabled = false; setAutofillHints(View.AUTOFILL_HINT_PHONE)
            setOnClickListener { if (!isEnabled && !isAdminMode && p.controlMode == 2) auth { isEnabled = true; adminEmailEt.isEnabled = true; requestFocus(); val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager; imm.showSoftInput(this, 0) } } }
        container.addView(adminPhoneEt)
        
        saveConfigBtn = Button(this).apply { text = "Save Configuration 🔒"
            setOnClickListener { auth { 
                val email = adminEmailEt.text.toString().trim(); val phone = adminPhoneEt.text.toString().trim()
                val hasEmail = email.isNotEmpty(); val hasPhone = phone.isNotEmpty()
                if (p.controlMode == 2 && (hasEmail != hasPhone)) { toast("Email and Phone are both required if one is provided"); return@auth }
                if (p.controlMode == 2 && (!hasEmail || !hasPhone)) { toast("Email and Phone are both mandatory in Admin mode"); return@auth }
                p.profile = profile.text.toString().trim(); p.manualDeviceName = device.text.toString().trim(); p.useDeviceName = useDeviceNameCb.isChecked; p.adminEmail = email; p.adminPhone = phone
                if (apiKey.isEnabled && apiKey.text.isNotEmpty()) p.apiKey = apiKey.text.toString().trim(); toast("Configuration saved"); updateUi(); adminEmailEt.isEnabled = false; adminPhoneEt.isEnabled = false
            } }
        }.apply { setPadding(0, 20, 0, 20) }; container.addView(saveConfigBtn)
        
        adminSwitchBtn = Button(this).apply { 
            setOnClickListener { 
                if (p.controlMode == 2) {
                    auth {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Change to User Setup?")
                            .setMessage("WARNING: All settings will become unlocked and the PIN will be invalidated. Email and Phone data will be removed. Proceed?")
                            .setPositiveButton("Proceed") { _, _ ->
                                p.clearPin(); p.adminEmail = ""; p.adminPhone = ""
                                p.controlMode = 1; buildUi(); updateUi()
                                toast("Switched to User Control")
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                } else {
                    showSetPinDialog { 
                        showAdminContactSetupDialog {
                            p.controlMode = 2; buildUi(); updateUi()
                            toast("Now in Admin-controlled setup.")
                        }
                    }
                }
            } 
        }
        container.addView(adminSwitchBtn)
        container.addView(Button(this).apply { text = "DNS Activity Counters"; setOnClickListener { showStats() } })
        
        container.addView(TextView(this).apply { text = "App Theme"; textSize = 18f; setPadding(0, 32, 0, 8) })
        val themeSpinner = Spinner(this)
        val themes = arrayOf("System Default", "Light Mode", "Dark Mode")
        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        themeSpinner.setSelection(p.themeMode)
        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (p.themeMode != position) {
                    p.themeMode = position
                    applyTheme(position)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        container.addView(themeSpinner)

        pinManageBtn = Button(this).apply { isEnabled = !isAdminMode; setOnClickListener { managePin() } }; container.addView(pinManageBtn); setContentView(root); updateUi()
    }

    private fun createReliabilityRow(label: String, circle: View): View { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 4, 0, 4) }
        row.addView(TextView(this).apply { text = label; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        circle.layoutParams = LinearLayout.LayoutParams(24, 24); row.addView(circle); return row }
    private fun updateCircleColor(view: View, colorStr: String) { val shape = GradientDrawable(); shape.shape = GradientDrawable.OVAL; shape.setColor(Color.parseColor(colorStr)); view.background = shape }
    private fun createRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun createGridButton(label: String, onClick: () -> Unit) = Button(this).apply { text = label; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 4, 4, 4) }; setOnClickListener { onClick() } }

    private fun updateUi() {
        val ssid = currentSsid(); val effectiveName = p.getEffectiveDeviceName(); status.text = "Profile: ${p.profile.ifBlank { "(not set)" }}\nDevice: $effectiveName\nNetwork: ${ssid ?: "Mobile data / Wi-Fi name unavailable"}"
        val pm = getSystemService(POWER_SERVICE) as PowerManager; val isBatteryExempt = pm.isIgnoringBatteryOptimizations(packageName)
        val isAlwaysOn = isAlwaysOnVpnEnabled(); val isOptimal = p.enabled && p.autoStart && p.foregroundService && isBatteryExempt && isAlwaysOn
        systemStatus.text = buildString { append("✓ Constant Polling: NO (Efficient)\n✓ Permanent NextDNS: NO (Standard DoH)\n• Optimal Setup: ${if (isOptimal) "YES" else "NO"}\n")
            if (!isOptimal) append("\n(Follow indicators and buttons to optimize performance)") else append("\nYour DNS protection is configured for maximum reliability.") }
        updateCircleColor(alwaysOnCircle, if (isAlwaysOn) "#4CAF50" else "#F44336"); updateCircleColor(batteryCircle, if (isBatteryExempt) "#4CAF50" else "#FFC107"); updateCircleColor(autoStartCircle, if (p.autoStart) "#4CAF50" else "#F44336")
        currentNetworkTv.text = "Current Network: ${ssid ?: "Mobile data / Unavailable"}"
        val isMobile = ssid == null; val globallyBypassed = (isMobile && !p.protectMobile) || (ssid != null && !p.protectWifi)
        val isSsidExcluded = ssid != null && p.excluded.any { it.equals(ssid, ignoreCase = true) }; val isBypassed = globallyBypassed || isSsidExcluded
        wifiExclusionTv.text = "Wi-Fi Exclusion: ${if (isBypassed) "Excluded" else "Not Excluded"}"; updateCircleColor(wifiExclusionCircle, if (isBypassed) "#4CAF50" else "#9E9E9E")
        setBtnStatus(toggleBtn, p.enabled, critical = true); setBtnStatus(autoStartBtn, p.autoStart, critical = true); setBtnStatus(foregroundBtn, p.foregroundService, critical = true); setBtnStatus(batteryBtn, isBatteryExempt, critical = false); setBtnStatus(alwaysOnBtn, isAlwaysOn, critical = true)
        
        val isAdmin = p.controlMode == 2 || isAdminMode
        val vis = if (isAdmin) View.VISIBLE else View.GONE
        adminSectionLabel.visibility = vis; adminEmailLabel.visibility = vis; adminEmailEt.visibility = vis
        adminPhoneLabel.visibility = vis; adminPhoneEt.visibility = vis; pinManageBtn.visibility = vis
        
        val hasKey = p.apiKey.isNotEmpty(); apiKey.isEnabled = !hasKey && p.encryptionWorks && !isAdmin; apiKeyLayout.alpha = if (apiKey.isEnabled) 1.0f else 0.5f; removeApiKeyBtn.visibility = if (hasKey && p.encryptionWorks && !isAdmin) View.VISIBLE else View.GONE
        if (!p.encryptionWorks) { apiKey.hint = "Security initialization failed"; apiKey.setText("") } else if (hasKey) { apiKey.setText("********"); apiKey.transformationMethod = PasswordTransformationMethod.getInstance() }
        if (p.lastSuccessfulTest > 0) lastTestTv.text = "Last DNS Test: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(p.lastSuccessfulTest))}"
        else { lastTestTv.text = "Last DNS Test: Never"; updateCircleColor(dnsCheckCircle, "#9E9E9E"); updateCircleColor(browserWarningCircle, "#9E9E9E"); updateCircleColor(connectivityCircle, "#9E9E9E"); updateCircleColor(cloudCircle, "#9E9E9E") }
        
        pinManageBtn.text = if (p.pinHash == null) "Set App PIN" else "Manage App PIN 🔒"; appExclusionBtn.text = "App Exclusions (${p.excludedApps.size}) 🔒"
        adminSwitchBtn.text = if (p.controlMode == 2) "Change to User Setup" else "Change to Admin-controlled setup"
        saveConfigBtn.isEnabled = !isAdmin || adminEmailEt.isEnabled
        if (p.controlMode == 1) { adminEmailEt.alpha = 0.3f; adminPhoneEt.alpha = 0.3f; adminEmailEt.isEnabled = false; adminPhoneEt.isEnabled = false } else { adminEmailEt.alpha = 1.0f; adminPhoneEt.alpha = 1.0f }
    }

    private fun setBtnStatus(btn: Button, ok: Boolean, critical: Boolean) {
        if (ok) { btn.setBackgroundColor(Color.parseColor("#4CAF50")); btn.setTextColor(Color.WHITE) }
        else { btn.setBackgroundColor(if (critical) Color.parseColor("#F44336") else Color.parseColor("#FFC107")); btn.setTextColor(if (critical) Color.WHITE else Color.BLACK) }
    }

    private fun runSecurityTests() {
        val s = DnsStats(this); val before = s.get("test_seen"); toast("Running diagnostics...")
        kotlin.concurrent.thread {
            var connectivityOk = false; var cloudOk = false
            try { InetAddress.getAllByName("diag.dnsrouter.check") } catch (_: Exception) {}
            Thread.sleep(1000); val captured = s.get("test_seen") > before
            try { val conn = URL("https://test.nextdns.io").openConnection() as HttpURLConnection; conn.connectTimeout = 5000; conn.readTimeout = 5000; connectivityOk = conn.responseCode == 200 } catch (_: Exception) {}
            if (p.apiKey.isNotBlank() && p.profile.isNotBlank()) { try { cloudOk = !fetchNextDnsStats(p.profile, p.apiKey).contains("Error") } catch (_: Exception) {} }
            runOnUiThread {
                p.lastSuccessfulTest = System.currentTimeMillis(); updateCircleColor(dnsCheckCircle, if (p.enabled) "#4CAF50" else "#F44336")
                if (!p.enabled) updateCircleColor(browserWarningCircle, "#9E9E9E") else if (!captured) { updateCircleColor(browserWarningCircle, "#F44336"); toast("ALERT: DNS Bypass detected!") } else updateCircleColor(browserWarningCircle, "#FFC107")
                updateCircleColor(connectivityCircle, if (connectivityOk) "#4CAF50" else "#F44336"); updateCircleColor(cloudCircle, if (cloudOk) "#4CAF50" else if (p.apiKey.isEmpty()) "#9E9E9E" else "#F44336"); updateUi(); toast("Tests complete")
            }
        }
    }

    private fun showSupportOptions() { AlertDialog.Builder(this).setTitle("Support Options").setItems(arrayOf("General Support Request", "Request App Exclusion")) { _, which -> if (which == 0) contactAdmin("General Support Request") else requestAppExclusion() }.setNegativeButton("Cancel", null).show() }
    private fun manageAppExclusions() {
        val items = p.excludedApps.sorted()
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(pos: Int, v: View?, parent: ViewGroup): View { val tv = super.getView(pos, v, parent) as TextView; val pkg = getItem(pos)!!; try { val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)); tv.text = "$label ($pkg)" } catch (_: Exception) {}; return tv }
        }
        val list = ListView(this).apply { setAdapter(adapter) }
        val dialog = AlertDialog.Builder(this).setTitle("Manage App Exclusions").setView(list).setPositiveButton("Add App") { _, _ -> showAppPicker { pkg -> p.excludedApps += pkg; updateUi(); manageAppExclusions() } }.setNegativeButton("Close", null).create()
        list.setOnItemLongClickListener { _, _, pos, _ -> val pkg = items[pos]; AlertDialog.Builder(this).setMessage("Remove $pkg from exclusions?").setPositiveButton("Remove") { _, _ -> p.excludedApps -= pkg; updateUi(); dialog.dismiss(); manageAppExclusions() }.setNegativeButton("Cancel", null).show(); true }
        dialog.show()
    }
    private fun showAppPicker(onSelected: (String) -> Unit) {
        val apps = packageManager.getInstalledApplications(0).asSequence().filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.sortedBy { packageManager.getApplicationLabel(it).toString() }.toList()
        val labels = apps.map { "${packageManager.getApplicationLabel(it)} (${it.packageName})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select App").setItems(labels) { _, which -> onSelected(apps[which].packageName) }.show()
    }
    private fun requestAppExclusion() {
        showAppPicker { pkg ->
            val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }; val appVer = try { packageManager.getPackageInfo(pkg, 0).versionName } catch (_: Exception) { "unknown" }
            val reasonEt = EditText(this).apply { hint = "Reason (e.g. payment failure)" }
            AlertDialog.Builder(this).setTitle("Request Exclusion: $appName").setView(reasonEt).setPositiveButton("Send Request") { _, _ ->
                    val reason = reasonEt.text.toString().trim().ifBlank { "None provided" }; val context = "App Exclusion Request: $appName"; val ssid = currentSsid()
                    val details = buildString { append("--- App Request ---\nApplication Name: $appName\nPackage: $pkg\nApp Version: $appVer\nReason: $reason\nRequested Action: exclude this application from DNS Router's VPN routing.\n\n")
                        append("--- System Status ---\nDevice: ${p.getEffectiveDeviceName()}\nNetwork: ${ssid ?: "Mobile data / Wi-Fi name unavailable"}\nNextDNS Profile: ${p.profile}\nDiagnostics Passed: ${p.lastSuccessfulTest > 0}\n") }
                    contactAdmin(context, details)
                }.setNegativeButton("Cancel", null).show()
        }
    }
    private fun contactAdmin(context: String, customBody: String? = null) {
        val email = adminEmailEt.text.toString().trim().ifBlank { p.adminEmail }; val phone = adminPhoneEt.text.toString().trim().ifBlank { p.adminPhone }
        if (email.isBlank() && phone.isBlank()) { toast("Admin contact info not set"); return }
        val subject = "DNS Router Support: $context"; val report = customBody ?: buildString { append("Device: ${p.getEffectiveDeviceName()}\nApp Version: ${versionName()}\nAndroid API: ${Build.VERSION.SDK_INT}\nProfile ID: ${p.profile}\nProtection Enabled: ${p.enabled}\n")
            val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()); append("Last Test: ${if (p.lastSuccessfulTest > 0) df.format(Date(p.lastSuccessfulTest)) else "Never"}\n\nProblem details:\n") }
        val emailIntent = Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(email)}?subject=${Uri.encode(subject)}&body=${Uri.encode(report)}".toUri())
        val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()).apply { putExtra("sms_body", report) }
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, "$subject\n\n$report") }
        val chooser = Intent.createChooser(fallbackIntent, "Request Assistance via:"); val initials = mutableListOf<Intent>(); if (email.isNotBlank()) initials.add(emailIntent); if (phone.isNotBlank()) initials.add(smsIntent)
        if (initials.isNotEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, initials.toTypedArray())
        try { startActivity(chooser) } catch (_: Exception) { toast("No contact app found") }
    }

    private fun versionName() = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "unknown" }
    private fun isAlwaysOnVpnEnabled(): Boolean = Settings.Secure.getString(contentResolver, "always_on_vpn_app") == packageName
    private fun showCompatibilityReport(isAuto: Boolean) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager; val isBatteryExempt = pm.isIgnoringBatteryOptimizations(packageName)
        val report = buildString { append("CORE SERVICES (Required)\n• DNS-only VPN: Core Support\n• NextDNS DoH: Core Support\n• Encrypted Storage: ${if (p.encryptionWorks) "Required (Active)" else "FAILED"}\n\n")
            append("RECOMMENDED (For Reliability)\n• Always-On VPN: ${if (isAlwaysOnVpnEnabled()) "Active" else "Highly Recommended"}\n• Battery Exemption: ${if (isBatteryExempt) "Active" else "Recommended"}\n• Boot Auto-Start: ${if (p.autoStart) "Active" else "Use"}\n• Foreground Service: ${if (p.foregroundService) "Active" else "Use"}\n\n")
            append("OPTIONAL (Features)\n• NextDNS API: ${if (p.apiKey.isNotEmpty()) "Configured" else "Optional"}\n• Wi-Fi Exclusions: ${if (p.excluded.isNotEmpty()) "In Use (${p.excluded.size})" else "Optional"}\n• PIN Protection: ${if (p.pinHash != null) "Active" else "Optional"}") }
        AlertDialog.Builder(this).setTitle(if (isAuto) "Version Update: Support Report" else "Device Support Report").setMessage(report).setPositiveButton("OK", null).show() }
    private fun checkBatteryOptimizationOnStartup() { val pm = getSystemService(POWER_SERVICE) as PowerManager; if (!pm.isIgnoringBatteryOptimizations(packageName)) { AlertDialog.Builder(this).setTitle("Performance Warning").setMessage("Android may stop DNS Routing in the background if battery optimization is enabled. For reliable automatic DNS Protection, allow DNS Router to run without battery optimization.").setPositiveButton("Disable") { _, _ -> showBatteryDialog() }.setNegativeButton("Later", null).show() } }
    private fun showBatteryDialog() { AlertDialog.Builder(this).setTitle("Background Reliability").setMessage("To prevent the system from killing the VPN service, you must disable battery optimization for DNS Router. On the next screen, find this app and select 'Don't optimize' or 'Unrestricted'.").setPositiveButton("Proceed") { _, _ -> openBatterySettings() }.setNegativeButton("Cancel", null).show() }
    private fun managePin() { if (p.pinHash == null) showSetPinDialog() else auth { AlertDialog.Builder(this).setTitle("Manage PIN").setItems(arrayOf("Change PIN", "Remove PIN")) { _, which -> if (which == 0) showSetPinDialog() else { p.clearPin(); updateUi(); toast("PIN removed") } }.setNegativeButton("Cancel", null).show() } }
    private fun showSetPinDialog(onSaved: (() -> Unit)? = null) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 16) }
        val pin1 = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "New PIN (4–12 digits)" }
        val pin2 = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "Verify New PIN" }
        container.addView(pin1); container.addView(pin2)
        AlertDialog.Builder(this).setTitle(if (p.pinHash == null) "Set App PIN" else "Change App PIN").setView(container).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create().apply {
            setOnShowListener { getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val s1 = pin1.text.toString(); val s2 = pin2.text.toString(); if (s1 != s2) toast("PINs do not match") else if (s1.length !in 4..12) toast("PIN must be 4–12 digits") else { p.setPin(s1); toast("PIN saved"); updateUi(); onSaved?.invoke(); dismiss() } } }
        }.show()
    }
    private fun auth(onSuccess: () -> Unit) {
        if (p.pinHash == null) { onSuccess(); return }
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "Current PIN" }
        val d = AlertDialog.Builder(this).setTitle("Enter PIN").setView(input).setNegativeButton("Cancel", null).setPositiveButton("OK", null).setNeutralButton("Forgot PIN") { _, _ -> forgotPin() }.create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (p.checkPin(input.text.toString())) { d.dismiss(); onSuccess() } else toast("Incorrect PIN") } }
        d.show()
    }
    private fun forgotPin() {
        if (isAdminMode) { AlertDialog.Builder(this).setTitle("Managed App").setMessage("This app is managed by an administrator. Contact your administrator to reset the PIN.").setPositiveButton("Contact Admin") { _, _ -> contactAdmin("Forgot PIN Request") }.setNegativeButton("Cancel", null).show() }
        else { AlertDialog.Builder(this).setTitle("Reset DNS Router?").setMessage("Forgot your PIN? To regain access, you must reset the app. This will clear ALL NextDNS settings and local statistics. This cannot be undone.").setPositiveButton("Reset Everything") { _, _ -> stopVpn(); p.resetAll(); DnsStats(this).clear(); toast("App reset successful"); val intent = packageManager.getLaunchIntentForPackage(packageName); intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(intent); finish() }.setNegativeButton("Cancel", null).show() }
    }
    private fun isAppManaged(): Boolean {
        val rm = getSystemService(RESTRICTIONS_SERVICE) as RestrictionsManager; val restrictions = rm.applicationRestrictions
        return (restrictions != null && restrictions.size() > 0) || try { val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager; dpm.isDeviceOwnerApp(packageName) || dpm.isProfileOwnerApp(packageName) } catch (_: Exception) { false }
    }
    private fun requestVpn() { val i = VpnService.prepare(this); if (i != null) vpnResult.launch(i) else startVpn() }
    private fun startVpn() { ContextCompat.startForegroundService(this, Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START)) }
    private fun stopVpn() { startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP)) }
    private fun editExclusions() {
        val ssid = currentSsid(); val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 16) }
        container.addView(TextView(this).apply { text = "Excluded networks: ${p.excluded.size}"; setPadding(0, 0, 0, 8); textSize = 14f })
        val e = EditText(this).apply { hint = "One Wi-Fi SSID per line"; setText(p.excluded.sorted().joinToString("\n")); minLines = 5; gravity = Gravity.TOP }
        container.addView(e)
        container.addView(Button(this).apply { text = if (ssid != null) "Add Current: $ssid" else "Current Wi-Fi: Wi-Fi name unavailable"; isEnabled = ssid != null
            setOnClickListener { val current = e.text.toString().lines().toMutableList(); if (ssid != null && !current.contains(ssid)) { e.append(if (e.text.isEmpty()) ssid else "\n$ssid") } }
        })
        container.addView(TextView(this).apply { text = "Note: SSIDs must match exactly. This app stays running but bypasses routing on these networks."; textSize = 12f; setPadding(0, 8, 0, 0) })
        AlertDialog.Builder(this).setTitle("Wi-Fi Exclusions").setView(container).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> p.excluded = e.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(); toast("Saved"); updateUi() }.show()
    }
    private fun showStats() {
        val s = DnsStats(this); val localText = "LOCAL COUNTERS:\nQueries captured: ${s.get("queries")}\nResponses sent: ${s.get("responses")}\nErrors: ${s.get("errors")}\nNXDOMAIN: ${s.get("nxdomain")}\nSERVFAIL: ${s.get("servfail")}"
        val d = AlertDialog.Builder(this).setTitle("DNS Activity").setMessage("$localText\n\nFetching NextDNS cloud analytics...").setPositiveButton("OK", null).setNeutralButton("Clear Local") { _, _ -> s.clear(); toast("Local statistics cleared") }.create(); d.show()
        val currentKey = p.apiKey; val currentProfile = p.profile
        if (currentKey.isNotBlank() && currentProfile.isNotBlank()) { kotlin.concurrent.thread { try { val cloudData = fetchNextDnsStats(currentProfile, currentKey); runOnUiThread { if (d.isShowing) d.setMessage("$localText\n\nNEXTDNS CLOUD (Profile: $currentProfile):\n$cloudData") } } catch (_: Exception) {} } } else { d.setMessage("$localText\n\n(API Key and Profile ID required for cloud analytics)") }
    }
    private fun fetchNextDnsStats(profile: String, key: String): String { return try { val url = URL("https://api.nextdns.io/profiles/$profile/analytics/status"); val conn = url.openConnection() as HttpURLConnection; conn.setRequestProperty("X-Api-Key", key); conn.connectTimeout = 10000; conn.readTimeout = 10000; if (conn.responseCode == 200) parseAnalyticsStatus(conn.inputStream.bufferedReader().readText()) else "Error ${conn.responseCode}: ${conn.responseMessage}" } catch (e: Exception) { "Error: ${e.message}" } }
    private fun parseAnalyticsStatus(json: String): String { val total = Regex("\"totalQueries\":\\s*(\\d+)").find(json)?.groupValues?.get(1) ?: "0"; val blocked = Regex("\"blockedQueries\":\\s*(\\d+)").find(json)?.groupValues?.get(1) ?: "0"; return "Total Queries: $total\nBlocked: $blocked" }
    private fun showSetup() { AlertDialog.Builder(this).setTitle("Always-On VPN Protection").setMessage("Highly Recommended: Enabling 'Always-on VPN' in Android settings ensures DNS Router is automatically managed by the system.\n\nRisk: If NOT enabled, DNS queries may bypass NextDNS during network transitions or if the app is stopped.\n\nOptional: 'Block connections without VPN' provides strict protection by disabling internet if the VPN is not connected.").setPositiveButton("Open Settings") { _, _ -> try { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } catch (_: Exception) {} }.setNegativeButton("Close", null).show() }
    private fun openBatterySettings() { try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData("package:$packageName".toUri())) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    private fun requestWifiPermission() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) } else { checkBatteryOptimizationOnStartup() } }
    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = try { val wm = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager; wm.connectionInfo.ssid?.trim('\"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" } } catch (_: Exception) { null }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
