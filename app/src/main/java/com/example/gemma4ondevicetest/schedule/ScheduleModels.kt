package com.example.gemma4ondevicetest.schedule

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String?
)

data class ScheduleEntry(
    val dateLabel: String,   // e.g. "05/30 (금)"
    val timeLabel: String,   // e.g. "14:00" or "종일"
    val title: String,
    val sortMillis: Long = 0L
)

data class ScheduleSummaryResult(
    val prompt: String,
    val entries: List<ScheduleEntry>
)
