package com.example.gemma4ondevicetest.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppUsageSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!AppUsagePermissionManager.isGranted(applicationContext)) {
            return Result.success()
        }
        return runCatching {
            AppUsageCollector.collect(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
