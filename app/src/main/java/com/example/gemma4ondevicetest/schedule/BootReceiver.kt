package com.example.gemma4ondevicetest.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            .getBoolean("notif_enabled", false)
        if (enabled) ScheduleWorkScheduler.schedule(context)
    }
}
