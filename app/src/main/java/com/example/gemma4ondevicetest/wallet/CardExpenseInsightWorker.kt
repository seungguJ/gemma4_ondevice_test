package com.example.gemma4ondevicetest.wallet

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gemma4ondevicetest.LlmEngine
import com.example.gemma4ondevicetest.ModelRuntimeGate
import com.example.gemma4ondevicetest.ModelRuntimeResult
import com.example.gemma4ondevicetest.ModelStore
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId

class CardExpenseInsightWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val candidateStore = CardExpenseCandidateStore(appContext)
    private val insightStore = CardExpenseInsightStore(appContext)

    override suspend fun doWork(): Result {
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        val includeLedgerTransactions = inputData.getBoolean(KEY_INCLUDE_LEDGER_TRANSACTIONS, false)
        insightStore.ensureCurrentMonth(currentMonthKey())
        val currentReport = insightStore.loadReport()
        val pending = candidateStore.loadPending(includeLedgerTransactions = includeLedgerTransactions)
        if (pending.isEmpty()) {
            insightStore.updateStatus("분석할 카드 내역 후보가 없습니다.", 0)
            return Result.success()
        }
        insightStore.updateStatus(
            statusMessage = "분석 입력 ${pending.size}건을 준비했습니다. 실행 조건을 확인 중입니다.",
            pendingCount = pending.size
        )

        val battery = SubscriptionAnalysisScheduler.currentBatteryGateStatus(applicationContext)
        if (!forceRun && !battery.isEligible) {
            insightStore.updateStatus(
                statusMessage = "충전 중이며 배터리 100%일 때만 카드 인사이트를 분석합니다. 현재 ${battery.levelPercent}%.",
                pendingCount = pending.size
            )
            return Result.retry()
        }

        val remainingCooldown =
            SubscriptionAnalysisScheduler.remainingCooldownMillis(currentReport.lastCompletedAt)
        if (!forceRun && remainingCooldown > 0L) {
            insightStore.updateStatus(
                statusMessage = "최근 카드 인사이트 분석이 완료되어 ${SubscriptionAnalysisScheduler.formatRemainingCooldown(remainingCooldown)} 뒤에 다시 분석합니다.",
                pendingCount = pending.size
            )
            return Result.retry()
        }

        insightStore.updateStatus(
            statusMessage = "모델 파일과 런타임 상태를 확인 중입니다.",
            pendingCount = pending.size
        )
        if (ModelRuntimeGate.isLoaded) {
            insightStore.updateStatus(
                statusMessage = "다른 화면이 모델을 사용 중이라 카드 인사이트 분석을 잠시 보류했습니다.",
                pendingCount = pending.size
            )
            return Result.retry()
        }

        val source = ModelStore.getSelectedModel(applicationContext)
        if (!ModelStore.hasModel(applicationContext, source)) {
            insightStore.updateStatus(
                statusMessage = "모델이 준비되지 않아 카드 인사이트 분석을 실행할 수 없습니다.",
                pendingCount = pending.size
            )
            return Result.success()
        }

        val batch = pending.take(SINGLE_ITEM_BATCH_SIZE)
        val prompt = buildPrompt(batch, compact = false)
        insightStore.updateStatus(
            statusMessage = "모델을 로드하고 카드 인사이트 ${batch.size}건을 분석 중입니다.",
            pendingCount = pending.size
        )

        val runtimeResult = ModelRuntimeGate.runBackgroundExclusive(
            context = applicationContext,
            sessionId = SESSION_ID,
            source = source,
            block = {
                insightStore.updateStatus(
                    statusMessage = "AI가 카드 인사이트 ${batch.size}건을 분류 중입니다.",
                    pendingCount = pending.size
                )
                var generated = LlmEngine.generateForSession(
                    sessionId = SESSION_ID,
                    prompt = prompt,
                    config = LlmEngine.LlmConfig(
                        maxTokens = CATEGORY_MAX_TOKENS,
                        contextSize = 4096,
                        systemInstruction = SYSTEM_INSTRUCTION
                    )
                )
                if (generated.isBlank()) {
                    Log.w(TAG, "Card insight AI response was blank. Retrying with compact prompt.")
                    ModelRuntimeGate.clearSession(SESSION_ID)
                    insightStore.updateStatus(
                        statusMessage = "AI 응답이 없어 입력을 줄여 다시 분석 중입니다.",
                        pendingCount = pending.size
                    )
                    generated = LlmEngine.generateForSession(
                        sessionId = SESSION_ID,
                        prompt = buildPrompt(batch, compact = true),
                        config = LlmEngine.LlmConfig(
                            maxTokens = CATEGORY_MAX_TOKENS,
                            contextSize = 3072,
                            systemInstruction = SYSTEM_INSTRUCTION
                        )
                    )
                }
                generated
            }
        )
        val response = when (runtimeResult) {
            ModelRuntimeResult.Busy -> {
                insightStore.updateStatus(
                    statusMessage = "다른 화면이 모델을 사용 중이라 카드 인사이트 분석을 잠시 보류했습니다.",
                    pendingCount = pending.size
                )
                return Result.retry()
            }
            ModelRuntimeResult.MissingModel -> {
                insightStore.updateStatus(
                    statusMessage = "모델이 준비되지 않아 카드 인사이트 분석을 실행할 수 없습니다.",
                    pendingCount = pending.size
                )
                return Result.success()
            }
            is ModelRuntimeResult.LoadFailed -> {
                insightStore.updateStatus(
                    statusMessage = runtimeResult.message,
                    pendingCount = pending.size
                )
                return if (forceRun) Result.success() else Result.retry()
            }
            is ModelRuntimeResult.Success -> runtimeResult.value
        }

        if (response.isBlank()) {
            Log.w(TAG, "Card insight AI response was blank after retry.")
            insightStore.updateStatus(
                statusMessage = "AI 응답을 받지 못했습니다. 입력을 줄여 재시도했지만 모델이 결과를 생성하지 않았습니다.",
                pendingCount = pending.size
            )
            return if (forceRun) Result.success() else Result.retry()
        }

        // 모델이 반복 폭주 등으로 해석 불가능한 응답을 내면, 같은 후보를 무한히 재분석하지 않도록
        // 기타로 분류해 진행한다. (해당 후보가 큐를 영구히 막는 문제 방지)
        val parsedReport = parseResponse(response, batch) ?: run {
            Log.w(TAG, "Card insight parse failed; assigning batch to 기타: ${response.take(LOG_RESPONSE_LIMIT)}")
            insightStore.updateStatus(
                statusMessage = "AI가 분류하지 못한 내역은 기타로 처리하고 다음 내역을 이어서 분석합니다.",
                pendingCount = pending.size
            )
            fallbackReport(batch)
        }

        val now = System.currentTimeMillis()
        insightStore.updateStatus(
            statusMessage = "분석 결과를 저장 중입니다.",
            pendingCount = pending.size
        )
        candidateStore.markAnalyzed(batch, now)
        // 배치는 1건씩 처리되므로 분류된 카테고리를 해당 후보에 기록해 둔다.
        val assignedCategory = parsedReport.categoryBreakdowns.firstOrNull()?.category ?: CATEGORY_OTHER
        batch.forEach { candidateStore.recordCategory(it.id, assignedCategory) }
        // 리포트를 누적하지 않고, 분석된 모든 후보 + 기록된 카테고리로 매번 새로 만든다.
        // 단일 소스에서 파생하므로 재분석/부분 재분석 시에도 이중 집계가 생기지 않는다.
        val rebuilt = buildReportFromAnalyzed(candidateStore, parsedReport.statusMessage)
        insightStore.saveReport(
            rebuilt.copy(
                generatedAt = now,
                lastCompletedAt = now,
                pendingCount = candidateStore.pendingCount()
            )
        )
        if (forceRun && candidateStore.loadPending(includeLedgerTransactions = includeLedgerTransactions).isNotEmpty()) {
            insightStore.updateStatus(
                statusMessage = "카드 인사이트 1건을 분석했습니다. 남은 내역을 이어서 분석합니다.",
                pendingCount = candidateStore.loadPending(includeLedgerTransactions = includeLedgerTransactions).size
            )
            CardExpenseInsightScheduler.enqueue(
                context = applicationContext,
                forceRun = true,
                includeLedgerTransactions = includeLedgerTransactions,
                replaceExisting = false
            )
        }
        return Result.success()
    }

    // 작은 모델용으로 핵심만 담은 짧은 프롬프트. 금액·가맹점명은 후보 데이터로 채우므로
    // 모델은 카테고리 하나만 고르면 된다.
    private fun buildPrompt(batch: List<CardExpenseInsightCandidate>, compact: Boolean): String {
        val candidate = batch.first()
        val merchant = candidate.merchantName.ifBlank { "(미상)" }
        val rawText = candidate.combinedText.take(if (compact) COMPACT_RAW_TEXT_LIMIT else RAW_TEXT_LIMIT)
        return buildString {
            appendLine("카드 결제 한 건의 카테고리를 고르세요.")
            appendLine("가맹점: $merchant")
            appendLine("내용: $rawText")
            appendLine("카테고리: $CATEGORY_HINT")
            appendLine("카드사명(삼성카드 등)은 가맹점이 아닙니다. 맞는 게 없으면 기타.")
            append("출력은 {\"category\":\"카테고리명\"} JSON 한 줄만.")
        }
    }

    // 분석 완료된 모든 후보와 그들에 기록된 카테고리로 현재 달 리포트를 통째로 만든다.
    private fun buildReportFromAnalyzed(
        candidateStore: CardExpenseCandidateStore,
        statusMessage: String
    ): CardExpenseInsightReport {
        data class Accumulator(var totalAmount: Long, var count: Int, val names: MutableList<String>)

        val analyzed = candidateStore.loadAnalysisItems().filter { it.isAnalyzed }
        val grouped = linkedMapOf<String, Accumulator>()
        analyzed.forEach { item ->
            val category = item.category?.ifBlank { null } ?: CATEGORY_OTHER
            val acc = grouped.getOrPut(category) { Accumulator(0L, 0, mutableListOf()) }
            acc.totalAmount += item.amount
            acc.count += 1
            item.merchantName.ifBlank { null }?.let { acc.names.add(sanitizeMerchantName(it)) }
        }

        val totalAmount = grouped.values.sumOf { it.totalAmount }
        val breakdowns = grouped.map { (category, acc) ->
            ExpenseCategoryBreakdown(
                category = category,
                totalAmount = acc.totalAmount,
                count = acc.count,
                representativeNames = acc.names.distinct().take(MAX_REPRESENTATIVE_NAMES),
                percentageOfTotal = if (totalAmount > 0) acc.totalAmount.toFloat() / totalAmount * 100f else 0f
            )
        }.sortedByDescending { it.totalAmount }

        val topMerchants = breakdowns.flatMap { it.representativeNames }.distinct().take(MAX_TOP_MERCHANTS)

        return CardExpenseInsightReport(
            monthKey = currentMonthKey(),
            generatedAt = 0L,
            lastCompletedAt = 0L,
            pendingCount = 0,
            analyzedCandidateCount = analyzed.size,
            statusMessage = statusMessage,
            categoryBreakdowns = breakdowns,
            topMerchants = topMerchants
        )
    }

    // 모델 응답에서는 카테고리명 하나만 읽는다. 금액·가맹점명·건수는 후보 데이터로 채운다.
    private fun parseResponse(
        rawResponse: String,
        batch: List<CardExpenseInsightCandidate>
    ): CardExpenseInsightReport? {
        val jsonText = extractJsonObject(rawResponse)
        if (jsonText == null) {
            Log.w(TAG, "No JSON object in AI response: ${rawResponse.take(LOG_RESPONSE_LIMIT)}")
            return null
        }
        val root = runCatching { JSONObject(jsonText) }.getOrNull()
        if (root == null) {
            Log.w(TAG, "Invalid JSON in AI response: ${rawResponse.take(LOG_RESPONSE_LIMIT)}")
            return null
        }
        // 신 스키마({"category":...}) 우선, 구 스키마({"categories":[{"category":...}]})도 호환.
        val rawCategory = root.optString("category", "").ifBlank {
            root.optJSONArray("categories")?.optJSONObject(0)?.optString("category", "").orEmpty()
        }
        if (rawCategory.isBlank()) {
            Log.w(TAG, "AI response has no category: ${rawResponse.take(LOG_RESPONSE_LIMIT)}")
            return null
        }
        return singleCategoryReport(batch, normalizeCategory(rawCategory), "카드 인사이트 분석 완료")
    }

    private fun fallbackReport(batch: List<CardExpenseInsightCandidate>): CardExpenseInsightReport =
        singleCategoryReport(batch, CATEGORY_OTHER, "자동 분류 실패분을 기타로 처리했습니다.")

    // 한 배치(현재 1건)를 단일 카테고리로 묶어, 금액·대표 가맹점명을 후보 데이터에서 채운다.
    private fun singleCategoryReport(
        batch: List<CardExpenseInsightCandidate>,
        category: String,
        statusMessage: String
    ): CardExpenseInsightReport {
        val totalAmount = batch.sumOf { it.amount }
        val names = batch.mapNotNull { it.merchantName.ifBlank { null } }
            .map { sanitizeMerchantName(it) }
            .distinct()
            .take(MAX_REPRESENTATIVE_NAMES)
        return CardExpenseInsightReport(
            monthKey = currentMonthKey(),
            generatedAt = 0L,
            lastCompletedAt = 0L,
            pendingCount = 0,
            analyzedCandidateCount = batch.size,
            statusMessage = statusMessage,
            categoryBreakdowns = listOf(
                ExpenseCategoryBreakdown(
                    category = category,
                    totalAmount = totalAmount,
                    count = batch.size,
                    representativeNames = names,
                    percentageOfTotal = 100f
                )
            ),
            topMerchants = names
        )
    }

    // 엔진(LiteRT-LM)이 grammar/JSON 스키마 강제 디코딩을 지원하지 않으므로,
    // 모델이 허용 목록 밖의 라벨을 반환해도 앱 단에서 허용 카테고리로 강제 매핑한다.
    private fun normalizeCategory(category: String): String {
        val trimmed = category.trim()
        if (ALLOWED_CATEGORIES.contains(trimmed)) return trimmed
        if (trimmed.isEmpty()) return CATEGORY_OTHER
        val lowered = trimmed.lowercase()
        val matched = CATEGORY_DEFINITIONS.firstOrNull { definition ->
            lowered.contains(definition.name.lowercase()) ||
                definition.keywords.any { keyword -> lowered.contains(keyword.lowercase()) }
        }
        return matched?.name ?: CATEGORY_OTHER
    }

    private fun extractJsonObject(text: String): String? {
        // 모델이 ```json ... ``` 코드펜스로 감싸는 경우가 많아 먼저 제거한다.
        val cleaned = text.replace("```json", " ").replace("```", " ")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    // 소형 모델이 같은 음절을 무한 반복(예: "이동의즐의즐...")하는 폭주를 방지하기 위해
    // 가맹점명 길이를 제한한다.
    private fun sanitizeMerchantName(raw: String): String {
        val name = raw.trim()
        if (name.length <= MAX_MERCHANT_NAME_LENGTH) return name
        return name.take(MAX_MERCHANT_NAME_LENGTH).trimEnd() + "…"
    }

    companion object {
        private const val TAG = "CardExpenseInsightWorker"
        private const val SINGLE_ITEM_BATCH_SIZE = 1
        private const val MAX_REPRESENTATIVE_NAMES = 5
        private const val MAX_MERCHANT_NAME_LENGTH = 24
        // 카테고리명 하나만 출력하므로 짧게 제한한다. 폭주해도 곧바로 잘려 fallback으로 진행된다.
        private const val CATEGORY_MAX_TOKENS = 64
        private const val MAX_TOP_MERCHANTS = 10
        private const val LOG_RESPONSE_LIMIT = 500
        private const val RAW_TEXT_LIMIT = 220
        private const val COMPACT_RAW_TEXT_LIMIT = 120
        // 프롬프트에 넣을 카테고리당 힌트 키워드 개수
        private const val HINT_KEYWORD_COUNT = 3
        private const val SESSION_ID = "card_expense_insight_worker"
        const val KEY_FORCE_RUN = "force_run"
        const val KEY_INCLUDE_LEDGER_TRANSACTIONS = "include_ledger_transactions"
        private const val CATEGORY_OTHER = "기타"
        private const val SYSTEM_INSTRUCTION =
            "너의 역할은 카드 거래 한 건을 카테고리 하나로 분류하는 것뿐이다. " +
                "반드시 한국어로만 판단하라. 가맹점이 무엇을 판매/제공하는지로 판단하고, " +
                "삼성카드·신한카드 등 카드사명은 결제 수단일 뿐 가맹점이 아니므로 판단에 쓰지 마라. " +
                "출력은 {\"category\":\"카테고리명\"} 형식의 JSON 하나뿐이며, 가맹점명·금액·요약·설명 등 다른 텍스트는 절대 생성하지 마라. " +
                "제공된 목록의 카테고리명만 사용하고, 맞는 항목이 없으면 기타를 사용하라."
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        private data class CategoryDefinition(
            val name: String,
            val description: String,
            val keywords: List<String>
        )

        private val CATEGORY_DEFINITIONS = listOf(
            CategoryDefinition(
                "식비",
                "음식점, 배달, 카페, 간식, 식료품 등 먹는 것과 관련된 지출",
                listOf("떡볶이", "분식", "식당", "음식", "푸드", "카페", "커피", "스타벅스", "투썸", "이디야", "메가커피", "빽다방", "치킨", "피자", "버거", "맥도날드", "롯데리아", "김밥", "국밥", "마라", "족발", "보쌈", "배달", "요기요", "배달의민족", "쿠팡이츠", "편의점", "cu", "gs25", "세븐일레븐", "마트", "식품")
            ),
            CategoryDefinition(
                "교통",
                "택시, 대중교통, 철도, 항공, 주유, 주차, 하이패스 등 이동 관련 지출",
                listOf("택시", "카카오t", "카카오 t", "타다", "우버", "버스", "지하철", "교통", "코레일", "철도", "ktx", "srt", "항공", "대한항공", "아시아나", "제주항공", "티웨이", "진에어", "주유", "충전소", "고속도로", "하이패스", "주차", "쏘카", "그린카")
            ),
            CategoryDefinition(
                "쇼핑",
                "온라인 쇼핑, 백화점, 의류, 잡화, 전자제품 구매",
                listOf("쿠팡", "네이버페이", "11번가", "g마켓", "옥션", "위메프", "티몬", "무신사", "백화점", "아울렛", "이마트", "홈플러스", "롯데마트", "다이소", "올리브영", "의류", "패션", "전자랜드", "하이마트", "애플", "삼성스토어")
            ),
            CategoryDefinition(
                "구독",
                "정기적으로 반복 결제되는 콘텐츠, 소프트웨어, 멤버십 서비스",
                listOf("넷플릭스", "유튜브", "youtube", "spotify", "스포티파이", "멜론", "지니", "왓챠", "티빙", "쿠팡와우", "쿠팡 와우", "구독", "정기결제", "멤버십", "icloud", "google", "openai", "chatgpt", "adobe", "notion")
            ),
            CategoryDefinition(
                "주거/공과금",
                "관리비, 전기, 가스, 수도, 월세 등 주거 유지 비용",
                listOf("관리비", "전기", "가스", "수도", "도시가스", "난방", "월세", "임대료", "아파트", "공과금")
            ),
            CategoryDefinition(
                "생활",
                "미용, 세탁, 생활용품, 반려동물, 개인관리 등 일상 서비스",
                listOf("미용", "헤어", "네일", "세탁", "문구", "생활용품", "생활", "반려", "펫", "동물병원", "약국화장품", "화장품", "잡화")
            ),
            CategoryDefinition(
                "의료",
                "병원, 약국, 진료, 검사, 건강 관련 지출",
                listOf("병원", "의원", "치과", "한의원", "약국", "의료", "진료", "검진", "건강", "안과", "피부과")
            ),
            CategoryDefinition(
                "교육",
                "학교, 학원, 강의, 도서, 시험, 학습 관련 지출",
                listOf("학원", "교육", "강의", "인강", "학교", "대학교", "교재", "서점", "도서", "시험", "토익", "자격증")
            ),
            CategoryDefinition(
                "통신",
                "휴대폰, 인터넷, 통신요금, 소프트웨어 통신 서비스",
                listOf("통신", "휴대폰", "핸드폰", "인터넷", "skt", "kt", "lg유플러스", "유플러스", "알뜰폰")
            ),
            CategoryDefinition(
                "문화/여가",
                "영화, 공연, 게임, 여행, 숙박, 스포츠, 취미",
                listOf("영화", "cgv", "롯데시네마", "메가박스", "공연", "전시", "게임", "스팀", "steam", "플레이스테이션", "닌텐도", "숙박", "호텔", "야놀자", "여기어때", "여행", "레저", "스포츠", "헬스", "필라테스", "골프")
            ),
            CategoryDefinition(
                "금융",
                "보험, 수수료, 이자, 금융서비스 관련 지출",
                listOf("보험", "수수료", "이자", "대출", "금융", "증권", "카드연회비", "연회비")
            )
        )
        private val ALLOWED_CATEGORIES = CATEGORY_DEFINITIONS.map { it.name }.toSet() + CATEGORY_OTHER

        // 예: "식비(식당·카페·배달), 교통(택시·버스·지하철), ..., 기타"
        private val CATEGORY_HINT =
            CATEGORY_DEFINITIONS.joinToString(", ") { definition ->
                "${definition.name}(${definition.keywords.take(HINT_KEYWORD_COUNT).joinToString("·")})"
            } + ", $CATEGORY_OTHER"
    }

    private fun currentMonthKey(): String = YearMonth.now(SEOUL_ZONE).toString()
}
