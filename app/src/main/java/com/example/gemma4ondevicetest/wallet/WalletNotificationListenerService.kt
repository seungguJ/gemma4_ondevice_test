package com.example.gemma4ondevicetest.wallet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class WalletNotificationListenerService : NotificationListenerService() {

    private lateinit var coordinator: WalletExpenseCoordinator
    private lateinit var logStore: WalletNotificationLogStore

    override fun onCreate() {
        super.onCreate()
        logStore = WalletNotificationLogStore(applicationContext)
        coordinator = WalletExpenseCoordinator(applicationContext, logStore)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WalletParserRules.ALLOWED_PACKAGES) return

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

        logStore.append(
            WalletNotificationLogEntry(
                id = UUID.randomUUID().toString(),
                receivedAt = sbn.postTime,
                packageName = sbn.packageName,
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                notificationKey = sbn.key,
                outcome = "received",
                outcomeDetail = ""
            )
        )

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

        coordinator.onRawNotification(raw)
    }

    companion object {
        private const val TAG = "WalletListener"
    }
}
