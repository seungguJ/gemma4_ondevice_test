package com.example.gemma4ondevicetest.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleWorkScheduler {

    private const val REQUEST_CODE      = 1001
    private const val REQUEST_CODE_SHOW = 1002
    private const val PREF_FILE         = "schedule_prefs"
    private const val KEY_HOUR          = "notif_hour"
    private const val KEY_MINUTE        = "notif_minute"

    fun saveTargetTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }

    fun readTargetTime(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        return p.getInt(KEY_HOUR, 8) to p.getInt(KEY_MINUTE, 0)
    }

    fun schedule(context: Context) {
        val (hour, minute) = readTargetTime(context)
        setAlarmClock(context, computeNextTriggerMillis(hour, minute))
    }

    fun scheduleNext(context: Context) = schedule(context)

    fun ensureScheduled(context: Context) = schedule(context)

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(buildPendingIntent(context))
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ScheduleWorker>().build()
        )
    }

    fun scheduleTestIn60Seconds(context: Context) {
        setAlarmClock(context, System.currentTimeMillis() + 60_000L)
    }

    private fun setAlarmClock(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, buildShowIntent(context))
        am.setAlarmClock(info, buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildShowIntent(context: Context): PendingIntent {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        return PendingIntent.getActivity(
            context, REQUEST_CODE_SHOW, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun computeNextTriggerMillis(hour: Int, minute: Int): Long {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(zone)
        var next = now.with(LocalTime.of(hour, minute, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }
}
