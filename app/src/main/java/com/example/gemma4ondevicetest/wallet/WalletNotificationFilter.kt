package com.example.gemma4ondevicetest.wallet

object WalletNotificationFilter {

    fun shouldProcess(raw: WalletRawNotification): Boolean {
        if (raw.packageName !in WalletParserRules.ALLOWED_PACKAGES) return false

        val combined = listOf(raw.title, raw.text, raw.bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (combined.isBlank()) return false

        if (WalletParserRules.hasExcludeKeyword(combined)) return false
        if (!WalletParserRules.hasFinanceKeyword(combined)) return false

        return true
    }
}
