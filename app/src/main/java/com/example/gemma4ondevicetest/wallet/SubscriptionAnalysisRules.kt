package com.example.gemma4ondevicetest.wallet

import java.util.Locale

object SubscriptionAnalysisRules {

    const val KAKAO_PACKAGE = "com.kakao.talk"
    private const val PAYMENT_KEYWORD = "결제"
    private const val EXCLUDE_KEYWORD = "광고"

    val ALLOWED_PACKAGES = setOf(
        "com.shcard.smartpay",
        "com.shinhan.sbanking",
        "com.kbcard.cxh.appcard",
        "com.kbstar.kbbank",
        "com.samsunglife.monimo",
        "com.btckorea.bithumb",
        "kr.co.kfcc.mobilebank",
        KAKAO_PACKAGE
    )

    private val FINANCE_NAME_KEYWORDS = listOf(
        "삼성", "삼성카드", "monimo", "모니모",
        "KB", "국민", "국민카드",
        "신한", "신한카드",
        "빗썸",
        "새마을금고", "MG"
    )

    fun supportsPackage(packageName: String): Boolean = packageName in ALLOWED_PACKAGES

    fun evaluate(raw: WalletRawNotification): SubscriptionFilterDecision {
        if (!supportsPackage(raw.packageName)) {
            return SubscriptionFilterDecision(false, "지원 패키지 아님")
        }

        val combined = listOf(raw.title, raw.text, raw.bigText, raw.subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (combined.isBlank()) {
            return SubscriptionFilterDecision(false, "본문 없음")
        }

        if (combined.contains(EXCLUDE_KEYWORD)) {
            return SubscriptionFilterDecision(false, "광고 포함")
        }

        if (!combined.contains(PAYMENT_KEYWORD)) {
            return SubscriptionFilterDecision(false, "결제 키워드 없음")
        }

        if (raw.packageName == KAKAO_PACKAGE) {
            val lowerCombined = combined.lowercase(Locale.ROOT)
            val hasFinanceName = FINANCE_NAME_KEYWORDS.any { keyword ->
                val normalizedKeyword = keyword.lowercase(Locale.ROOT)
                combined.contains(keyword) || lowerCombined.contains(normalizedKeyword)
            }
            if (!hasFinanceName) {
                return SubscriptionFilterDecision(false, "카카오톡 금융사명 없음")
            }
        }

        return SubscriptionFilterDecision(true, "수집 대상")
    }
}
