package com.example.gemma4ondevicetest.wallet

data class WalletRawNotification(
    val monthKey: String,
    val packageName: String,
    val appLabel: String,
    val postedAt: Long,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val notificationKey: String
)

enum class TransactionStatus { APPROVED, CANCELLED, UNKNOWN }

data class ParsedCardTransaction(
    val sourcePackage: String,
    val notificationKey: String,
    val monthKey: String,
    val approvedAt: String?,
    val cardLabel: String?,
    val merchantName: String?,
    val amount: Long,
    val currency: String,
    val installmentText: String?,
    val status: TransactionStatus,
    val rawTitle: String,
    val rawBody: String,
    val dedupeKey: String
)

data class ParseFailure(
    val notificationKey: String,
    val rawTitle: String,
    val rawBody: String,
    val reason: String
)

sealed class ParseResult {
    data class Success(val transaction: ParsedCardTransaction) : ParseResult()
    data class Failure(val failure: ParseFailure) : ParseResult()
}
