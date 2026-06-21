package com.example.gemma4ondevicetest.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.ZoneId

object AppUsageCollector {
    private const val INITIAL_LOOKBACK_MILLIS = AppUsageLogStore.RETENTION_MILLIS
    private const val OVERLAP_MILLIS = 1000L * 60L * 5L
    private const val MIN_SESSION_DURATION_SECONDS = 5L
    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    data class SyncResult(
        val insertedCount: Int,
        val scannedEventCount: Int,
        val lastEventAtMillis: Long
    )

    fun collect(context: Context, nowMillis: Long = System.currentTimeMillis()): SyncResult {
        if (!AppUsagePermissionManager.isGranted(context)) {
            return SyncResult(0, 0, 0L)
        }

        val store = AppUsageLogStore.getInstance(context)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val lastProcessed = store.getLastProcessedEventAtMillis()
        val beginMillis = when {
            lastProcessed > 0L -> (lastProcessed - OVERLAP_MILLIS).coerceAtLeast(0L)
            else -> (nowMillis - INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0L)
        }
        val events = usageStatsManager.queryEvents(beginMillis, nowMillis)
        val categoryCache = mutableMapOf<String, Int>()
        val pendingStarts = store.loadPendingForegroundSessions()
            .filterKeys { packageName -> AppUsageAllowlistPolicy.shouldCollect(context, packageName) }
            .toMutableMap()
        val sessions = mutableListOf<AppUsageSessionRecord>()
        var scanned = 0
        var lastSeenTimestamp = lastProcessed

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            scanned++
            if (event.timeStamp > lastSeenTimestamp) {
                lastSeenTimestamp = event.timeStamp
            }
            val packageName = event.packageName ?: continue
            if (!AppUsageAllowlistPolicy.shouldCollect(context, packageName)) continue
            val category = resolveCategoryCached(context, packageName, categoryCache)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    pendingStarts[packageName] = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startedAt = pendingStarts.remove(packageName) ?: continue
                    if (event.timeStamp <= startedAt) continue
                    val durationSeconds = ((event.timeStamp - startedAt) / 1000L).coerceAtLeast(1L)
                    if (durationSeconds < MIN_SESSION_DURATION_SECONDS) continue
                    val startedAtZoned = Instant.ofEpochMilli(startedAt).atZone(seoulZone)
                    sessions += AppUsageSessionRecord(
                        packageName = packageName,
                        appCategory = category,
                        startedAtMillis = startedAt,
                        endedAtMillis = event.timeStamp,
                        durationSeconds = durationSeconds,
                        weekday = startedAtZoned.dayOfWeek.value,
                        hhmm = startedAtZoned.hour * 100 + startedAtZoned.minute
                    )
                }
            }
        }

        val inserted = store.insertSessions(sessions)
        store.enforceRetention(nowMillis)
        store.savePendingForegroundSessions(pendingStarts)
        store.saveLastSyncState(nowMillis, lastSeenTimestamp)
        return SyncResult(inserted, scanned, lastSeenTimestamp)
    }

    private fun resolveCategoryCached(context: Context, packageName: String, cache: MutableMap<String, Int>): Int =
        cache.getOrPut(packageName) {
            AppUsageAllowlistPolicy.resolveCategory(context, packageName)
        }
}
