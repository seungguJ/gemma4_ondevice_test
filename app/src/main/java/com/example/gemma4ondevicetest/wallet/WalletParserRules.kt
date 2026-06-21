package com.example.gemma4ondevicetest.wallet

object WalletParserRules {

    val ALLOWED_PACKAGES = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.wallet",
        "com.samsung.android.samsungpay"
    )

    // 금액 패턴: "₩16,500" / "12,345원" / "KRW 12,345"
    val AMOUNT_KRW = Regex("""([\d,]+)원""")
    val AMOUNT_KRW_PREFIX = Regex("""KRW\s*([\d,]+)""")
    val AMOUNT_KRW_WON_SIGN = Regex("""₩([\d,]+)""")

    // 날짜/시간 패턴
    val DATETIME_FULL = Regex("""(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})\s*(\d{1,2}):(\d{2})""")
    val DATETIME_SHORTDATE = Regex("""(\d{1,2})[/.](\d{1,2})\s+(\d{1,2}):(\d{2})""")
    val TIME_ONLY = Regex("""(\d{1,2}):(\d{2})""")

    // 할부 문구
    val INSTALLMENT = Regex("""할부\s*(\d+)개월|(\d+)개월\s*할부|일시불""")

    // 상태 키워드
    val CANCEL_KEYWORDS = listOf("취소", "승인취소", "결제취소", "국내승인취소", "해외승인취소")
    val APPROVE_KEYWORDS = listOf("승인", "결제", "사용", "국내승인", "해외승인")
    val REQUIRED_PAYMENT_KEYWORDS = listOf("결제 완료")

    // 제목에 이 단어가 있으면 삼성월렛 외 앱 알림도 카드 지출로 받는다.
    const val CARD_TITLE_KEYWORD = "카드"

    // 카드성 키워드
    val CARD_KEYWORDS = listOf(
        "카드", "신용", "체크", "삼성카드", "현대카드", "신한카드", "KB국민카드", "롯데카드",
        "우리카드", "하나카드", "BC카드", "NH농협카드", "씨티카드", "카카오카드", "토스카드",
        "IBK기업카드", "수협카드", "광주카드", "전북카드", "제주카드", "우체국카드", "새마을금고카드", "신협카드"
    )

    // 비카드 금융 제외 키워드
    val EXCLUDE_KEYWORDS = listOf(
        "계좌이체", "자동이체", "이체", "송금", "입금", "출금", "계좌", "잔액",
        "충전", "포인트", "캐시백", "이벤트", "광고", "혜택", "펀드",
        "보험", "대출", "납부", "쿠폰", "ATM", "이용한도", "한도"
    )

    // 금융성 키워드 (수집 1차 필터)
    val FINANCE_KEYWORDS = APPROVE_KEYWORDS + CANCEL_KEYWORDS

    fun parseAmount(text: String): Long? {
        AMOUNT_KRW_WON_SIGN.find(text)?.let {
            return it.groupValues[1].replace(",", "").toLongOrNull()
        }
        AMOUNT_KRW.find(text)?.let {
            return it.groupValues[1].replace(",", "").toLongOrNull()
        }
        AMOUNT_KRW_PREFIX.find(text)?.let {
            return it.groupValues[1].replace(",", "").toLongOrNull()
        }
        return null
    }

    fun parseApprovedAt(text: String): String? {
        DATETIME_FULL.find(text)?.let { mr ->
            val g = mr.groupValues
            return "${g[1]}-${g[2].padStart(2,'0')}-${g[3].padStart(2,'0')}T${g[4].padStart(2,'0')}:${g[5]}"
        }
        DATETIME_SHORTDATE.find(text)?.let { mr ->
            val g = mr.groupValues
            return "--${g[1].padStart(2,'0')}-${g[2].padStart(2,'0')}T${g[3].padStart(2,'0')}:${g[4]}"
        }
        return null
    }

    fun parseInstallment(text: String): String? =
        INSTALLMENT.find(text)?.value

    fun detectStatus(text: String): TransactionStatus {
        val hasCancelKeyword = CANCEL_KEYWORDS.any { text.contains(it) }
        val hasApproveKeyword = APPROVE_KEYWORDS.any { text.contains(it) }
        return when {
            hasCancelKeyword -> TransactionStatus.CANCELLED
            hasApproveKeyword -> TransactionStatus.APPROVED
            else -> TransactionStatus.UNKNOWN
        }
    }

    fun hasRequiredPaymentKeyword(text: String) = REQUIRED_PAYMENT_KEYWORDS.any { text.contains(it) }
    fun hasCardKeyword(text: String) = CARD_KEYWORDS.any { text.contains(it) }
    fun titleIndicatesCard(title: String) = title.contains(CARD_TITLE_KEYWORD)
    fun hasExcludeKeyword(text: String) = EXCLUDE_KEYWORDS.any { text.contains(it) }
    fun hasFinanceKeyword(text: String) = FINANCE_KEYWORDS.any { text.contains(it) }
}
