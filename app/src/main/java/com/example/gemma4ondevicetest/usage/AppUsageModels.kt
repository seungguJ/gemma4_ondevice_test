package com.example.gemma4ondevicetest.usage

data class AppUsageSessionRecord(
    val id: Long = 0L,
    val packageName: String,
    val appCategory: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val weekday: Int,
    val hhmm: Int
)

data class AppUsageStatsSummary(
    val totalSessions: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val distinctPackageCount: Int = 0,
    val lastSyncedAtMillis: Long = 0L,
    val lastProcessedEventAtMillis: Long = 0L
)

data class AppUsageTopApp(
    val packageName: String,
    val sessionCount: Int,
    val totalDurationSeconds: Long
)
