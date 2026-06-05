package com.example.gemma4ondevicetest.wallet

import java.util.UUID

class CardExpenseRepository(private val store: CardTransactionStore) {

    fun saveParsedTransaction(parsed: ParsedCardTransaction, postedAt: Long): CardTransactionRecord? {
        val existing = store.loadByMonth(parsed.monthKey)
        if (CardExpenseDeduplicator.isDuplicate(parsed, existing)) return null

        val record = CardTransactionRecord(
            id = UUID.randomUUID().toString(),
            monthKey = parsed.monthKey,
            sourcePackage = parsed.sourcePackage,
            notificationKey = parsed.notificationKey,
            approvedAt = parsed.approvedAt,
            postedAt = postedAt,
            cardLabel = parsed.cardLabel,
            merchantName = parsed.merchantName,
            amount = parsed.amount,
            currency = parsed.currency,
            status = parsed.status,
            rawTitle = parsed.rawTitle,
            rawBody = parsed.rawBody,
            createdAt = System.currentTimeMillis(),
            dedupeKey = parsed.dedupeKey
        )
        store.save(record)
        return record
    }

    fun findById(id: String): CardTransactionRecord? = store.findById(id)

    fun findRecentTransactions(limit: Int): List<CardTransactionRecord> =
        store.loadAll().sortedByDescending { it.postedAt }.take(limit)

    fun findTransactionsByMonth(monthKey: String): List<CardTransactionRecord> =
        store.loadByMonth(monthKey).sortedByDescending { it.postedAt }

    fun getMonthlySummary(monthKey: String): MonthlyCardSummary {
        val txns = store.loadByMonth(monthKey)
        val approved = txns.filter { it.status == TransactionStatus.APPROVED }
        val cancelled = txns.filter { it.status == TransactionStatus.CANCELLED }
        return MonthlyCardSummary(
            monthKey = monthKey,
            grossApproved = approved.sumOf { it.amount },
            grossCancelled = cancelled.sumOf { it.amount },
            transactionCount = txns.size
        )
    }

    fun resetIfMonthChanged(currentMonth: String) {
        val stored = store.storedMonthKey()
        if (stored != null && stored != currentMonth) {
            store.deleteAll()
        }
        store.updateStoredMonthKey(currentMonth)
    }
}
