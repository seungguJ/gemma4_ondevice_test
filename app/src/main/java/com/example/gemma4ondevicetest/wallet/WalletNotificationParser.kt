package com.example.gemma4ondevicetest.wallet

object WalletNotificationParser {

    fun parse(raw: WalletRawNotification): ParseResult {
        val combined = listOf(raw.title, raw.bigText, raw.text, raw.subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (WalletParserRules.hasExcludeKeyword(combined)) {
            return fail(raw, "비카드 금융 키워드 포함")
        }

        if (!WalletParserRules.hasRequiredPaymentKeyword(combined)) {
            return fail(raw, "`결제 완료` 문구 없음")
        }

        val status = WalletParserRules.detectStatus(combined)
        if (status == TransactionStatus.UNKNOWN) {
            return fail(raw, "승인/취소 상태 키워드 없음")
        }

        val amount = WalletParserRules.parseAmount(combined)
            ?: return fail(raw, "금액 추출 실패")

        val approvedAt = WalletParserRules.parseApprovedAt(combined)
        val installmentText = WalletParserRules.parseInstallment(combined)
        val merchantName = extractMerchantName(combined, amount)

        val dedupeKey = buildDedupeKey(status, amount, approvedAt, raw.postedAt, merchantName)

        return ParseResult.Success(
            ParsedCardTransaction(
                sourcePackage = raw.packageName,
                notificationKey = raw.notificationKey,
                monthKey = raw.monthKey,
                approvedAt = approvedAt,
                cardLabel = null,
                merchantName = merchantName,
                amount = amount,
                currency = "KRW",
                installmentText = installmentText,
                status = status,
                rawTitle = raw.title,
                rawBody = raw.text.ifBlank { raw.bigText },
                dedupeKey = dedupeKey
            )
        )
    }

    fun extractMerchantName(text: String, amount: Long): String? {
        // 금액+원 패턴 이후 / 파이프 구분 / 날짜 패턴 이전에 위치한 텍스트를 가맹점으로 추정
        val amountFormatted = amount.toAmountString()
        val afterAmount = when {
            text.contains("${amountFormatted}원") -> text.substringAfter("${amountFormatted}원", "").trim()
            text.contains("₩${amountFormatted}") -> text.substringAfter("₩${amountFormatted}", "").trim()
            else -> return null
        }
        if (afterAmount.isBlank()) return null

        // 파이프(|), 날짜 패턴, 시간 패턴 앞까지 자름
        val candidate = afterAmount
            .replace(Regex("""\(.*?\)"""), "")  // 괄호 내용 제거 (할부 등)
            .split("|", "│")
            .firstOrNull()
            ?.trim()
            ?: return null

        // 날짜/시간 패턴 이전까지
        val cleaned = candidate
            .replace(WalletParserRules.DATETIME_FULL, "")
            .replace(WalletParserRules.DATETIME_SHORTDATE, "")
            .replace(WalletParserRules.TIME_ONLY, "")
            .replace(WalletParserRules.INSTALLMENT, "")
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun buildDedupeKey(
        status: TransactionStatus,
        amount: Long,
        approvedAt: String?,
        postedAt: Long,
        merchantName: String?
    ): String {
        val timeKey = approvedAt ?: postedAt.toString()
        return "${status}|${amount}|${timeKey}|${merchantName ?: ""}"
    }

    private fun fail(raw: WalletRawNotification, reason: String): ParseResult.Failure =
        ParseResult.Failure(
            ParseFailure(
                notificationKey = raw.notificationKey,
                rawTitle = raw.title,
                rawBody = raw.text.ifBlank { raw.bigText },
                reason = reason
            )
        )
}

private fun Long.toAmountString(): String {
    if (this <= 0L) return this.toString()
    var n = this
    val sb = StringBuilder()
    var count = 0
    while (n > 0) {
        if (count > 0 && count % 3 == 0) sb.insert(0, ',')
        sb.insert(0, (n % 10).toString())
        n /= 10
        count++
    }
    return sb.toString()
}
