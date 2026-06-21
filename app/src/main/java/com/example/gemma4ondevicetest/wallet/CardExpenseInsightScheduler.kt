package com.example.gemma4ondevicetest.wallet

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.UUID

object CardExpenseInsightScheduler {

    const val WORK_NAME = "card-expense-insight-analysis"

    fun enqueue(
        context: Context,
        forceRun: Boolean = false,
        includeLedgerTransactions: Boolean = false,
        replaceExisting: Boolean = true
    ): UUID {
        val builder = OneTimeWorkRequestBuilder<CardExpenseInsightWorker>()
            .setInputData(
                workDataOf(
                    CardExpenseInsightWorker.KEY_FORCE_RUN to forceRun,
                    CardExpenseInsightWorker.KEY_INCLUDE_LEDGER_TRANSACTIONS to includeLedgerTransactions
                )
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        if (!forceRun) {
            builder.setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .build()
            )
            val lastCompletedAt = CardExpenseInsightStore(context).loadReport().lastCompletedAt
            val delayMillis = if (lastCompletedAt > 0L) {
                SubscriptionAnalysisScheduler.remainingCooldownMillis(lastCompletedAt)
            } else {
                SubscriptionAnalysisScheduler.COOLDOWN_MILLIS
            }
            if (delayMillis > 0L) {
                builder.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            }
        } else {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = builder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            when {
                forceRun && replaceExisting -> ExistingWorkPolicy.REPLACE
                forceRun -> ExistingWorkPolicy.APPEND_OR_REPLACE
                else -> ExistingWorkPolicy.KEEP
            },
            request
        )
        return request.id
    }
}
