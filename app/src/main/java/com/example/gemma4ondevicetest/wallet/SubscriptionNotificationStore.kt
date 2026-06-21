package com.example.gemma4ondevicetest.wallet

import android.content.Context

class SubscriptionNotificationStore(context: Context) {

    private val inbox = NotificationInboxStore(context)

    fun append(raw: WalletRawNotification) {
        inbox.markSubscriptionEligible(raw)
    }

    fun loadPending(limit: Int): List<SubscriptionRawNotification> =
        inbox.loadPendingSubscription(limit).map { it.toSubscriptionRawNotification() }

    fun pendingCount(): Int = inbox.countPendingSubscription()

    fun markAnalyzed(ids: Collection<String>, analyzedAt: Long) {
        inbox.markSubscriptionAnalyzed(ids, analyzedAt)
    }

    fun loadAll(): List<SubscriptionRawNotification> {
        return inbox.loadSubscriptionEntries().map { it.toSubscriptionRawNotification() }
    }

    private fun NotificationInboxEntry.toSubscriptionRawNotification() = SubscriptionRawNotification(
        id = id,
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        postedAt = postedAt,
        notificationKey = notificationKey,
        analyzedAt = subscriptionAnalyzedAt
    )
}
