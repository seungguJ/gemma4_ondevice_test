package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class CardTransactionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallet_card_transactions", Context.MODE_PRIVATE)

    fun save(record: CardTransactionRecord) {
        val all = loadAll().toMutableList()
        all.removeAll { it.id == record.id }
        all.add(record)
        prefs.edit().putString(KEY_RECORDS, all.toJson()).apply()
    }

    fun loadAll(): List<CardTransactionRecord> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return parseRecords(json)
    }

    fun findById(id: String): CardTransactionRecord? =
        loadAll().firstOrNull { it.id == id }

    fun loadByMonth(monthKey: String): List<CardTransactionRecord> =
        loadAll().filter { it.monthKey == monthKey }

    fun deleteByMonth(monthKey: String) {
        val remaining = loadAll().filter { it.monthKey != monthKey }
        prefs.edit().putString(KEY_RECORDS, remaining.toJson()).apply()
    }

    fun deleteAll() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    fun storedMonthKey(): String? = prefs.getString(KEY_STORED_MONTH, null)

    fun updateStoredMonthKey(monthKey: String) {
        prefs.edit().putString(KEY_STORED_MONTH, monthKey).apply()
    }

    private fun List<CardTransactionRecord>.toJson(): String {
        val arr = JSONArray()
        forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun CardTransactionRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("monthKey", monthKey)
        put("sourcePackage", sourcePackage)
        put("notificationKey", notificationKey)
        put("approvedAt", approvedAt ?: JSONObject.NULL)
        put("postedAt", postedAt)
        put("cardLabel", cardLabel ?: JSONObject.NULL)
        put("merchantName", merchantName ?: JSONObject.NULL)
        put("amount", amount)
        put("currency", currency)
        put("status", status.name)
        put("rawTitle", rawTitle)
        put("rawBody", rawBody)
        put("createdAt", createdAt)
        put("dedupeKey", dedupeKey)
    }

    private fun parseRecords(json: String): List<CardTransactionRecord> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                CardTransactionRecord(
                    id = o.getString("id"),
                    monthKey = o.getString("monthKey"),
                    sourcePackage = o.getString("sourcePackage"),
                    notificationKey = o.getString("notificationKey"),
                    approvedAt = o.optString("approvedAt").takeIf { it.isNotEmpty() && it != "null" },
                    postedAt = o.getLong("postedAt"),
                    cardLabel = o.optString("cardLabel").takeIf { it.isNotEmpty() && it != "null" },
                    merchantName = o.optString("merchantName").takeIf { it.isNotEmpty() && it != "null" },
                    amount = o.getLong("amount"),
                    currency = o.getString("currency"),
                    status = TransactionStatus.valueOf(o.getString("status")),
                    rawTitle = o.getString("rawTitle"),
                    rawBody = o.getString("rawBody"),
                    createdAt = o.getLong("createdAt"),
                    dedupeKey = o.getString("dedupeKey")
                )
            } catch (_: Exception) { null }
        }
    }

    companion object {
        private const val KEY_RECORDS = "records"
        private const val KEY_STORED_MONTH = "stored_month"
    }
}
