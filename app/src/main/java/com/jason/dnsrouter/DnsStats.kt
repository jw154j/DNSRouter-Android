package com.jason.dnsrouter

import android.content.Context

class DnsStats(ctx: Context) {
    private val p = ctx.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun inc(key: String) = synchronized(this) {
        val current = p.getLong(key, 0)
        val next = current + 1
        p.edit().putLong(key, next).apply()
        android.util.Log.v("DnsStats", "Incremented $key to $next")
    }

    fun get(key: String) = p.getLong(key, 0)
    fun clear() = p.edit().clear().apply()
}
