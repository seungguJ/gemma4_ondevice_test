package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId

class CardExpenseInsightStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveReport(report: CardExpenseInsightReport) {
        prefs.edit()
            .putString(KEY_REPORT, report.toJson().toString())
            .apply()
    }

    fun loadReport(): CardExpenseInsightReport {
        val json = prefs.getString(KEY_REPORT, null) ?: return emptyReport()
        return runCatching { parseReport(JSONObject(json)) }.getOrElse { emptyReport() }
    }

    fun loadHistory(): List<CardExpenseMonthlyInsight> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val currentMonth = YearMonth.now(SEOUL_ZONE).toString()
        return runCatching { parseHistory(JSONArray(json)) }
            .getOrElse { emptyList() }
            .filter { it.monthKey.isNotBlank() && it.monthKey != currentMonth }
    }

    fun ensureCurrentMonth(currentMonth: String) {
        val report = loadReport()
        if (report.monthKey.isNotBlank() && report.monthKey != currentMonth) {
            if (CardExpenseInsightHistoryReducer.shouldArchive(report)) {
                val archived = CardExpenseInsightHistoryReducer.upsert(
                    loadHistory(),
                    report.toMonthlyInsight(),
                    MAX_HISTORY_ENTRIES
                )
                prefs.edit().putString(KEY_HISTORY, archived.toJson().toString()).apply()
            }
            saveReport(emptyReport(currentMonth))
        }
    }

    fun reset() {
        prefs.edit()
            .remove(KEY_REPORT)
            .remove(KEY_HISTORY)
            .apply()
    }

    /** 월별 기록은 보존한 채 현재 달 분석 결과만 비운다. 전체 재분석 직전에 사용한다. */
    fun clearCurrentReport() {
        saveReport(emptyReport(YearMonth.now(SEOUL_ZONE).toString()))
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

    private fun emptyReport(monthKey: String = "") = CardExpenseInsightReport(
        monthKey = monthKey,
        generatedAt = 0L,
        lastCompletedAt = 0L,
        pendingCount = 0,
        analyzedCandidateCount = 0,
        statusMessage = "아직 분석 기록이 없습니다.",
        categoryBreakdowns = emptyList(),
        topMerchants = emptyList()
    )

    private fun CardExpenseInsightReport.toJson(): JSONObject = JSONObject().apply {
        put("monthKey", monthKey)
        put("generatedAt", generatedAt)
        put("lastCompletedAt", lastCompletedAt)
        put("pendingCount", pendingCount)
        put("analyzedCandidateCount", analyzedCandidateCount)
        put("statusMessage", statusMessage)
        put("categoryBreakdowns", JSONArray().apply {
            categoryBreakdowns.forEach { cb ->
                put(JSONObject().apply {
                    put("category", cb.category)
                    put("totalAmount", cb.totalAmount)
                    put("count", cb.count)
                    put("representativeNames", JSONArray(cb.representativeNames))
                    put("percentageOfTotal", cb.percentageOfTotal.toDouble())
                })
            }
        })
        put("topMerchants", JSONArray(topMerchants))
    }

    private fun List<CardExpenseMonthlyInsight>.toJson(): JSONArray = JSONArray().apply {
        forEach { item ->
            put(item.toJson())
        }
    }

    private fun CardExpenseMonthlyInsight.toJson(): JSONObject = JSONObject().apply {
        put("monthKey", monthKey)
        put("lastCompletedAt", lastCompletedAt)
        put("analyzedCandidateCount", analyzedCandidateCount)
        put("totalAmount", totalAmount)
        put("totalCount", totalCount)
        put("categoryBreakdowns", JSONArray().apply {
            categoryBreakdowns.forEach { cb ->
                put(JSONObject().apply {
                    put("category", cb.category)
                    put("totalAmount", cb.totalAmount)
                    put("count", cb.count)
                    put("representativeNames", JSONArray(cb.representativeNames))
                    put("percentageOfTotal", cb.percentageOfTotal.toDouble())
                })
            }
        })
        put("topMerchants", JSONArray(topMerchants))
    }

    private fun parseReport(json: JSONObject): CardExpenseInsightReport {
        val breakdowns = parseBreakdowns(json.optJSONArray("categoryBreakdowns"))
        val merchantsArray = json.optJSONArray("topMerchants") ?: JSONArray()
        val topMerchants = (0 until merchantsArray.length()).map { merchantsArray.getString(it) }
        return CardExpenseInsightReport(
            monthKey = json.optString("monthKey", ""),
            generatedAt = json.optLong("generatedAt", 0L),
            lastCompletedAt = json.optLong("lastCompletedAt", 0L),
            pendingCount = json.optInt("pendingCount", 0),
            analyzedCandidateCount = json.optInt("analyzedCandidateCount", 0),
            statusMessage = json.optString("statusMessage", "아직 분석 기록이 없습니다."),
            categoryBreakdowns = breakdowns,
            topMerchants = topMerchants
        )
    }

    private fun parseHistory(array: JSONArray): List<CardExpenseMonthlyInsight> {
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val breakdowns = parseBreakdowns(item.optJSONArray("categoryBreakdowns"))
                val merchantsArray = item.optJSONArray("topMerchants") ?: JSONArray()
                CardExpenseMonthlyInsight(
                    monthKey = item.getString("monthKey"),
                    lastCompletedAt = item.optLong("lastCompletedAt", 0L),
                    analyzedCandidateCount = item.optInt("analyzedCandidateCount", 0),
                    totalAmount = item.optLong("totalAmount", breakdowns.sumOf { it.totalAmount }),
                    totalCount = item.optInt("totalCount", breakdowns.sumOf { it.count }),
                    categoryBreakdowns = breakdowns,
                    topMerchants = (0 until merchantsArray.length()).map { merchantsArray.getString(it) }
                )
            }.getOrNull()
        }.sortedByDescending { it.monthKey }
    }

    private fun parseBreakdowns(breakdownArray: JSONArray?): List<ExpenseCategoryBreakdown> {
        val array = breakdownArray ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val item = array.getJSONObject(i)
                val namesArray = item.optJSONArray("representativeNames") ?: JSONArray()
                ExpenseCategoryBreakdown(
                    category = item.getString("category"),
                    totalAmount = item.optLong("totalAmount", 0L),
                    count = item.optInt("count", 0),
                    representativeNames = (0 until namesArray.length()).map { namesArray.getString(it) },
                    percentageOfTotal = item.optDouble("percentageOfTotal", 0.0).toFloat()
                )
            }.getOrNull()
        }
    }

    companion object {
        private const val PREFS_NAME = "card_expense_insight_store"
        private const val KEY_REPORT = "insight_report"
        private const val KEY_HISTORY = "insight_history"
        private const val MAX_HISTORY_ENTRIES = 12
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
