package com.example.gemma4ondevicetest.schedule

import java.time.ZoneId
import java.time.ZonedDateTime

object SchedulePromptBuilder {

    private val ZONE = ZoneId.of("Asia/Seoul")

    fun build(
        events: List<CalendarEvent>,
        now: ZonedDateTime = ZonedDateTime.now(ZONE)
    ): ScheduleSummaryResult {
        val entries = buildEntries(events)

        val dayNames = arrayOf("일", "월", "화", "수", "목", "금", "토")
        val todayDow = dayNames[now.dayOfWeek.value % 7]
        val today = "%02d/%02d (%s)".format(now.monthValue, now.dayOfMonth, todayDow)

        val scheduleText = if (entries.isEmpty()) {
            "일정 없음"
        } else {
            entries.joinToString("\n") { entry ->
                "${entry.dateLabel} : ${entry.title}"
            }
        }

        val prompt = buildString {
            appendLine("오늘: $today")
            appendLine()
            appendLine("아래 일정 목록을 각 항목 한 줄씩 출력하라.")
            appendLine("출력 형식: mm/dd (요일) : 항목명")
            appendLine("예) 05/30 (금) : 팀 회의")
            appendLine()
            appendLine("목록:")
            appendLine(scheduleText)
            appendLine()
            appendLine("항목명은 원문 그대로 출력한다.")
            appendLine("목록이 비어 있으면 '일정 없음'만 출력한다.")
        }

        return ScheduleSummaryResult(prompt = prompt, entries = entries)
    }

    fun buildEntries(events: List<CalendarEvent>): List<ScheduleEntry> {
        return events.map { ev ->
            ScheduleEntry(
                dateLabel  = CalendarReader.formatDateLabel(ev.startMillis),
                timeLabel  = CalendarReader.formatEventTime(ev),
                title      = ev.title,
                sortMillis = ev.startMillis
            )
        }.sortedBy { it.sortMillis }
    }

    /**
     * Parses Gemma output. Lenient: accepts any line containing a date pattern and colon.
     * If Gemma returns suspiciously few results, caller should fall back to rawFallback().
     */
    fun parseGemmaOutput(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val datePattern = Regex("""^\d{1,2}/\d{1,2}.*:.*\S""")
        return raw.lines()
            .map { it.trim() }
            .filter { it == "일정 없음" || datePattern.matches(it) }
    }

    /**
     * Formats raw entries directly — used when Gemma over-filters or returns too few results.
     */
    fun rawFallback(entries: List<ScheduleEntry>): List<String> {
        if (entries.isEmpty()) return listOf("일정 없음")
        return entries.map { entry ->
            "${entry.dateLabel} : ${entry.title}"
        }
    }

    /**
     * Decides whether to use Gemma output or fall back to raw entries.
     * Falls back if Gemma returned fewer than half the expected items.
     */
    fun resolveLines(gemmaRaw: String, entries: List<ScheduleEntry>): List<String> {
        if (gemmaRaw.isBlank()) return rawFallback(entries)
        val parsed = parseGemmaOutput(gemmaRaw)
        if (parsed.size == 1 && parsed[0] == "일정 없음" && entries.isNotEmpty()) {
            return rawFallback(entries)
        }
        if (entries.isNotEmpty() && parsed.size < (entries.size + 1) / 2) {
            return rawFallback(entries)
        }
        return parsed.ifEmpty { rawFallback(entries) }
    }

    fun formatForNotification(lines: List<String>, maxItems: Int = 5): String {
        if (lines.isEmpty()) return "앞으로 7일 이내 일정이 없습니다."
        val shown = lines.take(maxItems)
        val more  = lines.size - shown.size
        return buildString {
            shown.forEach { appendLine(it) }
            if (more > 0) append("외 $more 건 더 있음")
        }.trimEnd()
    }
}
