package com.jason.dnsrouter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

class DnsStats(ctx: Context) {
    private val p = ctx.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private val logPref = ctx.getSharedPreferences("query_log", Context.MODE_PRIVATE)

    data class QueryRecord(val domain: String, val status: String, val timestamp: Long)

    fun inc(key: String) = synchronized(this) {
        val current = p.getLong(key, 0)
        val next = current + 1
        p.edit { putLong(key, next) }
    }

    fun get(key: String) = p.getLong(key, 0)

    fun logQuery(domain: String, status: String) = synchronized(this) {
        val log = getQueryLog().toMutableList()
        log.add(0, QueryRecord(domain, status, System.currentTimeMillis()))
        if (log.size > 100) log.removeAt(log.size - 1)
        
        val array = JSONArray()
        log.forEach { 
            val obj = JSONObject()
            obj.put("d", it.domain)
            obj.put("s", it.status)
            obj.put("t", it.timestamp)
            array.put(obj)
        }
        logPref.edit { putString("log", array.toString())}
    }

    fun getQueryLog(): List<QueryRecord> {
        val json = logPref.getString("log", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<QueryRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(QueryRecord(obj.getString("d"), obj.getString("s"), obj.getLong("t")))
        }
        return list
    }

    fun clear() {
        p.edit().clear().apply()
        logPref.edit {clear()}
    }
}
