package com.jason.dnsrouter

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

class Prefs(private val ctx: Context) {
    val encryptionWorks: Boolean
    private val p: SharedPreferences
    private val f: SharedPreferences = ctx.getSharedPreferences("config_fallback", Context.MODE_PRIVATE)

    init {
        var success = false
        p = try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encrypted = EncryptedSharedPreferences.create(
                ctx,
                "config_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            success = true
            encrypted
        } catch (e: Exception) {
            f
        }
        encryptionWorks = success
    }

    var profile: String get() = f.getString("profile", "") ?: ""; set(v) = f.edit().putString("profile", v).apply()
    
    var apiKey: String 
        get() = if (encryptionWorks) p.getString("api_key", "") ?: "" else ""
        set(v) { if (encryptionWorks) p.edit().putString("api_key", v).apply() }

    var useDeviceName: Boolean get() = f.getBoolean("use_hw_name", false); set(v) = f.edit().putBoolean("use_hw_name", v).apply()
    var manualDeviceName: String get() = f.getString("device", "") ?: ""; set(v) = f.edit().putString("device", v).apply()

    /** Returns the device name to be used for DNS logs. */
    fun getEffectiveDeviceName(): String {
        if (useDeviceName) {
            val name = if (Build.VERSION.SDK_INT >= 25) {
                Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME)
                    ?: Settings.Secure.getString(ctx.contentResolver, "bluetooth_name")
            } else {
                Settings.Secure.getString(ctx.contentResolver, "bluetooth_name")
            }
            return name?.takeUnless { it.isBlank() } ?: Build.MODEL
        }
        return manualDeviceName.ifBlank { "Mobile Device" }
    }

    var enabled: Boolean get() = f.getBoolean("enabled", false); set(v) = f.edit().putBoolean("enabled", v).apply()
    var autoStart: Boolean get() = f.getBoolean("autostart", true); set(v) = f.edit().putBoolean("autostart", v).apply()
    
    var pinHash: String? get() = f.getString("pin_hash", null); private set(v) = f.edit().putString("pin_hash", v).apply()
    var pinSalt: String? get() = f.getString("pin_salt", null); private set(v) = f.edit().putString("pin_salt", v).apply()
    
    var excluded: Set<String> get() = f.getStringSet("excluded", emptySet()) ?: emptySet(); set(v) = f.edit().putStringSet("excluded", v).apply()

    fun checkPin(pin: String): Boolean {
        val salt = pinSalt ?: return false
        return pinHash == hash(pin, Base64.decode(salt, Base64.NO_WRAP))
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        pinSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        pinHash = hash(pin, salt)
    }

    private fun hash(s: String, salt: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256")
        d.update(salt)
        d.update(s.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(d.digest(), Base64.NO_WRAP)
    }
}
