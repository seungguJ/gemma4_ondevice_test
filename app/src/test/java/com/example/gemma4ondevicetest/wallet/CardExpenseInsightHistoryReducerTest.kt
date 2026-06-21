package com.example.gemma4ondevicetest.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CardExpenseInsightHistoryReducerTest {

    @Test
    fun `의미 있는 리포트만 아카이브한다`() {
        val empty = CardExpenseInsightReport(
            monthKey = "2026-06",
            generatedAt = 0L,
            lastCompletedAt = 0L,
            pendingCount = 0,
            analyzedCandidateCount = 0,
            statusMessage = "아직 분석 기록이 없습니다.",
            overallSummary = "",
            categoryBreakdowns = emptyList(),
            topMerchants = emptyList()
        )
        val meaningful = empty.copy(
            analyzedCandidateCount = 3,
            overallSummary = "식비 비중이 높습니다.",
            categoryBreakdowns = listOf(breakdown("식비", 54000L, 3, 62f))
        )

        assertFalse(CardExpenseInsightHistoryReducer.shouldArchive(empty))
        assertTrue(CardExpenseInsightHistoryReducer.shouldArchive(meaningful))
    }

    @Test
    fun `월간 스냅샷은 같은 월이면 덮어쓰고 최신 월 순으로 유지한다`() {
        val june = monthly("2026-06", totalAmount = 64000L, totalCount = 4)
        val may = monthly("2026-05", totalAmount = 42000L, totalCount = 3)
        val juneUpdated = monthly("2026-06", totalAmount = 70000L, totalCount = 5)

        val history = CardExpenseInsightHistoryReducer.upsert(
            history = listOf(may, june),
            snapshot = juneUpdated,
            maxEntries = 12
        )

        assertEquals(listOf("2026-06", "2026-05"), history.map { it.monthKey })
        assertEquals(70000L, history.first().totalAmount)
        assertEquals(5, history.first().totalCount)
    }

    @Test
    fun `히스토리는 최대 보관 개수를 넘기지 않는다`() {
        val monthKeys = listOf(
            "2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07",
            "2026-08", "2026-09", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02"
        )
        val history = monthKeys.mapIndexed { index, monthKey ->
            monthly(
                monthKey = monthKey,
                totalAmount = (index + 1) * 1000L,
                totalCount = index + 1
            )
        }

        val reduced = CardExpenseInsightHistoryReducer.upsert(
            history = history.dropLast(1),
            snapshot = history.last(),
            maxEntries = 12
        )

        assertEquals(12, reduced.size)
        assertEquals("2027-02", reduced.first().monthKey)
        assertEquals("2026-02", reduced.last().monthKey)
    }

    @Test
    fun `전월 조회는 현재 월보다 작은 가장 최근 월을 반환한다`() {
        val april = monthly("2026-04", totalAmount = 20000L, totalCount = 2)
        val may = monthly("2026-05", totalAmount = 30000L, totalCount = 3)
        val june = monthly("2026-06", totalAmount = 40000L, totalCount = 4)

        val previous = CardExpenseInsightHistoryReducer.findPreviousMonth(
            history = listOf(april, may, june),
            currentMonthKey = "2026-06"
        )

        assertSame(may, previous)
    }

    @Test
    fun `전월이 없으면 null을 반환한다`() {
        val currentOnly = monthly("2026-06", totalAmount = 40000L, totalCount = 4)

        val previous = CardExpenseInsightHistoryReducer.findPreviousMonth(
            history = listOf(currentOnly),
            currentMonthKey = "2026-06"
        )

        assertNull(previous)
    }

    @Test
    fun `리포트를 월간 스냅샷으로 변환할 때 합계 정보를 보존한다`() {
        val report = CardExpenseInsightReport(
            monthKey = "2026-06",
            generatedAt = 10L,
            lastCompletedAt = 20L,
            pendingCount = 0,
            analyzedCandidateCount = 4,
            statusMessage = "완료",
            overallSummary = "식비와 쇼핑 비중이 큽니다.",
            categoryBreakdowns = listOf(
                breakdown("식비", 54000L, 3, 60f),
                breakdown("쇼핑", 36000L, 1, 40f)
            ),
            topMerchants = listOf("스타벅스", "쿠팡")
        )

        val snapshot = report.toMonthlyInsight()

        assertEquals("2026-06", snapshot.monthKey)
        assertEquals(90000L, snapshot.totalAmount)
        assertEquals(4, snapshot.totalCount)
        assertEquals(report.overallSummary, snapshot.overallSummary)
        assertEquals(report.topMerchants, snapshot.topMerchants)
    }

    private fun monthly(
        monthKey: String,
        totalAmount: Long,
        totalCount: Int
    ) = CardExpenseMonthlyInsight(
        monthKey = monthKey,
        lastCompletedAt = 100L,
        analyzedCandidateCount = totalCount,
        totalAmount = totalAmount,
        totalCount = totalCount,
        overallSummary = "$monthKey 요약",
        categoryBreakdowns = listOf(breakdown("식비", totalAmount, totalCount, 100f)),
        topMerchants = listOf("스타벅스")
    )

    private fun breakdown(
        category: String,
        totalAmount: Long,
        count: Int,
        percentage: Float
    ) = ExpenseCategoryBreakdown(
        category = category,
        totalAmount = totalAmount,
        count = count,
        representativeNames = listOf("대표"),
        percentageOfTotal = percentage
    )
}
