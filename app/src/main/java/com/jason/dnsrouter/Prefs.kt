package com.jason.dnsrouter

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    var profile: String get() = p.getString("profile", "da8163") ?: "da8163"; set(v) = p.edit().putString("profile", v).apply()
    var apiKey: String get() = p.getString("api_key", "d1381a6f7375eb904b22cd8cbb86ee711626cb61") ?: "d1381a6f7375eb904b22cd8cbb86ee711626cb61"; set(v) = p.edit().putString("api_key", v).apply()
    var deviceName: String get() = p.getString("device", "Jesse's A16 5G") ?: "Jesse's A16 5G"; set(v) = p.edit().putString("device", v).apply()
    var enabled: Boolean get() = p.getBoolean("enabled", true); set(v) = p.edit().putBoolean("enabled", v).apply()
    var autoStart: Boolean get() = p.getBoolean("autostart", true); set(v) = p.edit().putBoolean("autostart", v).apply()
    var pinHash: String? get() = p.getString("pin_hash", null); private set(v) = p.edit().putString("pin_hash", v).apply()
    var pinSalt: String? get() = p.getString("pin_salt", null); private set(v) = p.edit().putString("pin_salt", v).apply()
    var excluded: Set<String> get() = p.getStringSet("excluded", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("excluded", v).apply()

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
