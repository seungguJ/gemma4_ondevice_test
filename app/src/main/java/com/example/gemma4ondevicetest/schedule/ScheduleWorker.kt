package com.example.gemma4ondevicetest.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gemma4ondevicetest.LlmEngine
import com.example.gemma4ondevicetest.ModelRuntimeGate
import com.example.gemma4ondevicetest.ModelRuntimeResult
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now    = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        val events = CalendarReader.getUpcomingWeekEvents(applicationContext, now)
        val summary = SchedulePromptBuilder.build(events, now)

        val runtimeResult = ModelRuntimeGate.runBackgroundExclusive(
            context = applicationContext,
            sessionId = SESSION_ID,
            block = {
                LlmEngine.generateForSession(
                    sessionId = SESSION_ID,
                    prompt    = summary.prompt,
                    config    = LlmEngine.LlmConfig(
                        maxTokens         = 300,
                        systemInstruction = SYSTEM_INSTRUCTION
                    )
                )
            }
        )
        val rawReply = when (runtimeResult) {
            is ModelRuntimeResult.Success -> runtimeResult.value
            else -> ""
        }

        val lines = SchedulePromptBuilder.resolveLines(rawReply, summary.entries)

        val body = SchedulePromptBuilder.formatForNotification(lines)

        ScheduleNotificationHelper.showSummary(
            applicationContext,
            title = "일주일 일정 요약",
            body  = body
        )

        return Result.success()
    }

    companion object {
        private const val SESSION_ID = "schedule_worker_session"
        private const val SYSTEM_INSTRUCTION =
            "반드시 한국어로만 답하라. 지시한 출력 형식 외에 다른 말은 절대 하지 않는다. 각 줄은 반드시 '날짜 : 항목명' 형식이어야 한다."
    }
}
