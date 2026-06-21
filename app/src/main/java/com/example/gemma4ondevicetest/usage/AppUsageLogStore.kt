package com.example.gemma4ondevicetest.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class AppUsageLogStore private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createSessionsTable(db)
        db.execSQL(
            """
            CREATE TABLE $TABLE_META (
                meta_key TEXT PRIMARY KEY,
                meta_value TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_app_usage_started_at ON $TABLE_SESSIONS(started_at_millis DESC)"
        )
        db.execSQL(
            "CREATE INDEX idx_app_usage_package_name ON $TABLE_SESSIONS(package_name)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
            createSessionsTable(db)
            // 기존 세션을 지웠으므로 증분 동기화 기준점도 같이 초기화해야
            // 다음 collect()가 24시간 lookback으로 다시 백필한다.
            db.delete(
                TABLE_META,
                "meta_key IN (?, ?)",
                arrayOf(KEY_LAST_PROCESSED_EVENT_AT, KEY_PENDING_FOREGROUND_SESSIONS)
            )
        }
        if (oldVersion < 3) {
            db.delete(TABLE_SESSIONS, null, null)
            db.delete(
                TABLE_META,
                "meta_key IN (?, ?)",
                arrayOf(KEY_LAST_PROCESSED_EVENT_AT, KEY_PENDING_FOREGROUND_SESSIONS)
            )
        }
        if (oldVersion < 4) {
            db.delete(TABLE_SESSIONS, null, null)
            db.delete(
                TABLE_META,
                "meta_key IN (?, ?)",
                arrayOf(KEY_LAST_PROCESSED_EVENT_AT, KEY_PENDING_FOREGROUND_SESSIONS)
            )
        }
        if (oldVersion < 5) {
            enforceRetention(db, System.currentTimeMillis())
        }
    }

    fun insertSessions(records: List<AppUsageSessionRecord>): Int {
        if (records.isEmpty()) return 0
        var inserted = 0
        writableDatabase.beginTransaction()
        try {
            records.forEach { record ->
                val values = ContentValues().apply {
                    put("package_name", record.packageName)
                    put("app_category", record.appCategory)
                    put("started_at_millis", record.startedAtMillis)
                    put("ended_at_millis", record.endedAtMillis)
                    put("duration_seconds", record.durationSeconds)
                    put("weekday", record.weekday)
                    put("hhmm", record.hhmm)
                }
                val rowId = writableDatabase.insertWithOnConflict(
                    TABLE_SESSIONS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId != -1L) inserted++
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return inserted
    }

    fun enforceRetention(nowMillis: Long = System.currentTimeMillis()) {
        enforceRetention(writableDatabase, nowMillis)
        val cutoffMillis = (nowMillis - RETENTION_MILLIS).coerceAtLeast(0L)
        val pending = loadPendingForegroundSessions()
            .filterValues { startedAtMillis -> startedAtMillis >= cutoffMillis }
        savePendingForegroundSessions(pending)
    }

    private fun enforceRetention(db: SQLiteDatabase, nowMillis: Long) {
        val cutoffMillis = (nowMillis - RETENTION_MILLIS).coerceAtLeast(0L)
        db.delete(TABLE_SESSIONS, "started_at_millis < ?", arrayOf(cutoffMillis.toString()))
    }

    fun loadRecentSessions(limit: Int = 200): List<AppUsageSessionRecord> {
        val rows = mutableListOf<AppUsageSessionRecord>()
        readableDatabase.query(
            TABLE_SESSIONS,
            arrayOf("id", "package_name", "app_category", "started_at_millis", "ended_at_millis", "duration_seconds", "weekday", "hhmm"),
            null,
            null,
            null,
            null,
            "started_at_millis DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += AppUsageSessionRecord(
                    id = cursor.getLong(0),
                    packageName = cursor.getString(1),
                    appCategory = cursor.getInt(2),
                    startedAtMillis = cursor.getLong(3),
                    endedAtMillis = cursor.getLong(4),
                    durationSeconds = cursor.getLong(5),
                    weekday = cursor.getInt(6),
                    hhmm = cursor.getInt(7)
                )
            }
        }
        return rows
    }

    fun loadAllSessionsForExport(): List<AppUsageSessionRecord> {
        val rows = mutableListOf<AppUsageSessionRecord>()
        readableDatabase.query(
            TABLE_SESSIONS,
            arrayOf("id", "package_name", "app_category", "started_at_millis", "ended_at_millis", "duration_seconds", "weekday", "hhmm"),
            null,
            null,
            null,
            null,
            "started_at_millis ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += AppUsageSessionRecord(
                    id = cursor.getLong(0),
                    packageName = cursor.getString(1),
                    appCategory = cursor.getInt(2),
                    startedAtMillis = cursor.getLong(3),
                    endedAtMillis = cursor.getLong(4),
                    durationSeconds = cursor.getLong(5),
                    weekday = cursor.getInt(6),
                    hhmm = cursor.getInt(7)
                )
            }
        }
        return rows
    }

    fun loadTopApps(limit: Int = 5): List<AppUsageTopApp> {
        val rows = mutableListOf<AppUsageTopApp>()
        readableDatabase.rawQuery(
            """
            SELECT package_name, COUNT(*) AS session_count, COALESCE(SUM(duration_seconds), 0) AS total_duration
            FROM $TABLE_SESSIONS
            GROUP BY package_name
            ORDER BY total_duration DESC, session_count DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += AppUsageTopApp(
                    packageName = cursor.getString(0),
                    sessionCount = cursor.getInt(1),
                    totalDurationSeconds = cursor.getLong(2)
                )
            }
        }
        return rows
    }

    fun loadSummary(): AppUsageStatsSummary {
        var totalSessions = 0
        var totalDurationSeconds = 0L
        var distinctPackageCount = 0
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*), COALESCE(SUM(duration_seconds), 0), COUNT(DISTINCT package_name)
            FROM $TABLE_SESSIONS
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                totalSessions = cursor.getInt(0)
                totalDurationSeconds = cursor.getLong(1)
                distinctPackageCount = cursor.getInt(2)
            }
        }
        return AppUsageStatsSummary(
            totalSessions = totalSessions,
            totalDurationSeconds = totalDurationSeconds,
            distinctPackageCount = distinctPackageCount,
            lastSyncedAtMillis = getMetaLong(KEY_LAST_SYNCED_AT),
            lastProcessedEventAtMillis = getMetaLong(KEY_LAST_PROCESSED_EVENT_AT)
        )
    }

    fun saveLastSyncState(lastSyncedAtMillis: Long, lastProcessedEventAtMillis: Long) {
        putMetaLong(KEY_LAST_SYNCED_AT, lastSyncedAtMillis)
        putMetaLong(KEY_LAST_PROCESSED_EVENT_AT, lastProcessedEventAtMillis)
    }

    fun getLastProcessedEventAtMillis(): Long = getMetaLong(KEY_LAST_PROCESSED_EVENT_AT)

    fun loadPendingForegroundSessions(): Map<String, Long> {
        val raw = getMeta(KEY_PENDING_FOREGROUND_SESSIONS)
        if (raw.isBlank()) return emptyMap()
        val json = JSONObject(raw)
        return buildMap {
            json.keys().forEach { key -> put(key, json.optLong(key)) }
        }
    }

    fun savePendingForegroundSessions(pending: Map<String, Long>) {
        val json = JSONObject()
        pending.forEach { (key, value) -> json.put(key, value) }
        putMeta(KEY_PENDING_FOREGROUND_SESSIONS, json.toString())
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_SESSIONS, null, null)
        writableDatabase.delete(TABLE_META, null, null)
    }

    private fun putMetaLong(key: String, value: Long) = putMeta(key, value.toString())

    private fun getMetaLong(key: String): Long = getMeta(key).toLongOrNull() ?: 0L

    private fun getMeta(key: String): String {
        readableDatabase.query(
            TABLE_META,
            arrayOf("meta_value"),
            "meta_key = ?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return ""
    }

    private fun putMeta(key: String, value: String) {
        val values = ContentValues().apply {
            put("meta_key", key)
            put("meta_value", value)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_META,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    companion object {
        const val RETENTION_DAYS: Long = 7L
        const val RETENTION_MILLIS: Long = RETENTION_DAYS * 24L * 60L * 60L * 1000L

        private const val DB_NAME = "app_usage_logs.db"
        private const val DB_VERSION = 5
        private const val TABLE_SESSIONS = "app_usage_sessions"
        private const val TABLE_META = "app_usage_meta"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_LAST_PROCESSED_EVENT_AT = "last_processed_event_at"
        private const val KEY_PENDING_FOREGROUND_SESSIONS = "pending_foreground_sessions"

        @Volatile
        private var instance: AppUsageLogStore? = null

        fun getInstance(context: Context): AppUsageLogStore =
            instance ?: synchronized(this) {
                instance ?: AppUsageLogStore(context.applicationContext).also { instance = it }
            }
    }

    private fun createSessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                app_category INTEGER NOT NULL,
                started_at_millis INTEGER NOT NULL,
                ended_at_millis INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                weekday INTEGER NOT NULL,
                hhmm INTEGER NOT NULL,
                UNIQUE(package_name, started_at_millis, ended_at_millis)
            )
            """.trimIndent()
        )
    }
}
