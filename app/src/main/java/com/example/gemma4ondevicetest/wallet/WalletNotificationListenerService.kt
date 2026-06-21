package com.example.gemma4ondevicetest.wallet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WalletNotificationListenerService : NotificationListenerService() {

    private lateinit var coordinator: WalletExpenseCoordinator
    private lateinit var subscriptionCoordinator: SubscriptionAnalysisCoordinator

    override fun onCreate() {
        super.onCreate()
        coordinator = WalletExpenseCoordinator(applicationContext)
        subscriptionCoordinator = SubscriptionAnalysisCoordinator(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = run {
            val big = extras.getCharSequence("android.bigText")?.toString() ?: ""
            if (big.isNotBlank()) big else {
                // InboxStyle notifications (e.g. Samsung Pay) store expanded lines in textLines, not bigText
                extras.getCharSequenceArray("android.textLines")
                    ?.mapNotNull { it?.toString() }
                    ?.filter { it.isNotBlank() }
                    ?.joinToString(" ")
                    ?: ""
            }
        }
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        val monthKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        val raw = WalletRawNotification(
            monthKey = monthKey,
            packageName = sbn.packageName,
            appLabel = sbn.packageName,
            postedAt = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            notificationKey = sbn.key
        )

        if (sbn.packageName in WalletParserRules.ALLOWED_PACKAGES ||
            WalletParserRules.titleIndicatesCard(title)
        ) {
            coordinator.onRawNotification(raw)
        }

        if (SubscriptionAnalysisRules.supportsPackage(sbn.packageName)) {
            subscriptionCoordinator.onRawNotification(raw)
            Log.d(TAG, "subscription notification queued: $title")
        }
    }

    companion object {
        private const val TAG = "WalletListener"
    }
}
