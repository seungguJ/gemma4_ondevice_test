package com.example.gemma4ondevicetest.wallet

data class CardTransactionRecord(
    val id: String,
    val monthKey: String,
    val sourcePackage: String,
    val notificationKey: String,
    val approvedAt: String?,
    val postedAt: Long,
    val cardLabel: String?,
    val merchantName: String?,
    val amount: Long,
    val currency: String,
    val status: TransactionStatus,
    val rawTitle: String,
    val rawBody: String,
    val createdAt: Long,
    val dedupeKey: String
)

data class MonthlyCardSummary(
    val monthKey: String,
    val grossApproved: Long,
    val grossCancelled: Long,
    val transactionCount: Int
) {
    val netSpent: Long get() = grossApproved - grossCancelled
}
