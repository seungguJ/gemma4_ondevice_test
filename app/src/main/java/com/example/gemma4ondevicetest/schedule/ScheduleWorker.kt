package com.example.gemma4ondevicetest.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gemma4ondevicetest.LlmEngine
import com.example.gemma4ondevicetest.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val rawReply = withContext(Dispatchers.IO) {
            val wasLoaded = LlmEngine.isLoaded
            if (!wasLoaded) {
                val source = ModelStore.getSelectedModel(applicationContext)
                if (!ModelStore.hasModel(applicationContext, source)) return@withContext null
                if (!LlmEngine.loadModel(applicationContext, source)) return@withContext null
            }

            try {
                val result = LlmEngine.generateForSession(
                    sessionId = "schedule_worker_session",
                    prompt    = summary.prompt,
                    config    = LlmEngine.LlmConfig(
                        maxTokens         = 300,
                        systemInstruction = SYSTEM_INSTRUCTION
                    )
                )
                LlmEngine.clearSession("schedule_worker_session")
                result
            } finally {
                if (!wasLoaded) LlmEngine.free()
            }
        }

        val lines = SchedulePromptBuilder.resolveLines(rawReply.orEmpty(), summary.entries)

        val body = SchedulePromptBuilder.formatForNotification(lines)

        ScheduleNotificationHelper.showSummary(
            applicationContext,
            title = "일주일 일정 요약",
            body  = body
        )

        return Result.success()
    }

    companion object {
        private const val SYSTEM_INSTRUCTION =
            "반드시 한국어로만 답하라. 지시한 출력 형식 외에 다른 말은 절대 하지 않는다. 각 줄은 반드시 '날짜 : 항목명' 형식이어야 한다."
    }
}
