package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SubscriptionAnalysisScheduler {

    private const val UNIQUE_WORK_NAME = "subscription-analysis"
    const val COOLDOWN_MILLIS = 24 * 60 * 60 * 1000L

    fun enqueue(context: Context, initialDelayMillis: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<SubscriptionAnalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .apply {
                if (initialDelayMillis > 0L) {
                    setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                }
            }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueAutomatic(context: Context) {
        val lastCompletedAt = SubscriptionInsightStore(context).loadReport().lastCompletedAt
        val delayMillis = if (lastCompletedAt > 0L) {
            remainingCooldownMillis(lastCompletedAt)
        } else {
            COOLDOWN_MILLIS
        }
        enqueue(context, initialDelayMillis = delayMillis)
    }

    fun currentBatteryGateStatus(context: Context): BatteryGateStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        return BatteryGateStatus(
            isCharging = charging,
            levelPercent = percent.coerceIn(0, 100)
        )
    }

    fun remainingCooldownMillis(lastCompletedAt: Long, now: Long = System.currentTimeMillis()): Long {
        if (lastCompletedAt <= 0L) return 0L
        return (lastCompletedAt + COOLDOWN_MILLIS - now).coerceAtLeast(0L)
    }

    fun isCooldownActive(lastCompletedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        remainingCooldownMillis(lastCompletedAt, now) > 0L

    fun formatRemainingCooldown(remainingMillis: Long): String {
        val totalMinutes = (remainingMillis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    }
}
