package com.jason.dnsrouter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val p = Prefs(context)
            if (p.autoStart && p.enabled) {
                ContextCompat.startForegroundService(context, Intent(context, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START))
            }
        }
    }
}
