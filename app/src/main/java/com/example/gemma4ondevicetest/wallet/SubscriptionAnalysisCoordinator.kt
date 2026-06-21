package com.example.gemma4ondevicetest.wallet

import android.content.Context

class SubscriptionAnalysisCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val inbox = NotificationInboxStore(appContext)
    private val store = SubscriptionNotificationStore(appContext)
    private val insightStore = SubscriptionInsightStore(appContext)
    private val candidateStore = CardExpenseCandidateStore(appContext)
    private val cardInsightStore = CardExpenseInsightStore(appContext)

    fun onRawNotification(raw: WalletRawNotification) {
        val decision = SubscriptionAnalysisRules.evaluate(raw)
        if (!decision.accepted) return

        inbox.markAnalysisEligible(
            raw = raw,
            subscriptionEligible = true,
            cardInsightEligible = true
        )
        insightStore.updateStatus(
            statusMessage = "정기결제 후보 분석 대기 중입니다. 자동 분석은 24시간 대기 후 충전 중이며 배터리 100%일 때 실행됩니다.",
            pendingCount = store.pendingCount()
        )
        SubscriptionAnalysisScheduler.enqueueAutomatic(appContext)

        cardInsightStore.updateStatus(
            statusMessage = "카드 인사이트 분석 대기 중입니다. 자동 분석은 24시간 대기 후 충전 중이며 배터리 100%일 때 실행됩니다.",
            pendingCount = candidateStore.pendingCount()
        )
        CardExpenseInsightScheduler.enqueue(appContext)
    }
}
