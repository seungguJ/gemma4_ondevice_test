package com.example.gemma4ondevicetest.wallet

object CardExpenseDeduplicator {

    fun isDuplicate(
        incoming: ParsedCardTransaction,
        postedAt: Long,
        existing: List<CardTransactionRecord>
    ): Boolean {
        // 1순위: 같은 notificationKey가 같은 시각에 다시 들어온 경우만 중복으로 본다.
        if (existing.any { it.notificationKey == incoming.notificationKey && it.postedAt == postedAt }) return true
        // 2순위: dedupeKey 동일
        if (existing.any { it.dedupeKey == incoming.dedupeKey }) return true
        return false
    }
}
