package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WalletNotificationLogEntry(
    val id: String,
    val receivedAt: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val notificationKey: String,
    val outcome: String,       // received | filtered | parse_failed | saved | duplicate
    val outcomeDetail: String
)

class WalletNotificationLogStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallet_notif_log", Context.MODE_PRIVATE)

    fun append(entry: WalletNotificationLogEntry) {
        val all = loadAll().toMutableList()
        all.add(0, entry)
        prefs.edit().putString(KEY, all.take(MAX_ENTRIES).toJson()).apply()
    }

    fun updateOutcome(notifKey: String, outcome: String, outcomeDetail: String) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.notificationKey == notifKey }
        if (idx >= 0) {
            all[idx] = all[idx].copy(outcome = outcome, outcomeDetail = outcomeDetail)
            prefs.edit().putString(KEY, all.toJson()).apply()
        }
    }

    fun loadAll(): List<WalletNotificationLogEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return parseEntries(json)
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun List<WalletNotificationLogEntry>.toJson(): String {
        val arr = JSONArray()
        forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun WalletNotificationLogEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("receivedAt", receivedAt)
        put("packageName", packageName)
        put("title", title)
        put("text", text)
        put("bigText", bigText)
        put("subText", subText)
        put("notificationKey", notificationKey)
        put("outcome", outcome)
        put("outcomeDetail", outcomeDetail)
    }

    private fun parseEntries(json: String): List<WalletNotificationLogEntry> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                WalletNotificationLogEntry(
                    id = o.getString("id"),
                    receivedAt = o.getLong("receivedAt"),
                    packageName = o.getString("packageName"),
                    title = o.getString("title"),
                    text = o.getString("text"),
                    bigText = o.getString("bigText"),
                    subText = o.optString("subText", ""),
                    notificationKey = o.getString("notificationKey"),
                    outcome = o.getString("outcome"),
                    outcomeDetail = o.optString("outcomeDetail", "")
                )
            } catch (_: Exception) { null }
        }
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 100
    }
}
