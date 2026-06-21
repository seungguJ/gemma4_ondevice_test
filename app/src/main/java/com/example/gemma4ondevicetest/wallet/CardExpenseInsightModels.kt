package com.example.gemma4ondevicetest.wallet

data class ExpenseCategoryBreakdown(
    val category: String,
    val totalAmount: Long,
    val count: Int,
    val representativeNames: List<String>,
    val percentageOfTotal: Float
)

data class CardExpenseMonthlyInsight(
    val monthKey: String,
    val lastCompletedAt: Long,
    val analyzedCandidateCount: Int,
    val totalAmount: Long,
    val totalCount: Int,
    val categoryBreakdowns: List<ExpenseCategoryBreakdown>,
    val topMerchants: List<String>
)

data class CardExpenseInsightReport(
    val monthKey: String,
    val generatedAt: Long,
    val lastCompletedAt: Long,
    val pendingCount: Int,
    val analyzedCandidateCount: Int,
    val statusMessage: String,
    val categoryBreakdowns: List<ExpenseCategoryBreakdown>,
    val topMerchants: List<String>
) {
    val totalAmount: Long
        get() = categoryBreakdowns.sumOf { it.totalAmount }

    val totalCount: Int
        get() = categoryBreakdowns.sumOf { it.count }

    fun toMonthlyInsight(): CardExpenseMonthlyInsight = CardExpenseMonthlyInsight(
        monthKey = monthKey,
        lastCompletedAt = lastCompletedAt,
        analyzedCandidateCount = analyzedCandidateCount,
        totalAmount = totalAmount,
        totalCount = totalCount,
        categoryBreakdowns = categoryBreakdowns,
        topMerchants = topMerchants
    )
}
