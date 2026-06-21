package com.example.gemma4ondevicetest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.gemma4ondevicetest.schedule.CalendarReader
import com.example.gemma4ondevicetest.schedule.ScheduleNotificationHelper
import com.example.gemma4ondevicetest.schedule.SchedulePromptBuilder
import com.example.gemma4ondevicetest.schedule.ScheduleUiState
import com.example.gemma4ondevicetest.schedule.ScheduleLoadState
import com.example.gemma4ondevicetest.schedule.ScheduleWorkScheduler
import com.example.gemma4ondevicetest.wallet.CardExpenseCandidateStore
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightCandidate
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightHistoryReducer
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightReport
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightScheduler
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightStore
import com.example.gemma4ondevicetest.wallet.CardExpenseMonthlyInsight
import com.example.gemma4ondevicetest.wallet.CardExpenseRepository
import com.example.gemma4ondevicetest.wallet.CardTransactionRecord
import com.example.gemma4ondevicetest.wallet.CardTransactionStore
import com.example.gemma4ondevicetest.wallet.MonthlyCardSummary
import com.example.gemma4ondevicetest.wallet.BatteryGateStatus
import com.example.gemma4ondevicetest.wallet.SubscriptionAnalysisReport
import com.example.gemma4ondevicetest.wallet.SubscriptionAnalysisScheduler
import com.example.gemma4ondevicetest.wallet.SubscriptionInsightStore
import com.example.gemma4ondevicetest.wallet.WalletNotificationPermissionManager
import com.example.gemma4ondevicetest.usage.AppUsageAllowlistPolicy
import com.example.gemma4ondevicetest.usage.AppUsageCollector
import com.example.gemma4ondevicetest.usage.AppUsageLogStore
import com.example.gemma4ondevicetest.usage.AppUsagePermissionManager
import com.example.gemma4ondevicetest.usage.AppUsageSessionRecord
import com.example.gemma4ondevicetest.usage.AppUsageStatsSummary
import com.example.gemma4ondevicetest.usage.AppUsageSyncScheduler
import com.example.gemma4ondevicetest.usage.AppUsageTopApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.YearMonth
import java.io.File
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val seoulZone = ZoneId.of("Asia/Seoul")

    private var sessions by mutableStateOf<List<ChatSession>>(emptyList())
    private var activeSessionId by mutableStateOf("")
    private var selectedSource by mutableStateOf(ModelStore.CUSTOM)
    private var hasStartedOnce = false
    private var currentScreen by mutableStateOf(AppScreen.HOME)
    private var busyState by mutableStateOf(BusyUiState())
    private var chatLoadingMessage by mutableStateOf<String?>(null)
    private var answerModelLoaded by mutableStateOf(false)
    private var answerModelReady by mutableStateOf(false)
    private var modelDescription by mutableStateOf("")
    private var lastLoadInfo by mutableStateOf("")
    private var knowledgeTools by mutableStateOf<List<KnowledgeTool>>(emptyList())
    private var documentImportState by mutableStateOf<DocumentImportUiState?>(null)
    private var userDocuments by mutableStateOf<List<UserDocument>>(emptyList())
    private var viewingCategory by mutableStateOf<KnowledgeCategory?>(null)
    private var viewingDocumentText by mutableStateOf<String?>(null)
    private var previousScreen by mutableStateOf(AppScreen.HOME)
    private var scheduleUiState by mutableStateOf(ScheduleUiState())
    private var notifEnabled by mutableStateOf(false)
    private var walletMonthlySummary by mutableStateOf(MonthlyCardSummary(currentMonthKey(), 0L, 0L, 0))
    private var walletRecentTransactions by mutableStateOf<List<CardTransactionRecord>>(emptyList())
    private var walletPermissionGranted by mutableStateOf(false)
    private var selectedTransaction by mutableStateOf<CardTransactionRecord?>(null)
    private var subscriptionReport by mutableStateOf(SubscriptionAnalysisReport(0L, 0L, "아직 분석 기록이 없습니다.", 0, emptyList()))
    private var subscriptionBatteryStatus by mutableStateOf(BatteryGateStatus(isCharging = false, levelPercent = 0))
    private var cardExpenseInsightReport by mutableStateOf(CardExpenseInsightReport("", 0L, 0L, 0, 0, "아직 분석 기록이 없습니다.", emptyList(), emptyList()))
    private var cardExpenseInsightHistory by mutableStateOf<List<CardExpenseMonthlyInsight>>(emptyList())
    private var cardExpenseInsightItems by mutableStateOf<List<CardExpenseInsightCandidate>>(emptyList())
    private var cardExpenseInsightRunning by mutableStateOf(false)
    private var cardInsightObserverRegistered = false
    private var cardInsightPolling = false
    private var cardInsightSawActive = false
    private var appUsagePermissionGranted by mutableStateOf(false)
    private var appUsageSummary by mutableStateOf(AppUsageStatsSummary())
    private var appUsageRecentSessions by mutableStateOf<List<AppUsageSessionRecord>>(emptyList())
    private var appUsageTopApps by mutableStateOf<List<AppUsageTopApp>>(emptyList())

    private val activeSession: ChatSession
        get() {
            val existing = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.firstOrNull()
            if (existing != null) return existing
            val fallback = ChatSessionStore.createSession(ChatKind.GENERAL)
            sessions = listOf(fallback)
            activeSessionId = fallback.id
            return fallback
        }

    private val pickModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importModel(uri)
    }
    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importDocument(uri)
    }
    private val schedulePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshSchedulePermissions()
        refreshHomeSchedulePreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        selectedSource = ModelStore.getSelectedModel(this)
        sessions = ChatSessionStore.loadSessions(this)
        activeSessionId = ChatSessionStore.getActiveSessionId(this)
            ?.takeIf { id -> sessions.any { it.id == id } }
            ?: sessions.firstOrNull()?.id
            ?: ChatSessionStore.createSession(ChatKind.GENERAL).also { sessions = listOf(it) }.id
        refreshRuntimeState()
        ScheduleNotificationHelper.createChannel(this)
        notifEnabled = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
            .getBoolean("notif_enabled", false)
        val (savedHour, savedMinute) = ScheduleWorkScheduler.readTargetTime(this)
        scheduleUiState = scheduleUiState.copy(notifHour = savedHour, notifMinute = savedMinute)
        if (notifEnabled) ScheduleWorkScheduler.ensureScheduled(this)
        refreshSchedulePermissions()
        refreshHomeSchedulePreview()
        refreshSubscriptionState()
        refreshAppUsageState(triggerSync = true)

        setContent {
            GemmaApp(
                uiState = AppUiState(
                    currentScreen       = currentScreen,
                    selectedModelLabel  = selectedSource.label,
                    modelDescription    = modelDescription,
                    answerModelLoaded   = answerModelLoaded,
                    answerModelReady    = answerModelReady,
                    sessions            = sessions,
                    activeSessionId     = activeSessionId,
                    busy                = busyState,
                    chatLoadingMessage  = chatLoadingMessage,
                    lastLoadInfo        = lastLoadInfo,
                    knowledgeTools      = knowledgeTools,
                    documentImportState = documentImportState,
                    userDocuments       = userDocuments,
                    viewingCategory     = viewingCategory,
                    viewingDocumentText = viewingDocumentText,
                    scheduleUiState     = scheduleUiState,
                    walletMonthlySummary = walletMonthlySummary,
                    walletRecentTransactions = walletRecentTransactions,
                    walletPermissionGranted = walletPermissionGranted,
                    selectedTransaction = selectedTransaction,
                    subscriptionReport = subscriptionReport,
                    subscriptionBatteryStatus = subscriptionBatteryStatus,
                    cardExpenseInsightReport = cardExpenseInsightReport,
                    cardExpenseInsightHistory = cardExpenseInsightHistory,
                    cardExpenseInsightItems = cardExpenseInsightItems,
                    cardExpenseInsightRunning = cardExpenseInsightRunning,
                    appUsagePermissionGranted = appUsagePermissionGranted,
                    appUsageSummary = appUsageSummary,
                    appUsageRecentSessions = appUsageRecentSessions,
                    appUsageTopApps = appUsageTopApps
                ),
                onOpenHome             = { currentScreen = AppScreen.HOME },
                onOpenDrawerChat       = { currentScreen = AppScreen.CHAT },
                onOpenDrawerModel      = { currentScreen = AppScreen.MODEL },
                onOpenDrawerStatus     = { currentScreen = AppScreen.STATUS },
                onOpenDrawerDocuments  = { currentScreen = AppScreen.DOCUMENTS },
                onOpenSchedule         = { currentScreen = AppScreen.SCHEDULE },
                onOpenAppUsage         = {
                    refreshAppUsageState(triggerSync = true)
                    currentScreen = AppScreen.APP_USAGE
                },
                onOpenWallet           = {
                    refreshCardInsightState()
                    currentScreen = AppScreen.WALLET
                },
                onOpenSubscriptions    = {
                    refreshSubscriptionState()
                    currentScreen = AppScreen.SUBSCRIPTIONS
                },
                onNewGeneralChat       = { createNewSession(ChatKind.GENERAL) },
                onNewChatForTool       = { toolId -> createNewSessionForTool(toolId) },
                onSelectSession        = { selectSession(it) },
                onDeleteSession        = { deleteSession(it) },
                onSendPrompt           = { sendPrompt(it) },
                onToggleModel          = { toggleModel() },
                onPickModel            = {
                    selectedSource = ModelStore.CUSTOM
                    ModelStore.setSelectedModel(this, selectedSource)
                    refreshRuntimeState()
                    pickModelLauncher.launch(arrayOf("*/*"))
                },
                onDownloadGemma        = { downloadGemmaModel() },
                onPickDocument         = { pickDocumentLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
                onEnterText            = { title, content -> importTextContent(title, content) },
                onSectionUpdated       = { index, updated -> updateSectionInImport(index, updated) },
                onConfirmImport        = { confirmDocumentImport() },
                onCancelImport         = { documentImportState = null },
                onDeleteDocument       = { filePath ->
                    ManifestLoader.deleteUserDocument(this, filePath)
                    refreshRuntimeState()
                },
                onViewDocument         = { category ->
                    previousScreen = currentScreen
                    viewingCategory = category
                    viewingDocumentText = KnowledgePromptBuilder.loadDocumentText(this, category)
                    currentScreen = AppScreen.DOCUMENT_VIEWER
                },
                onCloseDocument        = {
                    currentScreen = previousScreen
                    viewingCategory = null
                    viewingDocumentText = null
                },
                onScheduleRequestPermissions = { requestSchedulePermissions() },
                onScheduleRefresh            = { loadScheduleData() },
                onScheduleToggleNotification = { enabled -> setScheduleNotification(enabled) },
                onScheduleTimeChanged        = { h, m -> setScheduleTime(h, m) },
                onScheduleRunNow             = { runScheduleNow() },
                onWalletOpenPermissionSettings = {
                    WalletNotificationPermissionManager.openSettings(this)
                },
                onWalletSelectTransaction = { transaction ->
                    val repository = CardExpenseRepository(CardTransactionStore(this))
                    selectedTransaction = repository.findById(transaction.id) ?: transaction
                },
                onWalletDismissTransaction = { selectedTransaction = null },
                onAppUsageOpenPermissionSettings = {
                    AppUsagePermissionManager.openSettings(this)
                },
                onAppUsageRunSync = { runAppUsageSync() },
                onAppUsageExportCsv = { exportAppUsageCsv() },
                onAppUsageClearLog = { clearAppUsageLog() },
                onRunSubscriptionAnalysis = { runSubscriptionAnalysis() },
                onRunCardInsightAnalysis  = { runCardInsightAnalysis() },
                onReanalyzeCardInsightAnalysis = { reanalyzeAllCardInsight() },
                onReanalyzeCardInsightItem = { reanalyzeSingleCardInsight(it) },
                onResetCardInsightAnalysis = { resetCardInsightAnalysis() }
            )
        }

        refreshRuntimeState()
    }

    override fun onStop() {
        super.onStop()
        ModelRuntimeGate.freeAll()
        refreshRuntimeState()
    }

    override fun onStart() {
        super.onStart()
        refreshWalletState()
        refreshSubscriptionState()
        refreshSchedulePermissions()
        refreshHomeSchedulePreview()
        refreshAppUsageState(triggerSync = true)
        if (!hasStartedOnce) { hasStartedOnce = true; return }
    }

    override fun onDestroy() {
        super.onDestroy()
        ModelRuntimeGate.freeAll()
    }

    private fun importModel(uri: Uri) {
        setBusy(true, "모델 파일 등록 중...")
        lifecycleScope.launch {
            val result = ModelStore.importModel(this@MainActivity, uri)
            setBusy(false)
            result.onSuccess {
                ModelRuntimeGate.freeAll()
                selectedSource = ModelStore.CUSTOM
                refreshRuntimeState()
                toast("모델 파일을 등록했습니다. 필요할 때 직접 로드하세요.")
            }.onFailure {
                toast(it.message ?: "모델 파일 가져오기에 실패했습니다.")
                refreshRuntimeState()
            }
        }
    }

    private fun importTextContent(title: String, content: String) {
        if (content.isBlank()) { toast("입력된 텍스트가 없습니다."); return }
        lifecycleScope.launch {
            val tools = knowledgeTools
            val safeName = title.take(20).replace(Regex("[^a-zA-Z0-9가-힣_-]"), "_").ifBlank { "text_input" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.txt"
            val sections = withContext(Dispatchers.Default) {
                DocumentImporter.detectSections(content, tools)
            }
            documentImportState = DocumentImportUiState(
                fileName = fileName,
                sections = sections.map { s ->
                    SectionEditState(
                        index       = s.index,
                        previewText = s.previewText,
                        fullText    = s.fullText,
                        toolId      = s.confirmedToolId,
                        major       = s.confirmedMajor,
                        middle      = s.confirmedMiddle,
                        minor       = s.confirmedMinor
                    )
                }
            )
            currentScreen = AppScreen.DOCUMENTS
        }
    }

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                DocumentImporter.readTextFromUri(this@MainActivity, uri)
            }
            if (text.isBlank()) { toast("파일 내용을 읽을 수 없습니다."); return@launch }
            val tools = knowledgeTools
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.txt"
            val sections = withContext(Dispatchers.Default) {
                DocumentImporter.detectSections(text, tools)
            }
            documentImportState = DocumentImportUiState(
                fileName = fileName,
                sections = sections.map { s ->
                    SectionEditState(
                        index       = s.index,
                        previewText = s.previewText,
                        fullText    = s.fullText,
                        toolId      = s.confirmedToolId,
                        major       = s.confirmedMajor,
                        middle      = s.confirmedMiddle,
                        minor       = s.confirmedMinor
                    )
                }
            )
            currentScreen = AppScreen.DOCUMENTS
        }
    }

    private fun updateSectionInImport(index: Int, updated: SectionEditState) {
        val state = documentImportState ?: return
        documentImportState = state.copy(
            sections = state.sections.toMutableList().also { it[index] = updated }
        )
    }

    private fun confirmDocumentImport() {
        val state = documentImportState ?: return
        documentImportState = state.copy(isSaving = true)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val importerSections = state.sections.map { s ->
                    DocumentImporter.DetectedSection(
                        index           = s.index,
                        previewText     = s.previewText,
                        fullText        = s.fullText,
                        suggestedToolId = s.toolId,
                        suggestedMajor  = s.major,
                        suggestedMiddle = s.middle,
                        confirmedToolId = s.toolId,
                        confirmedMajor  = s.major,
                        confirmedMiddle = s.middle,
                        confirmedMinor  = s.minor
                    )
                }
                DocumentImporter.saveSections(this@MainActivity, state.fileName, importerSections)
            }
            documentImportState = null
            refreshRuntimeState()
            toast("${state.sections.size}개 섹션을 저장했습니다.")
        }
    }

    private fun createNewSessionForTool(toolId: String) {
        createNewSession(ChatKind.GENERAL)
    }

    private fun downloadGemmaModel() {
        selectedSource = ModelStore.GEMMA_4
        ModelStore.setSelectedModel(this, selectedSource)
        refreshRuntimeState()
        setBusy(true, "Gemma 4 E2B (2bit) 다운로드 중...", determinate = true, progressValue = 0)
        lifecycleScope.launch {
            val result = ModelStore.downloadModel(this@MainActivity, ModelStore.GEMMA_4) { value ->
                busyState = busyState.copy(progress = value)
            }
            setBusy(false)
            result.onSuccess {
                ModelRuntimeGate.freeAll()
                addSystemMessage("Gemma 4 E2B (2bit) 다운로드가 완료되었습니다.")
                refreshRuntimeState()
                toast("모델 다운로드가 완료되었습니다. 필요할 때 직접 로드하세요.")
            }.onFailure {
                toast(it.message ?: "모델 다운로드에 실패했습니다.")
                refreshRuntimeState()
            }
        }
    }

    private fun toggleModel() {
        if (answerModelLoaded) {
            ModelRuntimeGate.freeAll()
            addSystemMessage("모델을 언로드했습니다.")
            refreshRuntimeState()
            return
        }
        setBusy(true, "모델 로드 중...")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                ModelRuntimeGate.loadForUser(this@MainActivity, selectedSource)
            }
            setBusy(false)
            if (loaded) {
                addSystemMessage("모델이 로드되었습니다.")
            } else {
                toast(LlmEngine.getLastError() ?: "모델 로드에 실패했습니다.")
            }
            refreshRuntimeState()
        }
    }

    private fun sendPrompt(prompt: String) {
        if (!answerModelLoaded) { toast("먼저 모델을 로드하세요."); return }
        val historySnapshot = activeSession.messages.toList()
        val sessionId = activeSessionId
        val internalContext = buildInternalDataContext(prompt)
        appendMessage(ChatMessage(prompt, fromUser = true))
        updateSessionTitle(prompt)
        setBusy(true, "응답 생성 중...")
        chatLoadingMessage = getString(R.string.chat_loading_default)
        lifecycleScope.launch {
            val reply = runCatching {
                withContext(Dispatchers.IO) {
                    // Inject stored history only when seeding a brand-new Conversation (no KV cache yet)
                    val history = if (!LlmEngine.hasConversation(sessionId)) {
                        buildConversationHistory(historySnapshot)
                    } else ""
                    val promptResult = buildModelPrompt(prompt, history, internalContext) { chatLoadingMessage = it }
                    val generated = LlmEngine.generateForSession(
                        sessionId,
                        promptResult.prompt,
                        LlmEngine.LlmConfig(systemInstruction = ChatKind.GENERAL.systemInstruction)
                    )
                    formatModelReply(generated, promptResult.selection)
                }
            }.getOrElse { LlmEngine.getLastError() ?: it.message ?: "" }
            setBusy(false)
            chatLoadingMessage = null
            if (reply.isBlank()) toast(LlmEngine.getLastError() ?: "응답 생성에 실패했습니다.")
            else appendMessage(ChatMessage(reply, fromUser = false))
            refreshRuntimeState()
        }
    }

    private fun formatModelReply(reply: String, selection: KnowledgeSelection?): String {
        if (reply.isBlank()) return ""
        val cleanedReply = cleanMarkdownForMobile(reply)
        if (selection == null) return cleanedReply

        return buildString {
            appendLine("[ Tool Calling ]")
            appendLine("참고 문서 :")
            selection.categories.forEach { appendLine("  · ${it.label}") }
            appendLine("────────────────────")
            appendLine()
            append(cleanedReply)
        }.trimEnd()
    }

    private fun cleanMarkdownForMobile(text: String): String {
        return text.lines()
            .map { line ->
                line.trim()
                    .removePrefix("### ").removePrefix("## ").removePrefix("# ")
                    .replace("**", "").replace("__", "")
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun setBusy(busy: Boolean, status: String? = null, determinate: Boolean = false, progressValue: Int = 0) {
        busyState = if (busy) BusyUiState(true, status.orEmpty(), determinate, progressValue) else BusyUiState()
    }

    private fun appendMessage(message: ChatMessage) {
        val session = activeSession
        val updated = session.copy(messages = (session.messages + message).toMutableList(), updatedAt = System.currentTimeMillis())
        sessions = sessions.map { if (it.id == session.id) updated else it }
        persistSessions()
    }

    private fun addSystemMessage(message: String) = appendMessage(ChatMessage(message, fromUser = false))

    private fun createNewSession(kind: ChatKind) {
        val session = ChatSessionStore.createSession(kind)
        sessions = sessions + session
        activeSessionId = session.id
        currentScreen = AppScreen.CHAT
        persistSessions()
    }

    private fun selectSession(sessionId: String) {
        activeSessionId = sessionId
        currentScreen = AppScreen.CHAT
        persistSessions()
    }

    private fun deleteSession(sessionId: String) {
        LlmEngine.clearSession(sessionId)
        val removedActive = activeSessionId == sessionId
        var nextSessions = sessions.filterNot { it.id == sessionId }
        if (nextSessions.isEmpty()) nextSessions = listOf(ChatSessionStore.createSession(ChatKind.GENERAL))
        sessions = nextSessions
        if (removedActive || sessions.none { it.id == activeSessionId }) {
            activeSessionId = sessions.maxByOrNull { it.updatedAt }?.id ?: sessions.first().id
        }
        persistSessions()
    }

    private fun updateSessionTitle(prompt: String) {
        val session = activeSession
        if (session.title != session.kind.defaultTitle) return
        val updated = session.copy(title = prompt.take(24))
        sessions = sessions.map { if (it.id == session.id) updated else it }
        persistSessions()
    }

    private fun persistSessions() = ChatSessionStore.saveSessions(this, sessions, activeSessionId)

    private fun buildModelPrompt(
        prompt: String,
        history: String = "",
        internalDataContext: String = "",
        onLoadingPhaseChanged: (String) -> Unit = {}
    ): KnowledgePromptResult {
        onLoadingPhaseChanged(getString(R.string.chat_loading_classifying))
        val routerResult = AgentRouter.route(this, prompt)
        if (routerResult == null) {
            val fullPrompt = if (internalDataContext.isNotBlank()) {
                buildString {
                    appendLine("반드시 한국어로만 답하라.")
                    appendLine()
                    appendLine(internalDataContext)
                    appendLine()
                    if (history.isNotBlank()) {
                        appendLine(history)
                        appendLine()
                    }
                    append("현재 질문: $prompt")
                }
            } else {
                if (history.isNotBlank()) "$history\n현재 질문: $prompt" else prompt
            }
            return KnowledgePromptResult(fullPrompt, null)
        }
        return KnowledgePromptBuilder.buildAgentPromptResult(this, routerResult, prompt, history, internalDataContext)
    }

    private fun isInternalDataQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return listOf(
            "이번 달", "카드", "소비", "결제", "사용내역", "카테고리",
            "정기결제", "정기", "구독", "다음 일정", "일정", "이번 주"
        ).any { lower.contains(it) }
    }

    private fun buildInternalDataContext(prompt: String): String {
        if (!isInternalDataQuery(prompt)) return ""
        val lower = prompt.lowercase()
        val asksCard = listOf("카드", "소비", "결제", "사용내역", "카테고리", "이번 달").any { lower.contains(it) }
        val asksSubscription = listOf("정기결제", "정기", "구독").any { lower.contains(it) }
        val asksSchedule = listOf("일정", "다음 일정", "이번 주").any { lower.contains(it) }
        val previousMonthInsight = CardExpenseInsightHistoryReducer.findPreviousMonth(
            history = cardExpenseInsightHistory,
            currentMonthKey = cardExpenseInsightReport.monthKey
        )
        return buildString {
            appendLine("[이번 달 앱 데이터 요약]")
            if (asksCard) {
                appendLine("카드 사용: ${walletMonthlySummary.monthKey} 기준 ${formatWonContext(walletMonthlySummary.netSpent)} (${walletMonthlySummary.transactionCount}건)")
                if (cardExpenseInsightReport.categoryBreakdowns.isNotEmpty()) {
                    val topCats = cardExpenseInsightReport.categoryBreakdowns.take(3)
                    appendLine("대표 카테고리: ${topCats.joinToString(", ") { "${it.category} ${it.percentageOfTotal.toInt()}%" }}")
                    appendLine("인사이트 후보 합계: ${formatWonContext(cardExpenseInsightReport.totalAmount)} (${cardExpenseInsightReport.totalCount}건)")
                    if (previousMonthInsight != null) {
                        val amountDelta = cardExpenseInsightReport.totalAmount - previousMonthInsight.totalAmount
                        val countDelta = cardExpenseInsightReport.totalCount - previousMonthInsight.totalCount
                        appendLine(
                            "전월 비교: ${previousMonthInsight.monthKey} 대비 ${formatSignedWonContext(amountDelta)}, 건수 ${formatSignedCountContext(countDelta)}"
                        )
                    }
                } else if (cardExpenseInsightReport.pendingCount > 0) {
                    appendLine("카테고리 분석 대기 중 (${cardExpenseInsightReport.pendingCount}건)")
                }
            }
            if (asksSubscription) {
                val count = subscriptionReport.candidates.size
                if (count > 0) {
                    val names = subscriptionReport.candidates.take(3).map { it.serviceName }
                    appendLine("정기결제 후보: ${count}건 (${names.joinToString(", ")})")
                } else {
                    appendLine("정기결제 후보: 아직 없음")
                }
            }
            if (asksSchedule) {
                val entries = scheduleUiState.entries
                if (entries.isNotEmpty()) {
                    val next = entries.first()
                    appendLine("다음 일정: ${next.dateLabel} ${next.timeLabel} ${next.title}")
                    if (entries.size > 1) appendLine("이번 주 총 ${entries.size}건 일정")
                } else {
                    appendLine("저장된 일정 없음")
                }
            }
        }.trim()
    }

    private fun formatWonContext(amount: Long): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(amount) + "원"

    private fun formatSignedWonContext(amount: Long): String {
        val sign = if (amount >= 0) "+" else "-"
        return sign + formatWonContext(kotlin.math.abs(amount))
    }

    private fun formatSignedCountContext(count: Int): String {
        val sign = if (count >= 0) "+" else ""
        return "${sign}${count}건"
    }

    private fun buildConversationHistory(messages: List<ChatMessage>, maxExchanges: Int = 2): String {
        val pairs = mutableListOf<Pair<String, String>>()
        var i = messages.size - 1
        while (i >= 1 && pairs.size < maxExchanges) {
            val aiMsg = messages[i]
            val userMsg = messages[i - 1]
            if (!aiMsg.fromUser && userMsg.fromUser) {
                val aiText = if (aiMsg.text.contains("────────────────────")) {
                    aiMsg.text.substringAfter("────────────────────").trim().take(300)
                } else {
                    aiMsg.text.take(300)
                }
                if (aiText.isNotBlank()) {
                    pairs.add(0, userMsg.text.take(150) to aiText)
                }
                i -= 2
            } else {
                i--
            }
        }
        if (pairs.isEmpty()) return ""
        return buildString {
            appendLine("[이전 대화]")
            pairs.forEach { (userText, aiText) ->
                appendLine("유저: $userText")
                appendLine("AI: $aiText")
            }
        }
    }

    private fun refreshRuntimeState() {
        answerModelLoaded = LlmEngine.isLoaded
        answerModelReady  = ModelStore.hasModel(this, selectedSource)
        modelDescription  = ModelStore.describe(this, selectedSource)
        knowledgeTools    = ManifestLoader.getTools(this)
        userDocuments     = ManifestLoader.listUserDocuments(this)
        refreshWalletState()
        refreshSubscriptionState()
        refreshAppUsageState(triggerSync = false)
    }

    private fun refreshWalletState() {
        val repository = CardExpenseRepository(CardTransactionStore(this))
        val monthKey = currentMonthKey()
        walletMonthlySummary = repository.getMonthlySummary(monthKey)
        walletRecentTransactions = repository.findRecentTransactions(limit = 20)
        walletPermissionGranted = WalletNotificationPermissionManager.isGranted(this)
        refreshCardInsightState()
    }

    private fun refreshSubscriptionState() {
        subscriptionReport = SubscriptionInsightStore(this).loadReport()
        subscriptionBatteryStatus = SubscriptionAnalysisScheduler.currentBatteryGateStatus(this)
    }

    private fun refreshCardInsightState() {
        val store = CardExpenseInsightStore(this)
        store.ensureCurrentMonth(currentMonthKey())
        val loadedReport = store.loadReport()
        if (!cardExpenseInsightRunning && isTransientCardInsightStatus(loadedReport.statusMessage)) {
            val pendingCount = CardExpenseCandidateStore(this).loadPending(includeLedgerTransactions = true).size
            store.updateStatus(
                statusMessage = if (loadedReport.lastCompletedAt > 0L) {
                    "마지막 분석 이후 상태를 확인했습니다. 새 내역이 있으면 '지금 분석'으로 다시 분석할 수 있습니다."
                } else {
                    "분석 대기 중입니다. '지금 분석'을 누르면 현재 대기/거래 내역을 분석합니다."
                },
                pendingCount = pendingCount
            )
            cardExpenseInsightReport = store.loadReport()
        } else {
            cardExpenseInsightReport = loadedReport
        }
        cardExpenseInsightHistory = store.loadHistory()
        cardExpenseInsightItems = CardExpenseCandidateStore(this).loadAnalysisItems()
    }

    private fun runCardInsightAnalysis() {
        refreshCardInsightState()
        val candidateStore = CardExpenseCandidateStore(this)
        val pending = candidateStore.loadPending(includeLedgerTransactions = true)
        if (pending.isEmpty()) {
            toast("분석할 카드 후보 또는 거래 내역이 없습니다.")
            return
        }
        val insightStore = CardExpenseInsightStore(this)
        cardExpenseInsightRunning = true
        cardInsightSawActive = false
        insightStore.updateStatus(
            statusMessage = "카드 인사이트 강제 분석을 시작합니다. 알림 후보와 최근 거래 내역을 확인 중입니다.",
            pendingCount = pending.size
        )
        refreshCardInsightState()
        CardExpenseInsightScheduler.enqueue(
            context = this,
            forceRun = true,
            includeLedgerTransactions = true
        )
        observeCardInsightWork()
        pollCardInsightWork()
        toast("카드 인사이트 강제 분석을 시작했습니다.")
        refreshCardInsightState()
    }

    // 강제 분석은 1건씩 처리되며 남은 내역이 있으면 같은 unique work에 다음 작업이 append 된다.
    // 개별 workId 대신 unique work 전체를 관찰해, 체인이 모두 끝났을 때만 로딩 상태를 해제한다.
    private fun observeCardInsightWork() {
        if (cardInsightObserverRegistered) return
        cardInsightObserverRegistered = true
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(CardExpenseInsightScheduler.WORK_NAME)
            .observe(this) { infos ->
                val active = infos.any { !it.state.isFinished }
                if (active) {
                    cardInsightSawActive = true
                    cardExpenseInsightRunning = true
                    if (!cardInsightPolling) pollCardInsightWork()
                } else if (cardInsightSawActive) {
                    // 활성 상태를 본 뒤 모든 작업이 끝났을 때만 종료로 처리한다.
                    cardInsightSawActive = false
                    cardExpenseInsightRunning = false
                    refreshWalletState()
                    val report = CardExpenseInsightStore(this).loadReport()
                    toast(
                        if (report.lastCompletedAt > 0L && report.categoryBreakdowns.isNotEmpty()) {
                            "카드 인사이트 분석이 완료되었습니다."
                        } else {
                            report.statusMessage.ifBlank { "카드 인사이트 분석을 마쳤습니다." }
                        }
                    )
                }
                refreshCardInsightState()
            }
    }

    private fun isTransientCardInsightStatus(message: String): Boolean {
        val transientWords = listOf(
            "강제 분석을 시작",
            "준비 중",
            "실행 조건을 확인",
            "런타임 상태를 확인",
            "모델을 로드",
            "분류 중",
            "분석 중",
            "저장 중"
        )
        return transientWords.any { message.contains(it) }
    }

    // 한 작업이 도는 동안에도 단계별 상태 메시지가 갱신되도록, 실행 중에는 1초마다 새로고침한다.
    private fun pollCardInsightWork() {
        if (cardInsightPolling) return
        cardInsightPolling = true
        lifecycleScope.launch {
            var ticks = 0
            while (cardExpenseInsightRunning && ticks < 600) {
                delay(1_000L)
                refreshCardInsightState()
                ticks++
            }
            cardInsightPolling = false
        }
    }

    private fun reanalyzeSingleCardInsight(candidate: CardExpenseInsightCandidate) {
        if (cardExpenseInsightRunning) {
            toast("이미 분석이 진행 중입니다. 잠시 후 다시 시도해 주세요.")
            return
        }
        // 리포트는 분석된 후보로부터 매번 새로 파생되므로, 이 건만 대기로 돌리고 다시 분석하면
        // 새 분류가 그대로 반영된다. (별도 차감 불필요)
        CardExpenseCandidateStore(this).markPending(candidate)
        refreshCardInsightState()
        runCardInsightAnalysis()
    }

    private fun reanalyzeAllCardInsight() {
        val candidateStore = CardExpenseCandidateStore(this)
        // 월별 기록은 유지하면서 현재 달 결과만 비우고, 모든 후보를 다시 대기 상태로 돌린다.
        CardExpenseInsightStore(this).clearCurrentReport()
        candidateStore.resetAnalyzed()
        refreshCardInsightState()
        runCardInsightAnalysis()
    }

    private fun resetCardInsightAnalysis() {
        val candidateStore = CardExpenseCandidateStore(this)
        val insightStore = CardExpenseInsightStore(this)
        insightStore.reset()
        candidateStore.resetAnalyzed()
        // reset()이 저장된 pendingCount까지 0으로 비우므로, 실제 후보 수로 다시 채워야
        // '지금 분석' 버튼이 곧바로 활성화된다.
        insightStore.updateStatus(
            statusMessage = "인사이트 기록을 초기화했습니다. '지금 분석'으로 현재 사용 내역을 다시 분석할 수 있습니다.",
            pendingCount = candidateStore.pendingCount()
        )
        refreshCardInsightState()
        toast("카드 인사이트 기록을 초기화했습니다.")
    }

    private fun runSubscriptionAnalysis() {
        refreshSubscriptionState()
        if (!subscriptionBatteryStatus.isEligible) {
            toast("충전 중이며 배터리 100%일 때만 분석할 수 있습니다.")
            return
        }
        val remainingCooldown =
            SubscriptionAnalysisScheduler.remainingCooldownMillis(subscriptionReport.lastCompletedAt)
        if (remainingCooldown > 0L) {
            toast("최근 분석 완료 후 ${SubscriptionAnalysisScheduler.formatRemainingCooldown(remainingCooldown)} 뒤에 다시 분석할 수 있습니다.")
            return
        }
        SubscriptionAnalysisScheduler.enqueue(this)
        toast("정기결제 후보 분석을 예약했습니다.")
        refreshSubscriptionState()
    }

    private fun refreshAppUsageState(triggerSync: Boolean) {
        appUsagePermissionGranted = AppUsagePermissionManager.isGranted(this)
        if (!appUsagePermissionGranted) {
            appUsageSummary = AppUsageStatsSummary()
            appUsageRecentSessions = emptyList()
            appUsageTopApps = emptyList()
            return
        }
        AppUsageSyncScheduler.ensureScheduled(this)
        lifecycleScope.launch {
            if (triggerSync) {
                withContext(Dispatchers.IO) {
                    AppUsageCollector.collect(this@MainActivity)
                }
            }
            val store = AppUsageLogStore.getInstance(this@MainActivity)
            withContext(Dispatchers.IO) { store.enforceRetention() }
            appUsageSummary = withContext(Dispatchers.IO) { store.loadSummary() }
            appUsageRecentSessions = withContext(Dispatchers.IO) { store.loadRecentSessions(limit = 120) }
            appUsageTopApps = withContext(Dispatchers.IO) { store.loadTopApps(limit = 8) }
        }
    }

    private fun runAppUsageSync() {
        if (!AppUsagePermissionManager.isGranted(this)) {
            toast("사용 패턴 접근 권한이 필요합니다.")
            return
        }
        setBusy(true, "앱 사용 로그 동기화 중...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUsageCollector.collect(this@MainActivity)
            }
            setBusy(false)
            refreshAppUsageState(triggerSync = false)
            toast("앱 사용 세션 ${result.insertedCount}건을 저장했습니다.")
        }
    }

    private fun exportAppUsageCsv() {
        if (!AppUsagePermissionManager.isGranted(this)) {
            toast("사용 패턴 접근 권한이 필요합니다.")
            return
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val store = AppUsageLogStore.getInstance(this@MainActivity)
                store.enforceRetention()
                val rows = store.loadAllSessionsForExport()
                writeAppUsageCsv(rows)
            }
            if (file == null) {
                toast("학습 CSV를 만들려면 최소 2개 이상의 앱 사용 세션이 필요합니다.")
                return@launch
            }
            shareCsv(file)
        }
    }

    private fun writeAppUsageCsv(rows: List<AppUsageSessionRecord>): File? {
        val examples = buildNextAppTrainingExamples(rows)
        if (examples.isEmpty()) return null
        val exportDir = File(cacheDir, "exports").apply { mkdirs() }
        val fileName = "next_app_training_examples_${System.currentTimeMillis()}.csv"
        val file = File(exportDir, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine(
                listOf(
                    "example_id",
                    "event_started_at_millis",
                    "event_started_at_iso",
                    "weekday",
                    "hhmm",
                    "time_bucket_30m",
                    "previous_app",
                    "previous_app_label",
                    "previous_duration_seconds",
                    "previous_category",
                    "recent_app_1",
                    "recent_app_2",
                    "recent_app_3",
                    "recent_sequence",
                    "gap_since_previous_seconds",
                    "label_next_app",
                    "label_next_app_label",
                    "label_next_started_at_millis",
                    "label_next_started_at_iso"
                ).joinToString(",")
            )
            examples.forEachIndexed { index, example ->
                writer.appendLine(
                    listOf(
                        (index + 1).toString(),
                        example.current.startedAtMillis.toString(),
                        Instant.ofEpochMilli(example.current.startedAtMillis).atZone(seoulZone).toString(),
                        example.current.weekday.toString(),
                        example.current.hhmm.toString().padStart(4, '0'),
                        timeBucket30m(example.current.hhmm),
                        example.current.packageName,
                        AppUsageAllowlistPolicy.resolveAppLabel(this, example.current.packageName),
                        example.current.durationSeconds.toString(),
                        AppUsageAllowlistPolicy.categoryLabel(this, example.current.appCategory),
                        example.recentApps.getOrNull(0).orEmpty(),
                        example.recentApps.getOrNull(1).orEmpty(),
                        example.recentApps.getOrNull(2).orEmpty(),
                        example.recentApps.joinToString("|"),
                        example.gapSincePreviousSeconds?.toString().orEmpty(),
                        example.next.packageName,
                        AppUsageAllowlistPolicy.resolveAppLabel(this, example.next.packageName),
                        example.next.startedAtMillis.toString(),
                        Instant.ofEpochMilli(example.next.startedAtMillis).atZone(seoulZone).toString()
                    ).joinToString(",") { csvCell(it) }
                )
            }
        }
        return file
    }

    private data class NextAppTrainingExample(
        val current: AppUsageSessionRecord,
        val next: AppUsageSessionRecord,
        val recentApps: List<String>,
        val gapSincePreviousSeconds: Long?
    )

    private fun buildNextAppTrainingExamples(rows: List<AppUsageSessionRecord>): List<NextAppTrainingExample> {
        val compacted = rows.sortedBy { it.startedAtMillis }
            .fold(mutableListOf<AppUsageSessionRecord>()) { acc, row ->
                val last = acc.lastOrNull()
                if (last?.packageName == row.packageName) {
                    acc[acc.lastIndex] = last.copy(
                        endedAtMillis = maxOf(last.endedAtMillis, row.endedAtMillis),
                        durationSeconds = last.durationSeconds + row.durationSeconds
                    )
                } else {
                    acc += row
                }
                acc
            }
        if (compacted.size < 2) return emptyList()

        return compacted.dropLast(1).mapIndexed { index, current ->
            val next = compacted[index + 1]
            val recentApps = compacted
                .subList(0, index + 1)
                .takeLast(3)
                .map { it.packageName }
                .asReversed()
            val previous = compacted.getOrNull(index - 1)
            val gapSeconds = previous?.let {
                ((current.startedAtMillis - it.endedAtMillis) / 1000L).coerceAtLeast(0L)
            }
            NextAppTrainingExample(current, next, recentApps, gapSeconds)
        }
    }

    private fun timeBucket30m(hhmm: Int): String {
        val hour = hhmm / 100
        val minute = hhmm % 100
        val bucketMinute = if (minute < 30) 0 else 30
        return "%02d:%02d".format(hour, bucketMinute)
    }

    private fun shareCsv(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Next app training examples CSV")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Next app 학습 CSV 내보내기"))
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun clearAppUsageLog() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppUsageLogStore.getInstance(this@MainActivity).clearAll()
            }
            refreshAppUsageState(triggerSync = false)
            toast("앱 사용 로그 DB를 비웠습니다.")
        }
    }

    private fun currentMonthKey(): String = YearMonth.now(seoulZone).toString()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // ─── Schedule feature ────────────────────────────────────────

    private fun refreshSchedulePermissions() {
        scheduleUiState = scheduleUiState.copy(
            calendarPermission = CalendarReader.hasCalendarPermission(this),
            notifPermission    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true,
            notifEnabled = notifEnabled
        )
    }

    private fun requestSchedulePermissions() {
        val perms = mutableListOf(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        schedulePermissionLauncher.launch(perms.toTypedArray())
    }

    private fun refreshHomeSchedulePreview() {
        if (!CalendarReader.hasCalendarPermission(this)) {
            scheduleUiState = scheduleUiState.copy(entries = emptyList())
            return
        }
        lifecycleScope.launch {
            val now = ZonedDateTime.now(seoulZone)
            val allEvents = withContext(Dispatchers.IO) {
                CalendarReader.getAllUpcomingWeekEvents(this@MainActivity, now)
            }
            val allEntries = SchedulePromptBuilder.buildEntries(allEvents)
            scheduleUiState = scheduleUiState.copy(entries = allEntries)
        }
    }

    private fun loadScheduleData() {
        scheduleUiState = scheduleUiState.copy(loadState = ScheduleLoadState.LOADING)
        lifecycleScope.launch {
            val now = ZonedDateTime.now(seoulZone)

            // 전체 일정 (UI 표시용 — 필터 없음)
            val allEvents  = withContext(Dispatchers.IO) { CalendarReader.getAllUpcomingWeekEvents(this@MainActivity, now) }
            val allEntries = SchedulePromptBuilder.buildEntries(allEvents)

            // Gmail 필터 일정 (Gemma 프롬프트용)
            val gmailEvents = withContext(Dispatchers.IO) { CalendarReader.getUpcomingWeekEvents(this@MainActivity, now) }
            val summary     = SchedulePromptBuilder.build(gmailEvents, now)

            if (!answerModelLoaded) {
                scheduleUiState = scheduleUiState.copy(
                    loadState  = ScheduleLoadState.DONE,
                    entries    = allEntries,
                    gemmaLines = emptyList()
                )
                return@launch
            }

            val raw = withContext(Dispatchers.IO) {
                LlmEngine.generateForSession(
                    sessionId = "schedule_ui_session",
                    prompt    = summary.prompt,
                    config    = LlmEngine.LlmConfig(
                        maxTokens         = 300,
                        systemInstruction = "반드시 한국어로만 답하라. 지시한 출력 형식 외에 다른 말은 절대 하지 않는다."
                    )
                ).also { LlmEngine.clearSession("schedule_ui_session") }
            }

            val lines = SchedulePromptBuilder.resolveLines(raw, summary.entries)
            scheduleUiState = scheduleUiState.copy(
                loadState  = ScheduleLoadState.DONE,
                entries    = allEntries,
                gemmaLines = lines
            )
        }
    }

    private fun setScheduleNotification(enabled: Boolean) {
        notifEnabled = enabled
        getSharedPreferences("schedule_prefs", MODE_PRIVATE).edit()
            .putBoolean("notif_enabled", enabled).apply()
        scheduleUiState = scheduleUiState.copy(notifEnabled = enabled)
        if (enabled) ScheduleWorkScheduler.schedule(this)
        else ScheduleWorkScheduler.cancel(this)
    }

    private fun setScheduleTime(hour: Int, minute: Int) {
        ScheduleWorkScheduler.saveTargetTime(this, hour, minute)
        scheduleUiState = scheduleUiState.copy(notifHour = hour, notifMinute = minute)
        if (notifEnabled) ScheduleWorkScheduler.schedule(this)
    }

    private fun runScheduleNow() {
        // Request notification permission first if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            schedulePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            return
        }
        scheduleUiState = scheduleUiState.copy(loadState = ScheduleLoadState.LOADING)
        lifecycleScope.launch {
            val now = ZonedDateTime.now(seoulZone)

            // 전체 일정 (UI 표시용)
            val allEvents  = withContext(Dispatchers.IO) { CalendarReader.getAllUpcomingWeekEvents(this@MainActivity, now) }
            val allEntries = SchedulePromptBuilder.buildEntries(allEvents)

            // Gmail 필터 일정 (Gemma + 알림용)
            val gmailEvents = withContext(Dispatchers.IO) { CalendarReader.getUpcomingWeekEvents(this@MainActivity, now) }
            val summary     = SchedulePromptBuilder.build(gmailEvents, now)

            val raw = if (answerModelLoaded) {
                withContext(Dispatchers.IO) {
                    LlmEngine.generateForSession(
                        sessionId = "schedule_runnow_session",
                        prompt    = summary.prompt,
                        config    = LlmEngine.LlmConfig(
                            maxTokens         = 300,
                            systemInstruction = "반드시 한국어로만 답하라. 지시한 출력 형식 외에 다른 말은 절대 하지 않는다."
                        )
                    ).also { LlmEngine.clearSession("schedule_runnow_session") }
                }
            } else ""

            val lines = SchedulePromptBuilder.resolveLines(raw, summary.entries)
            val body  = SchedulePromptBuilder.formatForNotification(lines)

            ScheduleNotificationHelper.showSummary(
                context = this@MainActivity,
                title   = "일주일 일정 요약",
                body    = body
            )

            scheduleUiState = scheduleUiState.copy(
                loadState  = ScheduleLoadState.DONE,
                entries    = allEntries,
                gemmaLines = lines
            )
            toast("알림을 발송했습니다")
        }
    }
}
