package com.example.gemma4ondevicetest.wallet

object CardExpenseDeduplicator {

    fun isDuplicate(incoming: ParsedCardTransaction, existing: List<CardTransactionRecord>): Boolean {
        // 1순위: notificationKey 동일
        if (existing.any { it.notificationKey == incoming.notificationKey }) return true
        // 2순위: dedupeKey 동일
        if (existing.any { it.dedupeKey == incoming.dedupeKey }) return true
        return false
    }
}
