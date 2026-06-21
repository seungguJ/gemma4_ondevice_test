package com.example.gemma4ondevicetest.wallet

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gemma4ondevicetest.LlmEngine
import com.example.gemma4ondevicetest.ModelRuntimeGate
import com.example.gemma4ondevicetest.ModelRuntimeResult
import com.example.gemma4ondevicetest.ModelStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SubscriptionAnalysisWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val rawStore = SubscriptionNotificationStore(appContext)
    private val insightStore = SubscriptionInsightStore(appContext)

    override suspend fun doWork(): Result {
        val currentReport = insightStore.loadReport()
        val pendingCount = rawStore.pendingCount()
        if (pendingCount == 0) {
            insightStore.updateStatus("분석할 정기결제 후보 알림이 없습니다.", 0)
            return Result.success()
        }

        val battery = SubscriptionAnalysisScheduler.currentBatteryGateStatus(applicationContext)
        if (!battery.isEligible) {
            insightStore.updateStatus(
                statusMessage = "충전 중이며 배터리 100%일 때만 분석합니다. 현재 ${battery.levelPercent}%.",
                pendingCount = pendingCount
            )
            return Result.retry()
        }

        val remainingCooldown = SubscriptionAnalysisScheduler.remainingCooldownMillis(currentReport.lastCompletedAt)
        if (remainingCooldown > 0L) {
            insightStore.updateStatus(
                statusMessage = "최근 분석이 완료되어 ${SubscriptionAnalysisScheduler.formatRemainingCooldown(remainingCooldown)} 뒤에 다시 분석합니다.",
                pendingCount = pendingCount
            )
            return Result.retry()
        }

        if (ModelRuntimeGate.isLoaded) {
            insightStore.updateStatus(
                statusMessage = "다른 화면이 모델을 사용 중이라 정기결제 분석을 잠시 보류했습니다.",
                pendingCount = pendingCount
            )
            return Result.retry()
        }

        val source = ModelStore.getSelectedModel(applicationContext)
        if (!ModelStore.hasModel(applicationContext, source)) {
            insightStore.updateStatus(
                statusMessage = "모델이 준비되지 않아 정기결제 분석을 실행할 수 없습니다.",
                pendingCount = pendingCount
            )
            return Result.success()
        }

        val batch = rawStore.loadPending(MAX_BATCH_SIZE)
        if (batch.isEmpty()) {
            insightStore.updateStatus("분석할 정기결제 후보 알림이 없습니다.", 0)
            return Result.success()
        }

        val prompt = buildPrompt(batch)
        val runtimeResult = ModelRuntimeGate.runBackgroundExclusive(
            context = applicationContext,
            sessionId = SESSION_ID,
            source = source,
            block = {
                LlmEngine.generateForSession(
                    sessionId = SESSION_ID,
                    prompt = prompt,
                    config = LlmEngine.LlmConfig(
                        maxTokens = 500,
                        systemInstruction = SYSTEM_INSTRUCTION
                    )
                )
            }
        )
        val response = when (runtimeResult) {
            ModelRuntimeResult.Busy -> {
                insightStore.updateStatus(
                    statusMessage = "다른 화면이 모델을 사용 중이라 정기결제 분석을 잠시 보류했습니다.",
                    pendingCount = pendingCount
                )
                return Result.retry()
            }
            ModelRuntimeResult.MissingModel -> {
                insightStore.updateStatus(
                    statusMessage = "모델이 준비되지 않아 정기결제 분석을 실행할 수 없습니다.",
                    pendingCount = pendingCount
                )
                return Result.success()
            }
            is ModelRuntimeResult.LoadFailed -> {
                insightStore.updateStatus(
                    statusMessage = runtimeResult.message,
                    pendingCount = pendingCount
                )
                return Result.retry()
            }
            is ModelRuntimeResult.Success -> runtimeResult.value
        }

        if (response.isBlank()) {
            insightStore.updateStatus(
                statusMessage = "AI 응답을 받지 못해 정기결제 분석을 완료하지 못했습니다.",
                pendingCount = pendingCount
            )
            return Result.retry()
        }

        val parsedReport = parseReport(response, batch)
        if (parsedReport == null) {
            insightStore.updateStatus(
                statusMessage = "AI 응답을 해석하지 못해 정기결제 분석을 완료하지 못했습니다.",
                pendingCount = pendingCount
            )
            return Result.retry()
        }

        val analyzedAt = System.currentTimeMillis()
        rawStore.markAnalyzed(batch.map { it.id }, analyzedAt)
        insightStore.saveReport(
            parsedReport.copy(
                generatedAt = analyzedAt,
                lastCompletedAt = analyzedAt,
                pendingCount = rawStore.pendingCount()
            )
        )
        return Result.success()
    }

    private fun buildPrompt(batch: List<SubscriptionRawNotification>): String {
        val payload = JSONArray().apply {
            batch.forEach { raw ->
                put(JSONObject().apply {
                    put("id", raw.id)
                    put("packageName", raw.packageName)
                    put("postedAt", raw.postedAt)
                    put("title", raw.title)
                    put("text", raw.text)
                    put("bigText", raw.bigText)
                    put("subText", raw.subText)
                })
            }
        }
        return buildString {
            appendLine("다음 알림 목록에서 정기결제 후보만 추려라.")
            appendLine("광고라고 추정되거나 결제와 무관하면 제외하라.")
            appendLine("응답은 반드시 JSON 객체 하나만 반환하라.")
            appendLine("JSON 스키마:")
            appendLine("{\"statusMessage\":\"...\",\"candidates\":[{\"notificationIds\":[\"...\"],\"serviceName\":\"...\",\"amountText\":\"...\",\"reason\":\"...\",\"sourcePackage\":\"...\",\"missableEvent\":true,\"missableReason\":\"...\"}]}")
            appendLine("serviceName은 사람이 읽을 수 있는 결제 서비스명으로 적고, 모르면 가장 가까운 상호명을 적어라.")
            appendLine("반복 결제 가능성이 낮으면 candidates에 넣지 마라.")
            appendLine("알림 데이터:")
            append(payload.toString())
        }
    }

    private fun parseReport(
        rawResponse: String,
        batch: List<SubscriptionRawNotification>
    ): SubscriptionAnalysisReport? {
        val jsonText = extractJsonObject(rawResponse) ?: return null
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val candidateArray = root.optJSONArray("candidates") ?: JSONArray()
        val candidates = (0 until candidateArray.length()).mapNotNull { index ->
            runCatching {
                val item = candidateArray.getJSONObject(index)
                val notificationIdsJson = item.optJSONArray("notificationIds") ?: JSONArray()
                val notificationIds = (0 until notificationIdsJson.length()).map { notificationIdsJson.getString(it) }
                val matched = batch.filter { it.id in notificationIds }
                val lastSeenAt = matched.maxOfOrNull { it.postedAt } ?: 0L
                SubscriptionCandidate(
                    id = UUID.randomUUID().toString(),
                    serviceName = item.optString("serviceName", "").ifBlank { "미상 결제" },
                    amountText = item.optString("amountText", ""),
                    reason = item.optString("reason", ""),
                    sourcePackage = item.optString("sourcePackage", matched.firstOrNull()?.packageName.orEmpty()),
                    notificationIds = notificationIds,
                    lastSeenAt = lastSeenAt,
                    missableEvent = item.optBoolean("missableEvent", false),
                    missableReason = item.optString("missableReason", "")
                )
            }.getOrNull()
        }

        return SubscriptionAnalysisReport(
            generatedAt = System.currentTimeMillis(),
            lastCompletedAt = 0L,
            statusMessage = root.optString("statusMessage", "정기결제 후보 분석 완료"),
            pendingCount = 0,
            candidates = candidates
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    companion object {
        private const val MAX_BATCH_SIZE = 40
        private const val SESSION_ID = "subscription_analysis_worker"
        private const val SYSTEM_INSTRUCTION =
            "반드시 한국어로만 판단하라. 출력은 JSON 객체 하나만 반환하고 설명 문장은 쓰지 마라. 실제 알림에 근거가 있는 경우만 정기결제 후보로 분류하라."
    }
}
