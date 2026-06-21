package com.example.gemma4ondevicetest.wallet

object CardExpenseInsightHistoryReducer {

    fun shouldArchive(report: CardExpenseInsightReport): Boolean {
        return report.monthKey.isNotBlank() && (
            report.analyzedCandidateCount > 0 ||
                report.categoryBreakdowns.isNotEmpty() ||
                report.topMerchants.isNotEmpty()
            )
    }

    fun upsert(
        history: List<CardExpenseMonthlyInsight>,
        snapshot: CardExpenseMonthlyInsight,
        maxEntries: Int = 12
    ): List<CardExpenseMonthlyInsight> {
        val next = history.toMutableList()
        val index = next.indexOfFirst { it.monthKey == snapshot.monthKey }
        if (index >= 0) {
            next[index] = snapshot
        } else {
            next.add(snapshot)
        }
        return next
            .sortedByDescending { it.monthKey }
            .take(maxEntries)
    }

    fun findPreviousMonth(
        history: List<CardExpenseMonthlyInsight>,
        currentMonthKey: String
    ): CardExpenseMonthlyInsight? {
        return history
            .filter { it.monthKey < currentMonthKey }
            .maxByOrNull { it.monthKey }
    }
}
