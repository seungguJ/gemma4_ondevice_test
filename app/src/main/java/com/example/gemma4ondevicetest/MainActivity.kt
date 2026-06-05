package com.example.gemma4ondevicetest

import android.Manifest
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
import androidx.lifecycle.lifecycleScope
import com.example.gemma4ondevicetest.schedule.CalendarReader
import com.example.gemma4ondevicetest.schedule.ScheduleNotificationHelper
import com.example.gemma4ondevicetest.schedule.SchedulePromptBuilder
import com.example.gemma4ondevicetest.schedule.ScheduleUiState
import com.example.gemma4ondevicetest.schedule.ScheduleLoadState
import com.example.gemma4ondevicetest.schedule.ScheduleWorkScheduler
import com.example.gemma4ondevicetest.wallet.CardExpenseRepository
import com.example.gemma4ondevicetest.wallet.CardTransactionRecord
import com.example.gemma4ondevicetest.wallet.CardTransactionStore
import com.example.gemma4ondevicetest.wallet.MonthlyCardSummary
import com.example.gemma4ondevicetest.wallet.WalletNotificationLogEntry
import com.example.gemma4ondevicetest.wallet.WalletNotificationLogStore
import com.example.gemma4ondevicetest.wallet.WalletNotificationPermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    private var sessions by mutableStateOf<List<ChatSession>>(emptyList())
    private var activeSessionId by mutableStateOf("")
    private var selectedSource by mutableStateOf(ModelStore.CUSTOM)
    private var modelWasLoaded = false
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
    private var walletNotificationLogs by mutableStateOf<List<WalletNotificationLogEntry>>(emptyList())

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
                    walletNotificationLogs = walletNotificationLogs
                ),
                onOpenHome             = { currentScreen = AppScreen.HOME },
                onOpenDrawerChat       = { currentScreen = AppScreen.CHAT },
                onOpenDrawerModel      = { currentScreen = AppScreen.MODEL },
                onOpenDrawerStatus     = { currentScreen = AppScreen.STATUS },
                onOpenDrawerDocuments  = { currentScreen = AppScreen.DOCUMENTS },
                onOpenSchedule         = { currentScreen = AppScreen.SCHEDULE },
                onOpenWallet           = { currentScreen = AppScreen.WALLET },
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
                onWalletClearLog = {
                    WalletNotificationLogStore(this).clear()
                    refreshWalletState()
                },
                onScheduleTestAlarm          = if (BuildConfig.DEBUG) ({
                    ScheduleWorkScheduler.scheduleTestIn60Seconds(this)
                    toast("60초 후 알람을 등록했습니다")
                }) else null
            )
        }

        if (ModelStore.hasModel(this, selectedSource)) {
            autoLoadMainModel()
        }
    }

    override fun onStop() {
        super.onStop()
        modelWasLoaded = LlmEngine.isLoaded
        LlmEngine.free()
        refreshRuntimeState()
    }

    override fun onStart() {
        super.onStart()
        refreshWalletState()
        refreshSchedulePermissions()
        refreshHomeSchedulePreview()
        if (!hasStartedOnce) { hasStartedOnce = true; return }
        if (modelWasLoaded && ModelStore.hasModel(this, selectedSource)) {
            autoLoadMainModel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LlmEngine.free()
    }

    private fun importModel(uri: Uri) {
        setBusy(true, "모델 파일 등록 중...")
        lifecycleScope.launch {
            val result = ModelStore.importModel(this@MainActivity, uri)
            setBusy(false)
            result.onSuccess {
                LlmEngine.free()
                selectedSource = ModelStore.CUSTOM
                refreshRuntimeState()
                autoLoadMainModel()
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

    private fun autoLoadMainModel() {
        if (LlmEngine.isLoaded) return
        val cacheExists = litertCacheExists()
        setBusy(true, "모델 로드 중...")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                LlmEngine.loadModel(this@MainActivity, selectedSource)
            }
            setBusy(false)
            if (!loaded) {
                val error = LlmEngine.getLastError() ?: "모델 로드에 실패했습니다."
                toast(error)
                lastLoadInfo = "로드 실패"
            } else {
                val ms = LlmEngine.lastLoadDurationMs
                val cacheTag = if (cacheExists) "캐시 사용" else "최초 컴파일"
                lastLoadInfo = "%.1f초 (%s)".format(ms / 1000f, cacheTag)
                toast("모델 로드 완료: $lastLoadInfo")
            }
            refreshRuntimeState()
        }
    }

    private fun litertCacheExists(): Boolean {
        return cacheDir.listFiles()?.any { f ->
            f.length() > 0 && (f.extension == "bin" ||
                f.name.contains("xnnpack", ignoreCase = true) ||
                f.name.contains("litertlm", ignoreCase = true))
        } ?: false
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
                LlmEngine.free()
                addSystemMessage("Gemma 4 E2B (2bit) 다운로드가 완료되었습니다.")
                refreshRuntimeState()
                autoLoadMainModel()
            }.onFailure {
                toast(it.message ?: "모델 다운로드에 실패했습니다.")
                refreshRuntimeState()
            }
        }
    }

    private fun toggleModel() {
        if (answerModelLoaded) {
            LlmEngine.free()
            addSystemMessage("모델을 언로드했습니다.")
            refreshRuntimeState()
            return
        }
        setBusy(true, "모델 로드 중...")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                LlmEngine.loadModel(this@MainActivity, selectedSource)
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
                    val promptResult = buildModelPrompt(prompt, history) { chatLoadingMessage = it }
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
        onLoadingPhaseChanged: (String) -> Unit = {}
    ): KnowledgePromptResult {
        onLoadingPhaseChanged(getString(R.string.chat_loading_classifying))
        val routerResult = AgentRouter.route(this, prompt)
        if (routerResult == null) {
            val fullPrompt = if (history.isNotBlank()) "$history\n현재 질문: $prompt" else prompt
            return KnowledgePromptResult(fullPrompt, null)
        }
        return KnowledgePromptBuilder.buildAgentPromptResult(this, routerResult, prompt, history)
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
    }

    private fun refreshWalletState() {
        val repository = CardExpenseRepository(CardTransactionStore(this))
        val monthKey = currentMonthKey()
        walletMonthlySummary = repository.getMonthlySummary(monthKey)
        walletRecentTransactions = repository.findRecentTransactions(limit = 20)
        walletPermissionGranted = WalletNotificationPermissionManager.isGranted(this)
        walletNotificationLogs = WalletNotificationLogStore(this).loadAll()
    }

    private fun currentMonthKey(): String = YearMonth.now().toString()

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
            val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
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
            val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))

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
            val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))

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
