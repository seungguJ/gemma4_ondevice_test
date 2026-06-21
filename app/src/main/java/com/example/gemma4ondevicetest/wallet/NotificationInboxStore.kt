package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class NotificationInboxEntry(
    val id: String,
    val monthKey: String,
    val packageName: String,
    val appLabel: String,
    val postedAt: Long,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val notificationKey: String,
    val subscriptionEligible: Boolean = false,
    val subscriptionAnalyzedAt: Long? = null,
    val cardInsightEligible: Boolean = false,
    val cardInsightAnalyzedAt: Long? = null
)

class NotificationInboxStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun appendRaw(raw: WalletRawNotification): NotificationInboxEntry {
        return upsertRaw(raw) { it }
    }

    fun markSubscriptionEligible(raw: WalletRawNotification) {
        upsertRaw(raw) {
            it.copy(subscriptionEligible = true)
        }
    }

    fun markCardInsightEligible(raw: WalletRawNotification) {
        upsertRaw(raw) {
            it.copy(cardInsightEligible = true)
        }
    }

    fun markAnalysisEligible(
        raw: WalletRawNotification,
        subscriptionEligible: Boolean,
        cardInsightEligible: Boolean
    ) {
        upsertRaw(raw) {
            it.copy(
                subscriptionEligible = it.subscriptionEligible || subscriptionEligible,
                cardInsightEligible = it.cardInsightEligible || cardInsightEligible
            )
        }
    }

    private fun upsertRaw(
        raw: WalletRawNotification,
        transform: (NotificationInboxEntry) -> NotificationInboxEntry
    ): NotificationInboxEntry {
        resetIfMonthChanged(raw.monthKey)
        val entries = loadEntries().toMutableList()
        val existingIndex = entries.indexOfFirst {
            it.notificationKey == raw.notificationKey && it.postedAt == raw.postedAt
        }
        val merged = if (existingIndex >= 0) {
            entries[existingIndex].copy(
                monthKey = raw.monthKey,
                packageName = raw.packageName,
                appLabel = raw.appLabel,
                postedAt = raw.postedAt,
                title = raw.title,
                text = raw.text,
                bigText = raw.bigText,
                subText = raw.subText,
                notificationKey = raw.notificationKey
            )
        } else {
            NotificationInboxEntry(
                id = UUID.randomUUID().toString(),
                monthKey = raw.monthKey,
                packageName = raw.packageName,
                appLabel = raw.appLabel,
                postedAt = raw.postedAt,
                title = raw.title,
                text = raw.text,
                bigText = raw.bigText,
                subText = raw.subText,
                notificationKey = raw.notificationKey
            )
        }
        val updated = transform(merged)
        if (existingIndex >= 0) {
            entries[existingIndex] = updated
        } else {
            entries.add(0, updated)
        }
        saveEntries(entries.take(MAX_ENTRIES))
        return updated
    }

    fun loadPendingSubscription(limit: Int): List<NotificationInboxEntry> =
        loadEntries()
            .filter { it.subscriptionEligible && it.subscriptionAnalyzedAt == null }
            .sortedBy { it.postedAt }
            .take(limit)

    fun loadSubscriptionEntries(): List<NotificationInboxEntry> =
        loadEntries()
            .filter { it.subscriptionEligible }
            .sortedByDescending { it.postedAt }

    fun loadPendingCardInsight(limit: Int? = null): List<NotificationInboxEntry> {
        val pending = loadEntries()
            .filter { it.cardInsightEligible && it.cardInsightAnalyzedAt == null }
            .sortedBy { it.postedAt }
        return if (limit != null) pending.take(limit) else pending
    }

    fun loadCardInsightEntries(): List<NotificationInboxEntry> =
        loadEntries()
            .filter { it.cardInsightEligible }
            .sortedByDescending { it.postedAt }

    fun countPendingSubscription(): Int =
        loadEntries().count { it.subscriptionEligible && it.subscriptionAnalyzedAt == null }

    fun countPendingCardInsight(): Int =
        loadEntries().count { it.cardInsightEligible && it.cardInsightAnalyzedAt == null }

    fun totalCardInsightCandidates(): Int =
        loadEntries().count { it.cardInsightEligible }

    fun markSubscriptionAnalyzed(ids: Collection<String>, analyzedAt: Long) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        val updated = loadEntries().map { entry ->
            if (entry.id in idSet) entry.copy(subscriptionAnalyzedAt = analyzedAt) else entry
        }
        saveEntries(updated)
    }

    fun markCardInsightAnalyzed(ids: Collection<String>, analyzedAt: Long) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        val updated = loadEntries().map { entry ->
            if (entry.id in idSet) entry.copy(cardInsightAnalyzedAt = analyzedAt) else entry
        }
        saveEntries(updated)
    }

    fun resetCardInsightAnalyzed() {
        val updated = loadEntries().map { entry ->
            if (entry.cardInsightEligible) entry.copy(cardInsightAnalyzedAt = null) else entry
        }
        saveEntries(updated)
    }

    fun resetCardInsightAnalyzed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        val updated = loadEntries().map { entry ->
            if (entry.id in idSet) entry.copy(cardInsightAnalyzedAt = null) else entry
        }
        saveEntries(updated)
    }

    fun resetIfMonthChanged(currentMonth: String) {
        val stored = prefs.getString(KEY_MONTH, null)
        if (stored != currentMonth) {
            prefs.edit()
                .putString(KEY_MONTH, currentMonth)
                .remove(KEY_ENTRIES)
                .apply()
        }
    }

    private fun loadEntries(): List<NotificationInboxEntry> {
        ensureCurrentMonth()
        val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                NotificationInboxEntry(
                    id = item.getString("id"),
                    monthKey = item.optString("monthKey", ""),
                    packageName = item.getString("packageName"),
                    appLabel = item.optString("appLabel", item.getString("packageName")),
                    postedAt = item.getLong("postedAt"),
                    title = item.getString("title"),
                    text = item.getString("text"),
                    bigText = item.getString("bigText"),
                    subText = item.optString("subText", ""),
                    notificationKey = item.getString("notificationKey"),
                    subscriptionEligible = item.optBoolean("subscriptionEligible", false),
                    subscriptionAnalyzedAt = item.optLongOrNull("subscriptionAnalyzedAt"),
                    cardInsightEligible = item.optBoolean("cardInsightEligible", false),
                    cardInsightAnalyzedAt = item.optLongOrNull("cardInsightAnalyzedAt")
                )
            }.getOrNull()
        }
    }

    private fun saveEntries(entries: List<NotificationInboxEntry>) {
        prefs.edit().putString(KEY_ENTRIES, entries.toJson()).apply()
    }

    private fun List<NotificationInboxEntry>.toJson(): String {
        val array = JSONArray()
        forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("monthKey", entry.monthKey)
                put("packageName", entry.packageName)
                put("appLabel", entry.appLabel)
                put("postedAt", entry.postedAt)
                put("title", entry.title)
                put("text", entry.text)
                put("bigText", entry.bigText)
                put("subText", entry.subText)
                put("notificationKey", entry.notificationKey)
                put("subscriptionEligible", entry.subscriptionEligible)
                if (entry.subscriptionAnalyzedAt != null) put("subscriptionAnalyzedAt", entry.subscriptionAnalyzedAt)
                else put("subscriptionAnalyzedAt", JSONObject.NULL)
                put("cardInsightEligible", entry.cardInsightEligible)
                if (entry.cardInsightAnalyzedAt != null) put("cardInsightAnalyzedAt", entry.cardInsightAnalyzedAt)
                else put("cardInsightAnalyzedAt", JSONObject.NULL)
            })
        }
        return array.toString()
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun ensureCurrentMonth() {
        resetIfMonthChanged(YearMonth.now(SEOUL_ZONE).toString())
    }

    companion object {
        private const val PREFS_NAME = "notification_inbox_store"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_MONTH = "month_key"
        private const val MAX_ENTRIES = 400
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
