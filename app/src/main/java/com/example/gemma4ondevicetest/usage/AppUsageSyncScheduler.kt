package com.example.gemma4ondevicetest.usage

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppUsageSyncScheduler {
    private const val PERIODIC_WORK_NAME = "app-usage-sync-periodic"
    private const val MANUAL_WORK_NAME = "app-usage-sync-manual"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUsageSyncWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AppUsageSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
