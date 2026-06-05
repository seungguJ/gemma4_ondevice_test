package com.example.gemma4ondevicetest.schedule

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private val ZONE = ZoneId.of("Asia/Seoul")
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

object CalendarReader {

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Gmail 계정만 필터링한 일정 — Gemma 프롬프트용 */
    fun getUpcomingWeekEvents(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now(ZONE)
    ): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) return emptyList()
        val ids = getCalendarIds(context)
        if (ids.isEmpty()) return emptyList()
        return queryInstancesById(context, now, ids)
    }

    /** 모든 캘린더의 일정 — UI 전체 일정 표시용 */
    fun getAllUpcomingWeekEvents(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now(ZONE)
    ): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) return emptyList()
        return queryInstancesById(context, now, calendarIds = null)
    }

    private fun queryInstancesById(
        context: Context,
        now: ZonedDateTime,
        calendarIds: Set<Long>?   // null = 필터 없이 전체
    ): List<CalendarEvent> {
        val end = now.plusDays(7)
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now.toInstant().toEpochMilli())
        ContentUris.appendId(builder, end.toInstant().toEpochMilli())

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val selection = calendarIds?.let {
            "${CalendarContract.Instances.CALENDAR_ID} IN (${it.joinToString(",")})"
        }

        // 제목+날짜 기준 중복 제거 (BEGIN ASC 정렬 유지)
        val seen = LinkedHashMap<String, CalendarEvent>()
        context.contentResolver.query(
            builder.build(), projection, selection, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val idIdx     = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIdx  = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx  = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx    = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val locIdx    = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                val title   = cursor.getString(titleIdx).orEmpty().ifBlank { "(제목 없음)" }
                val beginMs = cursor.getLong(beginIdx)
                val dedupeKey = "$title|${formatDateLabel(beginMs)}"
                if (!seen.containsKey(dedupeKey)) {
                    seen[dedupeKey] = CalendarEvent(
                        id          = cursor.getLong(idIdx),
                        title       = title,
                        startMillis = beginMs,
                        endMillis   = cursor.getLong(endIdx),
                        isAllDay    = cursor.getInt(allDayIdx) == 1,
                        location    = cursor.getString(locIdx)
                    )
                }
            }
        }
        return seen.values.toList()
    }

    fun formatEventTime(event: CalendarEvent): String {
        val start = Instant.ofEpochMilli(event.startMillis).atZone(ZONE)
        return if (event.isAllDay) "종일"
        else "%02d:%02d".format(start.hour, start.minute)
    }

    fun formatDateLabel(millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(ZONE)
        val dayNames = arrayOf("일", "월", "화", "수", "목", "금", "토")
        val dow = dayNames[dt.dayOfWeek.value % 7]
        return "%02d/%02d (%s)".format(dt.monthValue, dt.dayOfMonth, dow)
    }

    /**
     * Returns calendar IDs to query.
     * Uses all Google calendar accounts visible to the app.
     */
    private fun getCalendarIds(context: Context): Set<Long> {
        data class CalRow(val id: Long, val account: String, val type: String)

        val rows = mutableListOf<CalRow>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                rows += CalRow(
                    id      = cursor.getLong(idIdx),
                    account = cursor.getString(nameIdx).orEmpty(),
                    type    = cursor.getString(typeIdx).orEmpty()
                )
            }
        }

        // Google 계정 캘린더만 대상으로 제한한다.
        return rows
            .filter { it.type == GOOGLE_ACCOUNT_TYPE }
            .map { it.id }
            .toSet()
    }
}
