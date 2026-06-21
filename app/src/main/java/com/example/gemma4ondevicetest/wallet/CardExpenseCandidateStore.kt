package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

enum class CardExpenseInsightCandidateSource {
    INBOX,
    TRANSACTION
}

data class CardExpenseInsightCandidate(
    val id: String,
    val sourceId: String,
    val source: CardExpenseInsightCandidateSource,
    val title: String,
    val body: String,
    val postedAt: Long,
    val amount: Long,
    val merchantName: String,
    val analyzedAt: Long?,
    val category: String? = null
) {
    val isAnalyzed: Boolean get() = analyzedAt != null
    val combinedText: String
        get() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
}

class CardExpenseCandidateStore(context: Context) {

    private val inbox = NotificationInboxStore(context)
    private val transactionStore = CardTransactionStore(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("card_expense_insight_candidates", Context.MODE_PRIVATE)

    fun resetIfMonthChanged(currentMonth: String) {
        inbox.resetIfMonthChanged(currentMonth)
    }

    fun append(raw: WalletRawNotification) {
        inbox.markCardInsightEligible(raw)
    }

    fun loadPending(includeLedgerTransactions: Boolean = false): List<CardExpenseInsightCandidate> {
        val inboxEntries = inbox.loadPendingCardInsight()
        val inboxCandidates = inboxEntries.map { it.toInsightCandidate() }
        if (!includeLedgerTransactions) return inboxCandidates

        val inboxNotificationKeys = inbox.loadCardInsightEntries()
            .map { it.notificationKey }
            .toSet()
        val transactionCandidates = transactionStore.loadAll()
            .asSequence()
            .filter { it.id !in analyzedTransactionIds() }
            .filter { it.notificationKey !in inboxNotificationKeys }
            .sortedBy { it.postedAt }
            .map { it.toInsightCandidate() }
            .toList()

        return inboxCandidates + transactionCandidates
    }

    fun pendingCount(): Int = inbox.countPendingCardInsight()

    fun totalCount(): Int = inbox.totalCardInsightCandidates()

    fun markAnalyzed(candidates: Collection<CardExpenseInsightCandidate>, analyzedAt: Long) {
        val inboxIds = candidates
            .filter { it.source == CardExpenseInsightCandidateSource.INBOX }
            .map { it.sourceId }
        val transactionIds = candidates
            .filter { it.source == CardExpenseInsightCandidateSource.TRANSACTION }
            .map { it.sourceId }
        inbox.markCardInsightAnalyzed(inboxIds, analyzedAt)
        markTransactionsAnalyzed(transactionIds)
    }

    fun resetAnalyzed() {
        inbox.resetCardInsightAnalyzed()
        prefs.edit()
            .remove(KEY_ANALYZED_TRANSACTION_IDS)
            .remove(KEY_CANDIDATE_CATEGORIES)
            .apply()
    }

    /** 특정 후보 한 건만 다시 대기 상태로 돌려 재분석 대상에 포함시킨다. */
    fun markPending(candidate: CardExpenseInsightCandidate) {
        when (candidate.source) {
            CardExpenseInsightCandidateSource.INBOX ->
                inbox.resetCardInsightAnalyzed(listOf(candidate.sourceId))
            CardExpenseInsightCandidateSource.TRANSACTION ->
                prefs.edit()
                    .putStringSet(
                        KEY_ANALYZED_TRANSACTION_IDS,
                        analyzedTransactionIds() - candidate.sourceId
                    )
                    .apply()
        }
        clearCategory(candidate.id)
    }

    private fun clearCategory(candidateId: String) {
        val raw = prefs.getString(KEY_CANDIDATE_CATEGORIES, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (!json.has(candidateId)) return
        json.remove(candidateId)
        prefs.edit().putString(KEY_CANDIDATE_CATEGORIES, json.toString()).apply()
    }

    /** 분석 워커가 분류한 카테고리를 후보 id 기준으로 저장한다. */
    fun recordCategory(candidateId: String, category: String) {
        val json = JSONObject(prefs.getString(KEY_CANDIDATE_CATEGORIES, "{}").orEmpty())
        json.put(candidateId, category)
        prefs.edit().putString(KEY_CANDIDATE_CATEGORIES, json.toString()).apply()
    }

    private fun categoryFor(candidateId: String): String? {
        val raw = prefs.getString(KEY_CANDIDATE_CATEGORIES, null) ?: return null
        return runCatching { JSONObject(raw).optString(candidateId, "").ifBlank { null } }.getOrNull()
    }

    fun loadAnalysisItems(): List<CardExpenseInsightCandidate> {
        val inboxEntries = inbox.loadCardInsightEntries()
        val inboxItems = inboxEntries.map { it.toInsightCandidate() }
        val inboxNotificationKeys = inboxEntries.map { it.notificationKey }.toSet()
        val transactionItems = transactionStore.loadAll()
            .filter { it.notificationKey !in inboxNotificationKeys }
            .map { it.toInsightCandidate() }
        return (inboxItems + transactionItems).sortedByDescending { it.postedAt }
    }

    private fun NotificationInboxEntry.toInsightCandidate(): CardExpenseInsightCandidate {
        val combined = listOf(title, text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val amount = WalletParserRules.parseAmount(combined) ?: 0L
        val merchant = if (amount > 0) {
            WalletNotificationParser.extractMerchantName(combined, amount)
        } else {
            null
        }
        return CardExpenseInsightCandidate(
            id = "inbox:$id",
            sourceId = id,
            source = CardExpenseInsightCandidateSource.INBOX,
            title = title.ifBlank { "알림 후보" },
            body = combined,
            postedAt = postedAt,
            amount = amount,
            merchantName = merchant.orEmpty(),
            analyzedAt = cardInsightAnalyzedAt,
            category = categoryFor("inbox:$id")
        )
    }

    private fun CardTransactionRecord.toInsightCandidate(): CardExpenseInsightCandidate =
        CardExpenseInsightCandidate(
            id = "transaction:$id",
            sourceId = id,
            source = CardExpenseInsightCandidateSource.TRANSACTION,
            title = merchantName ?: rawTitle.ifBlank { "카드 거래" },
            body = buildString {
                appendLine(rawTitle)
                appendLine(rawBody)
                appendLine("가맹점: ${merchantName.orEmpty()}")
                appendLine("금액: ${amount}원")
                appendLine("상태: ${status.name}")
            }.trim(),
            postedAt = postedAt,
            amount = amount,
            merchantName = merchantName.orEmpty(),
            analyzedAt = if (id in analyzedTransactionIds()) postedAt else null,
            category = categoryFor("transaction:$id")
        )

    private fun analyzedTransactionIds(): Set<String> =
        prefs.getStringSet(KEY_ANALYZED_TRANSACTION_IDS, emptySet()).orEmpty()

    private fun markTransactionsAnalyzed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit()
            .putStringSet(KEY_ANALYZED_TRANSACTION_IDS, analyzedTransactionIds() + ids)
            .apply()
    }

    companion object {
        private const val KEY_ANALYZED_TRANSACTION_IDS = "analyzed_transaction_ids"
        private const val KEY_CANDIDATE_CATEGORIES = "candidate_categories"
    }
}
