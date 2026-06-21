package com.example.gemma4ondevicetest.wallet

data class SubscriptionRawNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val postedAt: Long,
    val notificationKey: String,
    val analyzedAt: Long? = null
) {
    val combinedText: String
        get() = listOf(title, text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

data class SubscriptionCandidate(
    val id: String,
    val serviceName: String,
    val amountText: String,
    val reason: String,
    val sourcePackage: String,
    val notificationIds: List<String>,
    val lastSeenAt: Long,
    val missableEvent: Boolean,
    val missableReason: String
)

data class SubscriptionAnalysisReport(
    val generatedAt: Long,
    val lastCompletedAt: Long,
    val statusMessage: String,
    val pendingCount: Int,
    val candidates: List<SubscriptionCandidate>
)

data class BatteryGateStatus(
    val isCharging: Boolean,
    val levelPercent: Int
) {
    val isEligible: Boolean
        get() = isCharging && levelPercent >= 100
}

data class SubscriptionFilterDecision(
    val accepted: Boolean,
    val reason: String
)
