package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.util.Log

class WalletExpenseCoordinator(
    context: Context,
    private val logStore: WalletNotificationLogStore
) {

    private val store = CardTransactionStore(context)
    private val repository = CardExpenseRepository(store)

    fun onRawNotification(raw: WalletRawNotification) {
        repository.resetIfMonthChanged(raw.monthKey)

        if (!WalletNotificationFilter.shouldProcess(raw)) {
            val reason = filterReason(raw)
            Log.d(TAG, "filtered out: ${raw.title} ($reason)")
            logStore.updateOutcome(raw.notificationKey, "filtered", reason)
            return
        }

        when (val result = WalletNotificationParser.parse(raw)) {
            is ParseResult.Success -> {
                val saved = repository.saveParsedTransaction(result.transaction, raw.postedAt)
                if (saved == null) {
                    Log.d(TAG, "duplicate skipped: ${raw.notificationKey}")
                    logStore.updateOutcome(raw.notificationKey, "duplicate", "dedupeKey 중복")
                } else {
                    Log.i(TAG, "saved: ${saved.merchantName} ${saved.amount}원 [${saved.status}]")
                    logStore.updateOutcome(raw.notificationKey, "saved", "${saved.merchantName ?: ""} ${saved.amount}원")
                }
            }
            is ParseResult.Failure -> {
                Log.w(TAG, "parse failed: ${result.failure.reason} | ${raw.title} ${raw.text}")
                logStore.updateOutcome(raw.notificationKey, "parse_failed", result.failure.reason)
            }
        }
    }

    private fun filterReason(raw: WalletRawNotification): String {
        val combined = listOf(raw.title, raw.text, raw.bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return when {
            combined.isBlank() -> "본문 없음"
            WalletParserRules.hasExcludeKeyword(combined) -> {
                val found = WalletParserRules.EXCLUDE_KEYWORDS.firstOrNull { combined.contains(it) } ?: "?"
                "제외 키워드: $found"
            }
            !WalletParserRules.hasFinanceKeyword(combined) -> "금융 키워드 없음"
            else -> "필터됨"
        }
    }

    fun getMonthlySummary(monthKey: String): MonthlyCardSummary =
        repository.getMonthlySummary(monthKey)

    fun getRecentTransactions(limit: Int = 20): List<CardTransactionRecord> =
        repository.findRecentTransactions(limit)

    companion object {
        private const val TAG = "WalletCoordinator"
    }
}
