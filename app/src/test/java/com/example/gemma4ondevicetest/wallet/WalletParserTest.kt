package com.example.gemma4ondevicetest.wallet

import org.junit.Assert.*
import org.junit.Test

class WalletParserTest {

    // ──────────────────────────────────────────────
    // 더미 데이터: 실제 삼성 Wallet 알림 형식을 모사
    // ──────────────────────────────────────────────

    private val PACKAGE = "com.samsung.android.wallet"
    private val MONTH = "2026-06"

    private fun raw(
        title: String,
        text: String = "",
        bigText: String = "",
        subText: String = "",
        key: String = "key-${System.nanoTime()}"
    ) = WalletRawNotification(
        monthKey = MONTH,
        packageName = PACKAGE,
        appLabel = "Samsung Wallet",
        postedAt = System.currentTimeMillis(),
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        notificationKey = key
    )

    // ──────────────────────────────────────────────
    // 1. 승인 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `삼성카드 국내승인 - 기본 포맷`() {
        val n = raw(
            title = "[삼성카드] 국내승인",
            bigText = "12,500원 스타벅스강남점 06/03 14:21"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(12500L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
        assertEquals("삼성카드", result.transaction.cardLabel)
        assertNotNull(result.transaction.approvedAt)
    }

    @Test
    fun `현대카드 결제 - 파이프 구분 포맷`() {
        val n = raw(
            title = "현대카드",
            text = "25,000원 결제 | 이마트 노원점 | 2026/06/03 09:15"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(25000L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
        assertEquals("현대카드", result.transaction.cardLabel)
        assertEquals("2026-06-03T09:15", result.transaction.approvedAt)
    }

    @Test
    fun `체크카드 사용 - 시간만 있는 포맷`() {
        val n = raw(
            title = "체크카드 사용",
            text = "8,900원 CU편의점 18:30"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(8900L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
        assertEquals("체크카드", result.transaction.cardLabel)
    }

    @Test
    fun `신한카드 온라인 결제 - 점 구분 날짜`() {
        val n = raw(
            title = "신한카드 승인",
            bigText = "150,000원 쿠팡 2026.06.03 15:45"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(150000L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
        assertEquals("2026-06-03T15:45", result.transaction.approvedAt)
    }

    @Test
    fun `KB국민카드 할부 - 괄호 할부 포맷`() {
        val n = raw(
            title = "KB국민카드 승인",
            bigText = "120,000원(할부3개월) 올리브영 2026/06/03 16:20"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(120000L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
        assertNotNull(result.transaction.installmentText)
        assertTrue(result.transaction.installmentText!!.contains("3"))
    }

    @Test
    fun `롯데카드 고액 결제 - 백만원대`() {
        val n = raw(
            title = "[롯데카드] 국내승인",
            text = "1,250,000원 | 삼성전자 강남직영점 | 2026/06/03 11:00"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(1250000L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
    }

    @Test
    fun `title만 있는 단순 승인 포맷`() {
        val n = raw(
            title = "삼성카드 34,000원 승인"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(34000L, result.transaction.amount)
        assertEquals(TransactionStatus.APPROVED, result.transaction.status)
    }

    // ──────────────────────────────────────────────
    // 2. 취소 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `신용카드 승인취소`() {
        val n = raw(
            title = "[현대카드] 국내승인취소",
            bigText = "34,000원 롯데마트 송파점 2026/06/03 11:00"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(34000L, result.transaction.amount)
        assertEquals(TransactionStatus.CANCELLED, result.transaction.status)
    }

    @Test
    fun `결제취소 키워드 포함`() {
        val n = raw(
            title = "삼성카드",
            text = "결제취소 | 15,000원 | 올리브영 | 06/03 13:20"
        )
        val result = WalletNotificationParser.parse(n) as ParseResult.Success
        assertEquals(TransactionStatus.CANCELLED, result.transaction.status)
        assertEquals(15000L, result.transaction.amount)
    }

    // ──────────────────────────────────────────────
    // 3. 필터 제외 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `계좌이체 알림 - 제외`() {
        val n = raw(
            title = "계좌이체 완료",
            text = "50,000원 이체 완료"
        )
        val result = WalletNotificationParser.parse(n)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `출금 알림 - 제외`() {
        val n = raw(title = "출금", text = "12,000원 출금 완료")
        assertTrue(WalletNotificationParser.parse(n) is ParseResult.Failure)
    }

    @Test
    fun `포인트 적립 알림 - 제외`() {
        val n = raw(
            title = "포인트 적립",
            text = "2,000 포인트 적립되었습니다"
        )
        assertTrue(WalletNotificationParser.parse(n) is ParseResult.Failure)
    }

    @Test
    fun `자동이체 알림 - 제외`() {
        val n = raw(title = "자동이체 완료", text = "45,000원 자동이체")
        assertTrue(WalletNotificationParser.parse(n) is ParseResult.Failure)
    }

    @Test
    fun `광고성 알림 - 제외`() {
        val n = raw(title = "삼성카드 이벤트", text = "혜택 받으세요 10,000원 할인")
        assertTrue(WalletNotificationParser.parse(n) is ParseResult.Failure)
    }

    @Test
    fun `금액 없는 알림 - 파싱 실패`() {
        val n = raw(title = "삼성카드 승인", text = "결제가 완료되었습니다")
        assertTrue(WalletNotificationParser.parse(n) is ParseResult.Failure)
    }

    // ──────────────────────────────────────────────
    // 4. WalletNotificationFilter 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `허용 패키지 + 승인 키워드 - filter 통과`() {
        val n = raw(title = "삼성카드 승인", text = "12,500원 승인")
        assertTrue(WalletNotificationFilter.shouldProcess(n))
    }

    @Test
    fun `비허용 패키지 - filter 차단`() {
        val n = raw(title = "카드 승인", text = "12,500원 결제").copy(
            packageName = "com.kakaobank.channel"
        )
        assertFalse(WalletNotificationFilter.shouldProcess(n))
    }

    @Test
    fun `포인트 키워드 있을 때 - filter 차단`() {
        val n = raw(title = "포인트 결제 승인", text = "2,000원 포인트 사용")
        assertFalse(WalletNotificationFilter.shouldProcess(n))
    }

    @Test
    fun `금융성 키워드 없을 때 - filter 차단`() {
        val n = raw(title = "삼성월렛 알림", text = "새로운 기능이 추가되었습니다")
        assertFalse(WalletNotificationFilter.shouldProcess(n))
    }

    // ──────────────────────────────────────────────
    // 5. WalletParserRules 단위 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `금액 파싱 - 쉼표 포함`() {
        assertEquals(12345L, WalletParserRules.parseAmount("12,345원 승인"))
        assertEquals(1250000L, WalletParserRules.parseAmount("1,250,000원"))
        assertEquals(900L, WalletParserRules.parseAmount("900원"))
    }

    @Test
    fun `금액 파싱 - KRW 프리픽스`() {
        assertEquals(50000L, WalletParserRules.parseAmount("KRW 50,000 결제"))
    }

    @Test
    fun `날짜 파싱 - yyyy-MM-dd HH-mm`() {
        val at = WalletParserRules.parseApprovedAt("결제 2026/06/03 14:21 완료")
        assertEquals("2026-06-03T14:21", at)
    }

    @Test
    fun `날짜 파싱 - 단축 MM-dd HH-mm`() {
        val at = WalletParserRules.parseApprovedAt("06/03 18:30 승인")
        assertEquals("--06-03T18:30", at)
    }

    @Test
    fun `상태 감지 - 취소 우선`() {
        assertEquals(TransactionStatus.CANCELLED, WalletParserRules.detectStatus("승인취소 12,500원"))
        assertEquals(TransactionStatus.CANCELLED, WalletParserRules.detectStatus("국내승인취소"))
    }

    @Test
    fun `상태 감지 - 승인`() {
        assertEquals(TransactionStatus.APPROVED, WalletParserRules.detectStatus("국내승인 완료"))
        assertEquals(TransactionStatus.APPROVED, WalletParserRules.detectStatus("카드 사용 완료"))
    }

    @Test
    fun `상태 감지 - 불명확`() {
        assertEquals(TransactionStatus.UNKNOWN, WalletParserRules.detectStatus("잔액 조회"))
    }

    // ──────────────────────────────────────────────
    // 6. 중복 제거 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `동일 notificationKey는 중복`() {
        val parsed = makeParsed(key = "same-key", amount = 10000L)
        val existing = listOf(makeRecord(notifKey = "same-key", dedupeKey = "other"))
        assertTrue(CardExpenseDeduplicator.isDuplicate(parsed, existing))
    }

    @Test
    fun `동일 dedupeKey는 중복`() {
        val parsed = makeParsed(key = "key-1", amount = 10000L, dedupeKey = "APPROVED|10000|T|가맹점|삼성카드")
        val existing = listOf(makeRecord(notifKey = "key-2", dedupeKey = "APPROVED|10000|T|가맹점|삼성카드"))
        assertTrue(CardExpenseDeduplicator.isDuplicate(parsed, existing))
    }

    @Test
    fun `다른 key + 다른 dedupeKey는 중복 아님`() {
        val parsed = makeParsed(key = "key-1", amount = 10000L, dedupeKey = "APPROVED|10000|T|A|삼성카드")
        val existing = listOf(makeRecord(notifKey = "key-2", dedupeKey = "APPROVED|20000|T|B|현대카드"))
        assertFalse(CardExpenseDeduplicator.isDuplicate(parsed, existing))
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private fun makeParsed(
        key: String,
        amount: Long,
        dedupeKey: String = "APPROVED|${amount}|T|가맹점|삼성카드"
    ) = ParsedCardTransaction(
        sourcePackage = PACKAGE,
        notificationKey = key,
        monthKey = MONTH,
        approvedAt = "2026-06-03T14:00",
        cardLabel = "삼성카드",
        merchantName = "가맹점",
        amount = amount,
        currency = "KRW",
        installmentText = null,
        status = TransactionStatus.APPROVED,
        rawTitle = "삼성카드 승인",
        rawBody = "${amount}원 가맹점",
        dedupeKey = dedupeKey
    )

    private fun makeRecord(notifKey: String, dedupeKey: String) = CardTransactionRecord(
        id = "id-$notifKey",
        monthKey = MONTH,
        sourcePackage = PACKAGE,
        notificationKey = notifKey,
        approvedAt = "2026-06-03T14:00",
        postedAt = System.currentTimeMillis(),
        cardLabel = "삼성카드",
        merchantName = "가맹점",
        amount = 10000L,
        currency = "KRW",
        status = TransactionStatus.APPROVED,
        rawTitle = "삼성카드 승인",
        rawBody = "10,000원 가맹점",
        createdAt = System.currentTimeMillis(),
        dedupeKey = dedupeKey
    )
}
