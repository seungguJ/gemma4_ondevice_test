package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SubscriptionInsightStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveReport(report: SubscriptionAnalysisReport) {
        prefs.edit().putString(KEY_REPORT, report.toJson().toString()).apply()
    }

    fun loadReport(): SubscriptionAnalysisReport {
        val json = prefs.getString(KEY_REPORT, null) ?: return emptyReport()
        return runCatching {
            parseReport(JSONObject(json))
        }.getOrElse { emptyReport() }
    }

    fun updateStatus(statusMessage: String, pendingCount: Int) {
        val current = loadReport()
        saveReport(
            current.copy(
                generatedAt = System.currentTimeMillis(),
                statusMessage = statusMessage,
                pendingCount = pendingCount
            )
        )
    }

    private fun emptyReport() = SubscriptionAnalysisReport(
        generatedAt = 0L,
        lastCompletedAt = 0L,
        statusMessage = "아직 분석 기록이 없습니다.",
        pendingCount = 0,
        candidates = emptyList()
    )

    private fun SubscriptionAnalysisReport.toJson(): JSONObject = JSONObject().apply {
        put("generatedAt", generatedAt)
        put("lastCompletedAt", lastCompletedAt)
        put("statusMessage", statusMessage)
        put("pendingCount", pendingCount)
        put("candidates", JSONArray().apply {
            candidates.forEach { candidate ->
                put(JSONObject().apply {
                    put("id", candidate.id)
                    put("serviceName", candidate.serviceName)
                    put("amountText", candidate.amountText)
                    put("reason", candidate.reason)
                    put("sourcePackage", candidate.sourcePackage)
                    put("notificationIds", JSONArray(candidate.notificationIds))
                    put("lastSeenAt", candidate.lastSeenAt)
                    put("missableEvent", candidate.missableEvent)
                    put("missableReason", candidate.missableReason)
                })
            }
        })
    }

    private fun parseReport(json: JSONObject): SubscriptionAnalysisReport {
        val candidateArray = json.optJSONArray("candidates") ?: JSONArray()
        val candidates = (0 until candidateArray.length()).mapNotNull { index ->
            runCatching {
                val item = candidateArray.getJSONObject(index)
                val notificationIdsJson = item.optJSONArray("notificationIds") ?: JSONArray()
                SubscriptionCandidate(
                    id = item.getString("id"),
                    serviceName = item.getString("serviceName"),
                    amountText = item.optString("amountText", ""),
                    reason = item.optString("reason", ""),
                    sourcePackage = item.optString("sourcePackage", ""),
                    notificationIds = (0 until notificationIdsJson.length()).map { notificationIdsJson.getString(it) },
                    lastSeenAt = item.optLong("lastSeenAt", 0L),
                    missableEvent = item.optBoolean("missableEvent", false),
                    missableReason = item.optString("missableReason", "")
                )
            }.getOrNull()
        }

        return SubscriptionAnalysisReport(
            generatedAt = json.optLong("generatedAt", 0L),
            lastCompletedAt = json.optLong("lastCompletedAt", 0L),
            statusMessage = json.optString("statusMessage", "아직 분석 기록이 없습니다."),
            pendingCount = json.optInt("pendingCount", 0),
            candidates = candidates
        )
    }

    companion object {
        private const val PREFS_NAME = "subscription_insight_store"
        private const val KEY_REPORT = "analysis_report"
    }
}
