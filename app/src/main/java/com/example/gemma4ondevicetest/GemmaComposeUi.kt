package com.example.gemma4ondevicetest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightReport
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightCandidate
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightCandidateSource
import com.example.gemma4ondevicetest.wallet.CardExpenseMonthlyInsight
import com.example.gemma4ondevicetest.wallet.CardTransactionRecord
import com.example.gemma4ondevicetest.wallet.ExpenseCategoryBreakdown
import com.example.gemma4ondevicetest.wallet.MonthlyCardSummary
import com.example.gemma4ondevicetest.wallet.CardExpenseInsightHistoryReducer
import com.example.gemma4ondevicetest.wallet.BatteryGateStatus
import com.example.gemma4ondevicetest.wallet.SubscriptionAnalysisReport
import com.example.gemma4ondevicetest.wallet.SubscriptionAnalysisScheduler
import com.example.gemma4ondevicetest.wallet.TransactionStatus
import com.example.gemma4ondevicetest.usage.AppUsageAllowlistPolicy
import com.example.gemma4ondevicetest.usage.AppUsageSessionRecord
import com.example.gemma4ondevicetest.usage.AppUsageStatsSummary
import com.example.gemma4ondevicetest.usage.AppUsageTopApp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────── data models ───────────────────────

enum class AppScreen { HOME, CHAT, WALLET, SUBSCRIPTIONS, APP_USAGE, MODEL, STATUS, DOCUMENTS, DOCUMENT_VIEWER, SCHEDULE }

data class BusyUiState(
    val visible: Boolean = false,
    val message: String = "",
    val determinate: Boolean = false,
    val progress: Int = 0
)

data class SectionEditState(
    val index: Int,
    val previewText: String,
    val fullText: String,
    val toolId: String,
    val major: String,
    val middle: String,
    val minor: String
)

data class DocumentImportUiState(
    val fileName: String,
    val sections: List<SectionEditState>,
    val isSaving: Boolean = false
)

data class AppUiState(
    val currentScreen: AppScreen,
    val selectedModelLabel: String,
    val modelDescription: String,
    val answerModelLoaded: Boolean,
    val answerModelReady: Boolean,
    val sessions: List<ChatSession>,
    val activeSessionId: String,
    val busy: BusyUiState,
    val chatLoadingMessage: String?,
    val lastLoadInfo: String = "",
    val knowledgeTools: List<KnowledgeTool> = emptyList(),
    val documentImportState: DocumentImportUiState? = null,
    val userDocuments: List<UserDocument> = emptyList(),
    val viewingCategory: KnowledgeCategory? = null,
    val viewingDocumentText: String? = null,
    val scheduleUiState: com.example.gemma4ondevicetest.schedule.ScheduleUiState = com.example.gemma4ondevicetest.schedule.ScheduleUiState(),
    val walletMonthlySummary: MonthlyCardSummary = MonthlyCardSummary("", 0L, 0L, 0),
    val walletRecentTransactions: List<CardTransactionRecord> = emptyList(),
    val walletPermissionGranted: Boolean = false,
    val selectedTransaction: CardTransactionRecord? = null,
    val subscriptionReport: SubscriptionAnalysisReport = SubscriptionAnalysisReport(0L, 0L, "아직 분석 기록이 없습니다.", 0, emptyList()),
    val subscriptionBatteryStatus: BatteryGateStatus = BatteryGateStatus(isCharging = false, levelPercent = 0),
    val cardExpenseInsightReport: CardExpenseInsightReport = CardExpenseInsightReport("", 0L, 0L, 0, 0, "아직 분석 기록이 없습니다.", emptyList(), emptyList()),
    val cardExpenseInsightHistory: List<CardExpenseMonthlyInsight> = emptyList(),
    val cardExpenseInsightItems: List<CardExpenseInsightCandidate> = emptyList(),
    val cardExpenseInsightRunning: Boolean = false,
    val appUsagePermissionGranted: Boolean = false,
    val appUsageSummary: AppUsageStatsSummary = AppUsageStatsSummary(),
    val appUsageRecentSessions: List<AppUsageSessionRecord> = emptyList(),
    val appUsageTopApps: List<AppUsageTopApp> = emptyList()
)

// ─────────────────────── color tokens ───────────────────────

private val CPrimary       = Color(0xFF9C8FFF)
private val CPrimaryDeep   = Color(0xFF6A5FCC)
private val CBackground    = Color(0xFF0C0C18)
private val CSurface       = Color(0xFF141421)
private val CSurfaceHigh   = Color(0xFF1C1C2E)
private val CSurfaceVar    = Color(0xFF23233A)
private val COnSurface     = Color(0xFFEBE8F8)
private val CMuted         = Color(0xFF8C89AC)
private val COutline       = Color(0xFF353458)
private val CSecondary     = Color(0xFFBBB0FF)
private val CGreen         = Color(0xFF4ECBA0)
private val CAmber         = Color(0xFFFFB547)
private val CRed           = Color(0xFFFF6B6B)

private val GradientUser = Brush.linearGradient(
    colors = listOf(Color(0xFF6355CC), Color(0xFF9C8FFF)),
    start  = Offset(0f, 0f), end = Offset(200f, 200f)
)
private val GradientAccent = Brush.linearGradient(
    colors = listOf(CPrimaryDeep, CPrimary),
    start  = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, 0f)
)

// ─────────────────────── theme ───────────────────────

@Composable
private fun GemmaComposeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary               = CPrimary,
            onPrimary             = Color.White,
            primaryContainer      = Color(0xFF3D3688),
            onPrimaryContainer    = Color(0xFFD9D4FF),
            secondary             = CSecondary,
            onSecondary           = Color.White,
            background            = CBackground,
            onBackground          = COnSurface,
            surface               = CSurface,
            onSurface             = COnSurface,
            surfaceVariant        = CSurfaceHigh,
            onSurfaceVariant      = CMuted,
            surfaceContainerLow   = CSurfaceVar,
            surfaceContainerLowest = CBackground,
            outline               = COutline,
        ),
        content = content
    )
}

// ─────────────────────── root composable ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaApp(
    uiState: AppUiState,
    onOpenHome: () -> Unit,
    onOpenDrawerChat: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenDrawerModel: () -> Unit,
    onOpenDrawerStatus: () -> Unit,
    onOpenDrawerDocuments: () -> Unit,
    onNewGeneralChat: () -> Unit,
    onNewChatForTool: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onToggleModel: () -> Unit,
    onPickModel: () -> Unit,
    onDownloadGemma: () -> Unit,
    onPickDocument: () -> Unit,
    onSectionUpdated: (Int, SectionEditState) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onViewDocument: (KnowledgeCategory) -> Unit,
    onCloseDocument: () -> Unit,
    onOpenSchedule: () -> Unit = {},
    onScheduleRequestPermissions: () -> Unit = {},
    onScheduleRefresh: () -> Unit = {},
    onScheduleToggleNotification: (Boolean) -> Unit = {},
    onScheduleTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    onScheduleRunNow: () -> Unit = {},
    onWalletOpenPermissionSettings: () -> Unit = {},
    onWalletSelectTransaction: (CardTransactionRecord) -> Unit = {},
    onWalletDismissTransaction: () -> Unit = {},
    onAppUsageOpenPermissionSettings: () -> Unit = {},
    onAppUsageRunSync: () -> Unit = {},
    onAppUsageExportCsv: () -> Unit = {},
    onAppUsageClearLog: () -> Unit = {},
    onRunSubscriptionAnalysis: () -> Unit = {},
    onRunCardInsightAnalysis: () -> Unit = {},
    onReanalyzeCardInsightAnalysis: () -> Unit = {},
    onReanalyzeCardInsightItem: (CardExpenseInsightCandidate) -> Unit = {},
    onResetCardInsightAnalysis: () -> Unit = {},
    onEnterText: (String, String) -> Unit = { _, _ -> }
) {
    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    val activeSession = uiState.sessions.firstOrNull { it.id == uiState.activeSessionId }
        ?: uiState.sessions.firstOrNull()

    val isGenerating = uiState.chatLoadingMessage != null
    val showOverlay  = uiState.busy.visible && !isGenerating

    BackHandler(enabled = drawerState.isOpen || uiState.currentScreen != AppScreen.HOME) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            uiState.currentScreen == AppScreen.DOCUMENT_VIEWER -> onCloseDocument()
            uiState.currentScreen != AppScreen.HOME -> onOpenHome()
        }
    }

    GemmaComposeTheme {
        Box(Modifier.fillMaxSize().background(CBackground)) {
            ModalNavigationDrawer(
                drawerState   = drawerState,
                drawerContent = {
                    AppDrawer(
                        sessions           = uiState.sessions.sortedByDescending { it.updatedAt },
                        activeSessionId    = uiState.activeSessionId,
                        currentScreen      = uiState.currentScreen,
                        selectedModelLabel = uiState.selectedModelLabel,
                        answerModelLoaded  = uiState.answerModelLoaded,
                        knowledgeTools     = uiState.knowledgeTools,
                        activeKindName     = activeSession?.kind?.name ?: "",
                        onNewGeneralChat   = { onNewGeneralChat(); scope.launch { drawerState.close() } },
                        onNewChatForTool   = { id -> onNewChatForTool(id); scope.launch { drawerState.close() } },
                        onSelectSession    = { id -> onSelectSession(id); scope.launch { drawerState.close() } },
                        onDeleteSession    = onDeleteSession,
                        onOpenHome         = { onOpenHome(); scope.launch { drawerState.close() } },
                        onOpenChat         = { onOpenDrawerChat(); scope.launch { drawerState.close() } },
                        onOpenWallet       = { onOpenWallet(); scope.launch { drawerState.close() } },
                        onOpenSubscriptions = { onOpenSubscriptions(); scope.launch { drawerState.close() } },
                        onOpenAppUsage     = { onOpenAppUsage(); scope.launch { drawerState.close() } },
                        onOpenModel        = { onOpenDrawerModel(); scope.launch { drawerState.close() } },
                        onOpenStatus       = { onOpenDrawerStatus(); scope.launch { drawerState.close() } },
                        onOpenDocuments    = { onOpenDrawerDocuments(); scope.launch { drawerState.close() } },
                        onOpenSchedule     = { onOpenSchedule(); scope.launch { drawerState.close() } },
                        onViewDocument     = { cat -> onViewDocument(cat); scope.launch { drawerState.close() } },
                        onDeleteDocument   = onDeleteDocument
                    )
                }
            ) {
                Scaffold(
                    containerColor      = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        AppTopBar(
                            currentScreen     = uiState.currentScreen,
                            activeSession     = activeSession,
                            viewingCategory   = uiState.viewingCategory,
                            answerModelLoaded = uiState.answerModelLoaded,
                            answerModelReady  = uiState.answerModelReady,
                            isBusy            = uiState.busy.visible,
                            onOpenMenu        = { scope.launch { drawerState.open() } },
                            onToggleModel     = onToggleModel,
                            onCloseDocument   = onCloseDocument
                        )
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (uiState.currentScreen) {
                            AppScreen.HOME -> HomeScreen(
                                walletSummary = uiState.walletMonthlySummary,
                                walletPermissionGranted = uiState.walletPermissionGranted,
                                subscriptionReport = uiState.subscriptionReport,
                                cardInsightReport = uiState.cardExpenseInsightReport,
                                cardInsightItems = uiState.cardExpenseInsightItems,
                                scheduleUiState = uiState.scheduleUiState,
                                answerModelReady = uiState.answerModelReady,
                                appUsagePermissionGranted = uiState.appUsagePermissionGranted,
                                appUsageSummary = uiState.appUsageSummary,
                                onOpenWallet = onOpenWallet,
                                onOpenSubscriptions = onOpenSubscriptions,
                                onOpenSchedule = onOpenSchedule,
                                onOpenChat = onOpenDrawerChat,
                                onOpenAppUsage = onOpenAppUsage
                            )
                            AppScreen.CHAT -> ChatScreen(
                                messages           = activeSession?.messages.orEmpty(),
                                kind               = activeSession?.kind ?: ChatKind.GENERAL,
                                answerModelLoaded  = uiState.answerModelLoaded,
                                answerModelReady   = uiState.answerModelReady,
                                isBusy             = uiState.busy.visible,
                                isGenerating       = isGenerating,
                                chatLoadingMessage = uiState.chatLoadingMessage,
                                onToggleModel      = onToggleModel,
                                onSendPrompt       = onSendPrompt
                            )
                            AppScreen.WALLET -> WalletScreen(
                                summary = uiState.walletMonthlySummary,
                                transactions = uiState.walletRecentTransactions,
                                permissionGranted = uiState.walletPermissionGranted,
                                selectedTransaction = uiState.selectedTransaction,
                                insightReport = uiState.cardExpenseInsightReport,
                                insightHistory = uiState.cardExpenseInsightHistory,
                                insightItems = uiState.cardExpenseInsightItems,
                                insightRunning = uiState.cardExpenseInsightRunning,
                                onOpenPermissionSettings = onWalletOpenPermissionSettings,
                                onSelectTransaction = onWalletSelectTransaction,
                                onDismissTransaction = onWalletDismissTransaction,
                                onRunInsightAnalysis = onRunCardInsightAnalysis,
                                onReanalyzeInsightAnalysis = onReanalyzeCardInsightAnalysis,
                                onReanalyzeInsightItem = onReanalyzeCardInsightItem,
                                onResetInsightAnalysis = onResetCardInsightAnalysis
                            )
                            AppScreen.SUBSCRIPTIONS -> SubscriptionScreen(
                                report = uiState.subscriptionReport,
                                batteryStatus = uiState.subscriptionBatteryStatus,
                                onRunAnalysis = onRunSubscriptionAnalysis
                            )
                            AppScreen.APP_USAGE -> AppUsageScreen(
                                permissionGranted = uiState.appUsagePermissionGranted,
                                summary = uiState.appUsageSummary,
                                topApps = uiState.appUsageTopApps,
                                recentSessions = uiState.appUsageRecentSessions,
                                onOpenPermissionSettings = onAppUsageOpenPermissionSettings,
                                onRunSync = onAppUsageRunSync,
                                onExportCsv = onAppUsageExportCsv,
                                onClearLog = onAppUsageClearLog
                            )
                            AppScreen.MODEL -> ModelScreen(
                                uiState         = uiState,
                                onToggleModel   = onToggleModel,
                                onPickModel     = onPickModel,
                                onDownloadGemma = onDownloadGemma
                            )
                            AppScreen.STATUS -> StatusScreen(uiState = uiState, activeSession = activeSession)
                            AppScreen.DOCUMENTS -> DocumentScreen(
                                uiState          = uiState,
                                onPickDocument   = onPickDocument,
                                onSectionUpdated = onSectionUpdated,
                                onConfirmImport  = onConfirmImport,
                                onCancelImport   = onCancelImport,
                                onDeleteDocument = onDeleteDocument,
                                onEnterText      = onEnterText
                            )
                            AppScreen.DOCUMENT_VIEWER -> {
                                val cat = uiState.viewingCategory
                                if (cat != null) {
                                    DocumentViewerScreen(
                                        category = cat,
                                        text     = uiState.viewingDocumentText
                                    )
                                }
                            }
                            AppScreen.SCHEDULE -> Column(Modifier.fillMaxSize()) {
                                val financeNote = buildScheduleFinanceLine(
                                    subscriptionReport = uiState.subscriptionReport,
                                    cardInsightReport = uiState.cardExpenseInsightReport
                                )
                                if (financeNote.isNotBlank()) {
                                    ScheduleFinanceContextBar(text = financeNote)
                                }
                                Box(Modifier.weight(1f)) {
                                    com.example.gemma4ondevicetest.schedule.ScheduleScreen(
                                        uiState                  = uiState.scheduleUiState,
                                        modelLoaded              = uiState.answerModelLoaded,
                                        onRequestPermissions     = onScheduleRequestPermissions,
                                        onRefresh                = onScheduleRefresh,
                                        onToggleNotification     = onScheduleToggleNotification,
                                        onTimeChanged            = onScheduleTimeChanged,
                                        onRunNow                 = onScheduleRunNow
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showOverlay,
                enter   = fadeIn(tween(250)),
                exit    = fadeOut(tween(250))
            ) { GemmaLoadingOverlay(uiState.busy.message, uiState.busy.determinate, uiState.busy.progress) }
        }
    }
}

// ─────────────────────── top bar ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    currentScreen: AppScreen,
    activeSession: ChatSession?,
    viewingCategory: KnowledgeCategory?,
    answerModelLoaded: Boolean,
    answerModelReady: Boolean,
    isBusy: Boolean,
    onOpenMenu: () -> Unit,
    onToggleModel: () -> Unit,
    onCloseDocument: () -> Unit
) {
    val title = when (currentScreen) {
        AppScreen.HOME            -> "WalletMate"
        AppScreen.CHAT            -> activeSession?.title ?: "새 대화"
        AppScreen.WALLET          -> "카드 사용내역"
        AppScreen.SUBSCRIPTIONS   -> "정기결제 후보"
        AppScreen.APP_USAGE       -> "사용 패턴 로그"
        AppScreen.MODEL           -> "모델 관리"
        AppScreen.STATUS          -> "실행 상태"
        AppScreen.DOCUMENTS       -> "문서 관리"
        AppScreen.DOCUMENT_VIEWER -> viewingCategory?.label ?: "문서"
        AppScreen.SCHEDULE        -> "일주일 일정 요약"
    }
    val subtitle = when (currentScreen) {
        AppScreen.HOME            -> "온디바이스 금융 도우미"
        AppScreen.CHAT            -> "대화"
        AppScreen.WALLET          -> "삼성 Wallet 알림 기반 월간 소비 요약"
        AppScreen.SUBSCRIPTIONS   -> "충전 완료 시 AI 배치 분석"
        AppScreen.APP_USAGE       -> "Usage Access 기반 앱 사용 세션 DB"
        AppScreen.MODEL           -> "다운로드 · 로드 · 설정"
        AppScreen.STATUS          -> "현재 런타임 정보"
        AppScreen.DOCUMENTS       -> "지식 문서 추가 · 관리"
        AppScreen.DOCUMENT_VIEWER -> "참고 문서"
        AppScreen.SCHEDULE        -> "일주일 일정 · 매일 오전 8시 알림"
    }
    val isViewer = currentScreen == AppScreen.DOCUMENT_VIEWER
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor             = CSurface,
            titleContentColor          = COnSurface,
            navigationIconContentColor = COnSurface,
            actionIconContentColor     = COnSurface
        ),
        navigationIcon = {
            if (isViewer) {
                IconButton(onClick = onCloseDocument) { Icon(Icons.Outlined.Close, "닫기") }
            } else {
                IconButton(onClick = onOpenMenu) { Icon(Icons.Outlined.Menu, "메뉴") }
            }
        },
        title = {
            Column {
                Text(
                    title,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CMuted)
            }
        },
        actions = {
            if (currentScreen == AppScreen.CHAT) {
                ModelStatusPill(loaded = answerModelLoaded, ready = answerModelReady, busy = isBusy, onToggle = onToggleModel)
                Spacer(Modifier.width(8.dp))
            }
        }
    )
}

@Composable
private fun ModelStatusPill(loaded: Boolean, ready: Boolean, busy: Boolean, onToggle: () -> Unit) {
    val containerColor = when { loaded -> CGreen.copy(0.15f); busy -> CAmber.copy(0.15f); else -> CSurfaceHigh }
    val contentColor   = when { loaded -> CGreen;             busy -> CAmber;             else -> CMuted }
    Surface(
        onClick  = { if (ready && !busy) onToggle() },
        shape    = RoundedCornerShape(20.dp),
        color    = containerColor,
        enabled  = ready && !busy
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (busy && !loaded) {
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = CAmber)
            } else {
                Box(Modifier.size(8.dp).background(contentColor, CircleShape))
            }
            Text(
                when { busy && !loaded -> "로드 중"; loaded -> "준비됨"; ready -> "탭하여 로드"; else -> "모델 없음" },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

// ─────────────────────── loading overlay ───────────────────────

@Composable
private fun GemmaLoadingOverlay(message: String, determinate: Boolean, progress: Int) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE60C0C18)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 48.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(24.dp)
        ) {
            PulsingIcon()
            Text(message, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = COnSurface, textAlign = TextAlign.Center)
            if (determinate) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress  = { progress / 100f },
                        modifier  = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color     = CPrimary, trackColor = CSurfaceHigh, strokeCap = StrokeCap.Round
                    )
                    Text("$progress%", style = MaterialTheme.typography.labelMedium, color = CMuted)
                }
            } else {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = CPrimary, trackColor = CSurfaceHigh, strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun PulsingIcon() {
    val t1 = rememberInfiniteTransition("r1")
    val t2 = rememberInfiniteTransition("r2")
    val ring1Scale by t1.animateFloat(0.4f, 1.4f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), "r1s")
    val ring1Alpha by t1.animateFloat(0.55f, 0f,  infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), "r1a")
    val ring2Scale by t2.animateFloat(0.4f, 1.4f, infiniteRepeatable(tween(1600, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart), "r2s")
    val ring2Alpha by t2.animateFloat(0.55f, 0f,  infiniteRepeatable(tween(1600, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Restart), "r2a")
    val iconT = rememberInfiniteTransition("ir")
    val iconRot by iconT.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), "ir")
    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(100.dp).scale(ring1Scale).alpha(ring1Alpha).border(1.5.dp, CPrimary, CircleShape))
        Box(Modifier.size(100.dp).scale(ring2Scale).alpha(ring2Alpha).border(1.5.dp, CPrimary, CircleShape))
        Box(Modifier.size(70.dp).background(GradientAccent, CircleShape), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_walletmate_scene),
                contentDescription = null,
                modifier = Modifier.size(54.dp).rotate(iconRot)
            )
        }
    }
}

// ─────────────────────── drawer ───────────────────────

@Composable
private fun AppDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String,
    currentScreen: AppScreen,
    selectedModelLabel: String,
    answerModelLoaded: Boolean,
    knowledgeTools: List<KnowledgeTool>,
    activeKindName: String,
    onNewGeneralChat: () -> Unit,
    onNewChatForTool: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenHome: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenSchedule: () -> Unit,
    onViewDocument: (KnowledgeCategory) -> Unit,
    onDeleteDocument: (String) -> Unit
) {
    var expandedToolIds by remember { mutableStateOf(emptySet<String>()) }

    ModalDrawerSheet(
        Modifier.width(300.dp),
        drawerContainerColor = CSurface,
        drawerContentColor   = COnSurface
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── header ──
            DrawerHeader(selectedModelLabel, answerModelLoaded)

            // ── scrollable body (single LazyColumn — expands without squishing sessions) ──
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "header_actions") {
                    Button(
                        onClick = onNewGeneralChat,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(GradientAccent, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), Color.White)
                                Text("새 대화 시작", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    DrawerSectionLabel("빠른 이동")
                    Spacer(Modifier.height(4.dp))
                    DrawerChannelRow(
                        icon     = Icons.Outlined.Home,
                        label    = "홈",
                        selected = currentScreen == AppScreen.HOME,
                        onClick  = onOpenHome
                    )
                    DrawerSectionLabel("대화 채널")
                    Spacer(Modifier.height(4.dp))
                    DrawerChannelRow(
                        icon     = Icons.Outlined.AutoAwesome,
                        label    = "일반 대화",
                        selected = activeKindName == ChatKind.GENERAL.name && currentScreen == AppScreen.CHAT,
                        onClick  = onOpenChat
                    )
                    DrawerChannelRow(
                        icon     = Icons.Outlined.CreditCard,
                        label    = "카드 사용내역",
                        selected = currentScreen == AppScreen.WALLET,
                        onClick  = onOpenWallet
                    )
                    DrawerChannelRow(
                        icon     = Icons.Outlined.Autorenew,
                        label    = "정기결제 후보",
                        selected = currentScreen == AppScreen.SUBSCRIPTIONS,
                        onClick  = onOpenSubscriptions
                    )
                    DrawerChannelRow(
                        icon     = Icons.Outlined.Timeline,
                        label    = "사용 패턴 로그",
                        selected = currentScreen == AppScreen.APP_USAGE,
                        onClick  = onOpenAppUsage
                    )
                }

                if (knowledgeTools.isNotEmpty()) {
                    item(key = "tools_header") {
                        Spacer(Modifier.height(10.dp))
                        DrawerSectionLabel("사용 가능한 지식 문서")
                        Spacer(Modifier.height(4.dp))
                    }
                    items(knowledgeTools, key = { "tool_${it.id}" }) { tool ->
                        val isExpanded = tool.id in expandedToolIds
                        KnowledgeToolItem(
                            tool             = tool,
                            expanded         = isExpanded,
                            onToggle         = {
                                expandedToolIds = if (isExpanded) expandedToolIds - tool.id else expandedToolIds + tool.id
                            },
                            onViewDocument   = onViewDocument,
                            onDeleteDocument = onDeleteDocument
                        )
                    }
                }

                item(key = "sessions_header") {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = COutline, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))
                    DrawerSectionLabel("최근 대화")
                    Spacer(Modifier.height(4.dp))
                }

                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session  = session,
                        active   = session.id == activeSessionId,
                        onClick  = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }

            // ── bottom toolbar ──
            HorizontalDivider(color = COutline, thickness = 0.5.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DrawerBottomItem(Icons.Outlined.FolderOpen,     "문서",  currentScreen == AppScreen.DOCUMENTS, onOpenDocuments)
                DrawerBottomItem(Icons.Outlined.ModelTraining,  "모델",  currentScreen == AppScreen.MODEL,     onOpenModel)
                DrawerBottomItem(Icons.Outlined.Info,           "상태",  currentScreen == AppScreen.STATUS,    onOpenStatus)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrawerHeader(modelLabel: String, modelLoaded: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1B38), CSurface)))
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(CPrimary.copy(0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = CPrimary, modifier = Modifier.size(20.dp))
            }
            Text("WalletMate", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
            Text(modelLabel, style = MaterialTheme.typography.bodySmall, color = CMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(if (modelLoaded) "모델 준비됨" else "모델 미로드", modelLoaded)
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    Box(
        Modifier
            .background(if (active) CGreen.copy(0.15f) else CMuted.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) CGreen else CMuted)
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text,
        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        color    = CMuted.copy(0.7f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun DrawerChannelRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CPrimary.copy(0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) CPrimary else CMuted)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) COnSurface else CMuted,
            modifier = Modifier.weight(1f)
        )
        if (selected) Box(Modifier.size(6.dp).background(CPrimary, CircleShape))
    }
}

@Composable
private fun KnowledgeToolItem(
    tool: KnowledgeTool,
    expanded: Boolean,
    onToggle: () -> Unit,
    onViewDocument: (KnowledgeCategory) -> Unit,
    onDeleteDocument: (String) -> Unit
) {
    val icon = toolIcon(tool.id)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                Modifier.size(18.dp).clickable(onClick = onToggle),
                tint = CMuted
            )
            Icon(icon, null, Modifier.size(16.dp), tint = CMuted)
            Text(
                tool.displayName,
                style    = MaterialTheme.typography.bodyMedium,
                color    = CMuted,
                modifier = Modifier.weight(1f)
            )
        }

        if (expanded) {
            val majors = tool.categories.map { it.major }.distinct()
            majors.forEach { major ->
                val cats = tool.categories.filter { it.major == major }
                Column(Modifier.padding(start = 36.dp)) {
                    Text(
                        major,
                        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color    = CMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    cats.forEach { cat ->
                        val leafLabel = if (cat.minor.isNotBlank()) "${cat.middle} · ${cat.minor}" else cat.middle
                        val isUserDoc = cat.source == "user_upload"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onViewDocument(cat) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(4.dp).background(CPrimary.copy(0.5f), CircleShape))
                            Text(leafLabel, style = MaterialTheme.typography.labelSmall, color = COnSurface.copy(0.7f), modifier = Modifier.weight(1f))
                            if (isUserDoc) {
                                Box(
                                    Modifier
                                        .background(CAmber.copy(0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("사용자", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = CAmber)
                                }
                                IconButton(
                                    onClick  = { cat.filePath?.let { onDeleteDocument(it) } },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(13.dp),
                                        tint     = CRed.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DrawerBottomItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CPrimary.copy(0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (selected) CPrimary else CMuted)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) CPrimary else CMuted)
    }
}

@Composable
private fun SessionItem(session: ChatSession, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) CPrimary.copy(0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Box(Modifier.width(3.dp).height(24.dp).background(CPrimary, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style    = MaterialTheme.typography.bodySmall.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                color    = if (active) COnSurface else CMuted
            )
            Text(session.kind.label, style = MaterialTheme.typography.labelSmall, color = CMuted.copy(0.7f))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.DeleteOutline, "삭제", Modifier.size(15.dp), tint = CMuted.copy(0.5f))
        }
    }
}

@Composable
private fun HomeScreen(
    walletSummary: MonthlyCardSummary,
    walletPermissionGranted: Boolean,
    subscriptionReport: SubscriptionAnalysisReport,
    cardInsightReport: CardExpenseInsightReport,
    cardInsightItems: List<CardExpenseInsightCandidate>,
    scheduleUiState: com.example.gemma4ondevicetest.schedule.ScheduleUiState,
    answerModelReady: Boolean,
    appUsagePermissionGranted: Boolean,
    appUsageSummary: AppUsageStatsSummary,
    onOpenWallet: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenAppUsage: () -> Unit
) {
    val walletDisplayAmount = cardExpenseDisplayAmount(
        summary = walletSummary,
        insightReport = cardInsightReport,
        insightItems = cardInsightItems
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF233A6B), Color(0xFF0F766E), Color(0xFF0C1324))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "이번 달 소비, 일정, 대화를 한곳에서 관리합니다",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                if (walletPermissionGranted) {
                                    "${walletSummary.monthKey} 기준 ${formatWon(walletDisplayAmount)} 사용, ${cardInsightItems.size}건이 분석 기준으로 수집되어 있습니다."
                                } else {
                                    "삼성 Wallet 알림 접근을 허용하면 월간 카드 사용내역이 자동으로 쌓입니다."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.84f)
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.ic_walletmate_scene),
                            contentDescription = null,
                            modifier = Modifier.size(104.dp).alpha(0.96f)
                        )
                    }
                }
            }
        }

        item {
            val topCategoryLine = if (cardInsightReport.categoryBreakdowns.isNotEmpty()) {
                val top = cardInsightReport.categoryBreakdowns.first()
                "${top.category} ${top.percentageOfTotal.toInt()}% · ${cardInsightReport.analyzedCandidateCount}건 분석됨"
            } else if (cardInsightReport.pendingCount > 0) {
                "분석 대기 ${cardInsightReport.pendingCount}건"
            } else {
                null
            }
            HomeNavigationCard(
                title = "카드 사용내역",
                body = if (walletPermissionGranted) {
                    topCategoryLine ?: "월간 합계와 최근 거래를 확인합니다."
                } else {
                    "알림 접근 권한을 연결하고 카드 승인 내역을 확인합니다."
                },
                badge = if (walletPermissionGranted) formatWon(walletDisplayAmount) else "권한 필요",
                icon = Icons.Outlined.CreditCard,
                accent = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C))),
                onClick = onOpenWallet
            )
        }

        item {
            HomeNavigationCard(
                title = "정기결제 후보",
                body = if (subscriptionReport.candidates.isNotEmpty()) {
                    "AI가 추린 정기결제 후보 ${subscriptionReport.candidates.size}건과 놓치기 쉬운 결제 이벤트를 확인합니다."
                } else {
                    "금융 알림을 모아 충전 완료 시 정기결제 후보를 분석합니다."
                },
                badge = if (subscriptionReport.pendingCount > 0) "대기 ${subscriptionReport.pendingCount}건" else "후보 ${subscriptionReport.candidates.size}건",
                icon = Icons.Outlined.Autorenew,
                accent = Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0E7490))),
                onClick = onOpenSubscriptions
            )
        }

        item {
            HomeNavigationCard(
                title = "채팅",
                body = if (answerModelReady) {
                    "일반 질문이나 문서 기반 질문을 바로 시작합니다."
                } else {
                    "모델을 준비한 뒤 대화를 시작합니다."
                },
                badge = if (answerModelReady) "대화 시작" else "모델 준비 필요",
                icon = Icons.Outlined.Chat,
                accent = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
                onClick = onOpenChat
            )
        }

        item {
            HomeNavigationCard(
                title = "사용 패턴 로그",
                body = if (appUsagePermissionGranted) {
                    "최근 ${appUsageSummary.totalSessions}개 세션이 DB에 저장되어 있습니다. 추후 개인화 학습용 로그를 확인합니다."
                } else {
                    "Usage Access 권한을 연결해 기기 전체 앱 사용 세션을 수집합니다."
                },
                badge = if (appUsagePermissionGranted) "${appUsageSummary.distinctPackageCount}개 앱" else "권한 필요",
                icon = Icons.Outlined.Timeline,
                accent = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4338CA))),
                onClick = onOpenAppUsage
            )
        }

        item {
            HomeNavigationCard(
                title = "일정",
                body = when {
                    !scheduleUiState.calendarPermission -> "캘린더 권한을 연결하고 다가오는 일정을 확인합니다."
                    scheduleUiState.entries.isNotEmpty() -> "일정 화면에서 다가오는 일정 ${scheduleUiState.entries.size}건을 확인합니다."
                    else -> "일정 화면에서 캘린더를 불러오고 주간 일정을 확인합니다."
                },
                badge = when {
                    !scheduleUiState.calendarPermission -> "권한 필요"
                    scheduleUiState.entries.isNotEmpty() -> "${scheduleUiState.entries.size}건"
                    else -> "불러오기 전"
                },
                icon = Icons.Outlined.CalendarMonth,
                accent = Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0F766E))),
                onClick = onOpenSchedule
            )
        }
    }
}

@Composable
private fun ScheduleFinanceContextBar(text: String) {
    Surface(color = CAmber.copy(0.08f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Lightbulb, null, Modifier.size(14.dp), tint = CAmber)
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = CMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeNavigationCard(
    title: String,
    body: String,
    badge: String,
    icon: ImageVector,
    accent: Brush,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = CSurfaceHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(accent, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = CMuted)
                Text(badge, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = CPrimary)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = CMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletScreen(
    summary: MonthlyCardSummary,
    transactions: List<CardTransactionRecord>,
    permissionGranted: Boolean,
    selectedTransaction: CardTransactionRecord?,
    insightReport: CardExpenseInsightReport,
    insightHistory: List<CardExpenseMonthlyInsight>,
    insightItems: List<CardExpenseInsightCandidate>,
    insightRunning: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onSelectTransaction: (CardTransactionRecord) -> Unit,
    onDismissTransaction: () -> Unit,
    onRunInsightAnalysis: () -> Unit = {},
    onReanalyzeInsightAnalysis: () -> Unit = {},
    onReanalyzeInsightItem: (CardExpenseInsightCandidate) -> Unit = {},
    onResetInsightAnalysis: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    WalletTransactionsTab(
        summary = summary,
        transactions = transactions,
        permissionGranted = permissionGranted,
        insightReport = insightReport,
        insightHistory = insightHistory,
        insightItems = insightItems,
        insightRunning = insightRunning,
        onOpenPermissionSettings = onOpenPermissionSettings,
        onSelectTransaction = onSelectTransaction,
        onRunAnalysis = onRunInsightAnalysis,
        onReanalyze = onReanalyzeInsightAnalysis,
        onReanalyzeItem = onReanalyzeInsightItem,
        onResetAnalysis = onResetInsightAnalysis
    )

    if (selectedTransaction != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissTransaction,
            sheetState = sheetState,
            containerColor = CSurfaceHigh,
            contentColor = COnSurface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(COutline, RoundedCornerShape(2.dp))
                )
            }
        ) {
            TransactionDetailSheet(transaction = selectedTransaction)
        }
    }
}

@Composable
private fun SubscriptionScreen(
    report: SubscriptionAnalysisReport,
    batteryStatus: BatteryGateStatus,
    onRunAnalysis: () -> Unit
) {
    val remainingCooldown = SubscriptionAnalysisScheduler.remainingCooldownMillis(report.lastCompletedAt)
    val cooldownActive = remainingCooldown > 0L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = CSurfaceHigh) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("분석 게이트", style = MaterialTheme.typography.labelLarge, color = CMuted)
                    Text(
                        when {
                            !batteryStatus.isEligible -> "충전 중이며 100%일 때만 AI 분석이 실행됩니다."
                            cooldownActive -> "최근 분석 완료 후 24시간이 지나야 다시 분석합니다."
                            else -> "충전 중 100% 상태로 분석 가능합니다."
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = COnSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryPill(if (batteryStatus.isCharging) "충전 중" else "충전 아님")
                        SummaryPill("배터리 ${batteryStatus.levelPercent}%")
                        SummaryPill("대기 ${report.pendingCount}건")
                        if (cooldownActive) {
                            SummaryPill("재분석 ${SubscriptionAnalysisScheduler.formatRemainingCooldown(remainingCooldown)} 후")
                        }
                    }
                    Text(report.statusMessage, style = MaterialTheme.typography.bodyMedium, color = CMuted)
                    Button(
                        onClick = onRunAnalysis,
                        enabled = batteryStatus.isEligible && !cooldownActive,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("지금 분석")
                    }
                }
            }
        }

        item {
            Text("정기결제 후보", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
        }

        if (report.candidates.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("아직 추출된 후보가 없습니다.", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
                        Text("지원 금융 알림이 쌓인 뒤 충전 완료 상태에서 분석하면 후보가 여기에 표시됩니다.", style = MaterialTheme.typography.bodyMedium, color = CMuted)
                    }
                }
            }
        } else {
            items(report.candidates, key = { it.id }) { candidate ->
                Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                candidate.serviceName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = COnSurface
                            )
                            if (candidate.amountText.isNotBlank()) {
                                Text(candidate.amountText, style = MaterialTheme.typography.labelLarge, color = CPrimary)
                            }
                        }
                        Text(candidate.reason.ifBlank { "반복 결제 가능성이 있는 알림입니다." }, style = MaterialTheme.typography.bodyMedium, color = CMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryPill(candidate.sourcePackage.substringAfterLast('.'))
                            if (candidate.lastSeenAt > 0) {
                                SummaryPill(formatTransactionTimestamp(candidate.lastSeenAt))
                            }
                            if (candidate.missableEvent) {
                                SummaryPill("놓치기 쉬움")
                            }
                        }
                        if (candidate.missableReason.isNotBlank()) {
                            Text(candidate.missableReason, style = MaterialTheme.typography.labelMedium, color = CAmber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletTransactionsTab(
    summary: MonthlyCardSummary,
    transactions: List<CardTransactionRecord>,
    permissionGranted: Boolean,
    insightReport: CardExpenseInsightReport,
    insightHistory: List<CardExpenseMonthlyInsight>,
    insightItems: List<CardExpenseInsightCandidate>,
    insightRunning: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onSelectTransaction: (CardTransactionRecord) -> Unit,
    onRunAnalysis: () -> Unit,
    onReanalyze: () -> Unit = {},
    onReanalyzeItem: (CardExpenseInsightCandidate) -> Unit = {},
    onResetAnalysis: () -> Unit = {}
) {
    val displayedAmount = cardExpenseDisplayAmount(summary, insightReport, insightItems)
    var reportExpanded by rememberSaveable { mutableStateOf(false) }
    var analysisItemsExpanded by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetAnalysis()
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            },
            title = { Text("인사이트 초기화") },
            text = {
                Text(
                    "현재 달 분석 결과와 카테고리, 그리고 저장된 월별 기록까지 모두 삭제합니다. " +
                        "다시 분석하려면 '지금 분석' 또는 '전체 재분석'을 사용하세요."
                )
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = CSurfaceHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("이번 달 누적", style = MaterialTheme.typography.labelLarge, color = CMuted)
                            Text(
                                formatWon(displayedAmount),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = COnSurface
                            )
                        }
                        TextButton(onClick = { reportExpanded = !reportExpanded }) {
                            Text(if (reportExpanded) "분석 리포트 닫기" else "분석 리포트 보기")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (reportExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                null,
                                Modifier.size(18.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryPill("분석 기준 ${formatWon(displayedAmount)}")
                        SummaryPill("분석 후보 ${insightItems.size}건")
                        if (summary.grossCancelled > 0L) SummaryPill("취소 ${formatWon(summary.grossCancelled)}")
                    }
                }
            }
        }

        if (reportExpanded) {
            walletInsightReportItems(
                report = insightReport,
                history = insightHistory,
                insightItems = insightItems,
                insightRunning = insightRunning,
                analysisItemsExpanded = analysisItemsExpanded,
                onToggleAnalysisItems = { analysisItemsExpanded = !analysisItemsExpanded },
                onRunAnalysis = onRunAnalysis,
                onReanalyze = onReanalyze,
                onReanalyzeItem = onReanalyzeItem,
                onResetAnalysisRequest = { showResetConfirm = true }
            )
        }

        if (!permissionGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CAmber.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("알림 접근이 필요합니다", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
                        Text("삼성 Wallet 알림을 읽어야 카드 사용내역을 자동으로 저장할 수 있습니다.", style = MaterialTheme.typography.bodyMedium, color = CMuted)
                        OutlinedButton(onClick = onOpenPermissionSettings) {
                            Icon(Icons.Outlined.Notifications, null)
                            Spacer(Modifier.width(8.dp))
                            Text("권한 설정 열기")
                        }
                    }
                }
            }
        }

        item {
            Text("최근 거래", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
        }

        if (transactions.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
                    Text(
                        if (permissionGranted) "아직 저장된 카드 거래가 없습니다." else "권한을 연결하면 거래가 여기에 표시됩니다.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CMuted
                    )
                }
            }
        } else {
            items(transactions, key = { it.id }) { transaction ->
                WalletTransactionItem(
                    transaction = transaction,
                    onClick = { onSelectTransaction(transaction) }
                )
            }
        }
    }
}

// ─────────────────────── wallet insight tab ───────────────────────

private val CATEGORY_COLORS = mapOf(
    "식비"   to Color(0xFFF59E0B),
    "교통"   to Color(0xFF3B82F6),
    "쇼핑"   to Color(0xFFA855F7),
    "구독"   to Color(0xFF14B8A6),
    "주거/공과금" to Color(0xFF0EA5E9),
    "생활"   to Color(0xFF22C55E),
    "의료"   to Color(0xFFEF4444),
    "교육"   to Color(0xFF84CC16),
    "통신"   to Color(0xFF06B6D4),
    "문화/여가" to Color(0xFFEC4899),
    "금융"   to Color(0xFF6366F1),
    "기타"   to Color(0xFF6B7280)
)

private fun categoryColor(category: String): Color =
    CATEGORY_COLORS[category] ?: Color(0xFF6B7280)

@Composable
private fun WalletInsightTab(
    report: CardExpenseInsightReport,
    history: List<CardExpenseMonthlyInsight>,
    insightItems: List<CardExpenseInsightCandidate>,
    insightRunning: Boolean,
    onRunAnalysis: () -> Unit,
    onReanalyze: () -> Unit = {},
    onReanalyzeItem: (CardExpenseInsightCandidate) -> Unit = {},
    onResetAnalysis: () -> Unit = {}
) {
    var analysisItemsExpanded by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetAnalysis()
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            },
            title = { Text("인사이트 초기화") },
            text = {
                Text(
                    "현재 달 분석 결과와 카테고리, 그리고 저장된 월별 기록까지 모두 삭제합니다. " +
                        "다시 분석하려면 '지금 분석' 또는 '전체 재분석'을 사용하세요."
                )
            }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        walletInsightReportItems(
            report = report,
            history = history,
            insightItems = insightItems,
            insightRunning = insightRunning,
            analysisItemsExpanded = analysisItemsExpanded,
            onToggleAnalysisItems = { analysisItemsExpanded = !analysisItemsExpanded },
            onRunAnalysis = onRunAnalysis,
            onReanalyze = onReanalyze,
            onReanalyzeItem = onReanalyzeItem,
            onResetAnalysisRequest = { showResetConfirm = true }
        )
    }
}

private fun LazyListScope.walletInsightReportItems(
    report: CardExpenseInsightReport,
    history: List<CardExpenseMonthlyInsight>,
    insightItems: List<CardExpenseInsightCandidate>,
    insightRunning: Boolean,
    analysisItemsExpanded: Boolean,
    onToggleAnalysisItems: () -> Unit,
    onRunAnalysis: () -> Unit,
    onReanalyze: () -> Unit = {},
    onReanalyzeItem: (CardExpenseInsightCandidate) -> Unit = {},
    onResetAnalysisRequest: () -> Unit = {}
) {
    val previousMonth = CardExpenseInsightHistoryReducer.findPreviousMonth(history, report.monthKey)
    val displayPendingCount = insightItems.count { !it.isAnalyzed }.coerceAtLeast(report.pendingCount)
    val analyzedItems = insightItems.filter { it.isAnalyzed }

    item {
        Surface(shape = RoundedCornerShape(28.dp), color = CSurfaceHigh) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("AI 카테고리 분석", style = MaterialTheme.typography.labelLarge, color = CMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (insightRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CPrimary
                        )
                    }
                    Text(
                        report.statusMessage,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = COnSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("대기 ${displayPendingCount}건")
                    if (report.analyzedCandidateCount > 0) {
                        SummaryPill("분석 ${report.analyzedCandidateCount}건")
                    }
                    if (report.lastCompletedAt > 0L) {
                        SummaryPill(formatTransactionTimestamp(report.lastCompletedAt))
                    }
                    if (report.generatedAt > 0L) {
                        SummaryPill("갱신 ${formatTransactionTimestamp(report.generatedAt)}")
                    }
                }
                Text(
                    if (insightRunning) {
                        "현재 단계가 바뀌면 이 영역이 자동으로 갱신됩니다."
                    } else {
                        "마지막 상태와 분석된 항목은 아래 분석 내역에서 확인할 수 있습니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRunAnalysis,
                        enabled = insightItems.any { !it.isAnalyzed } && !insightRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("지금 분석")
                    }
                    OutlinedButton(
                        onClick = onReanalyze,
                        enabled = insightItems.isNotEmpty() && !insightRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("전체 재분석")
                    }
                }
                OutlinedButton(
                    onClick = onResetAnalysisRequest,
                    enabled = !insightRunning,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("초기화")
                }
                Text(
                    "지금 분석: 대기 중인 내역만 분석 · 전체 재분석: 모든 내역을 처음부터 다시 분류 · 초기화: 분석/월별 기록 전체 삭제",
                    style = MaterialTheme.typography.labelSmall,
                    color = CMuted
                )
            }
        }
    }

    if (report.totalAmount > 0L || previousMonth != null) {
        item {
            MonthlyInsightTrendCard(
                currentReport = report,
                previousMonth = previousMonth
            )
        }
    }

    if (report.categoryBreakdowns.isNotEmpty()) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "카테고리별 비율",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = COnSurface
                    )
                    CategorySegmentBar(breakdowns = report.categoryBreakdowns)
                    Spacer(Modifier.height(2.dp))
                    CategoryLegendGrid(breakdowns = report.categoryBreakdowns)
                }
            }
        }
    }

    if (report.categoryBreakdowns.isNotEmpty()) {
        item {
            Text(
                "카테고리 상세",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = COnSurface
            )
        }
        items(report.categoryBreakdowns, key = { it.category }) { bd ->
            CategoryBreakdownCard(
                bd = bd,
                items = analyzedItems.filter { it.category == bd.category }
            )
        }
    }

    if (history.isNotEmpty()) {
        item {
            Text(
                "월별 변화",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = COnSurface
            )
        }
        items(history.take(6), key = { it.monthKey }) { historyItem ->
            MonthlyHistoryCard(historyItem)
        }
    }

    if (report.categoryBreakdowns.isEmpty() && displayPendingCount == 0) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("아직 카테고리 분석 결과가 없습니다.", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
                    Text("지원 금융 알림이 쌓이면 자동으로 카테고리 분석이 예약됩니다.", style = MaterialTheme.typography.bodyMedium, color = CMuted)
                }
            }
        }
    }

    if (insightItems.isNotEmpty()) {
        item {
            CardInsightAnalysisHistorySection(
                items = insightItems,
                expanded = analysisItemsExpanded,
                onToggle = onToggleAnalysisItems
            )
        }
        if (analysisItemsExpanded) {
            items(insightItems.take(30), key = { it.id }) { item ->
                CardInsightAnalysisItemRow(
                    item = item,
                    analysisRunning = insightRunning,
                    onReanalyze = { onReanalyzeItem(item) }
                )
            }
        }
    }
}

@Composable
private fun CardInsightAnalysisHistorySection(
    items: List<CardExpenseInsightCandidate>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val pendingCount = items.count { !it.isAnalyzed }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = CSurfaceHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ReceiptLong, null, tint = CMuted, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "분석 내역",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = COnSurface
                )
                Text(
                    "총 ${items.size}건 · 대기 ${pendingCount}건 · 분석 ${items.size - pendingCount}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = CMuted
                )
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                null,
                tint = CMuted
            )
        }
    }
}

@Composable
private fun CardInsightAnalysisItemRow(
    item: CardExpenseInsightCandidate,
    analysisRunning: Boolean = false,
    onReanalyze: () -> Unit = {}
) {
    val analyzed = item.isAnalyzed
    val statusColor = if (analyzed) CGreen else CAmber
    Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(statusColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (analyzed) Icons.Outlined.CheckCircle else Icons.Outlined.Pending,
                    null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title.ifBlank { item.merchantName.ifBlank { "카드 내역" } },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = COnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryPill(if (analyzed) "분석됨" else "대기")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill(
                        when (item.source) {
                            CardExpenseInsightCandidateSource.INBOX -> "알림 후보"
                            CardExpenseInsightCandidateSource.TRANSACTION -> "거래 내역"
                        }
                    )
                    if (item.amount > 0L) SummaryPill(formatWon(item.amount))
                    item.category?.let { SummaryPill(it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTransactionTimestamp(item.postedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = CMuted
                    )
                    TextButton(
                        onClick = onReanalyze,
                        enabled = !analysisRunning,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("재분석", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyInsightTrendCard(
    currentReport: CardExpenseInsightReport,
    previousMonth: CardExpenseMonthlyInsight?
) {
    val deltaAmount = previousMonth?.let { currentReport.totalAmount - it.totalAmount }
    val deltaCount = previousMonth?.let { currentReport.totalCount - it.totalCount }
    val topCategory = currentReport.categoryBreakdowns.firstOrNull()?.category

    Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "월별 비교",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = COnSurface
            )
            Text(
                "${currentReport.monthKey} 기준 ${formatWon(currentReport.totalAmount)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = COnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("후보 ${currentReport.analyzedCandidateCount}건")
                if (topCategory != null) SummaryPill("대표 $topCategory")
                if (deltaAmount != null) {
                    SummaryPill("전월 대비 ${formatSignedWon(deltaAmount)}")
                }
                if (deltaCount != null) {
                    SummaryPill("건수 ${formatSignedCount(deltaCount)}")
                }
            }
            if (previousMonth != null) {
                Text(
                    "지난달 ${previousMonth.monthKey} ${formatWon(previousMonth.totalAmount)} · ${previousMonth.totalCount}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = CMuted
                )
            }
        }
    }
}

@Composable
private fun MonthlyHistoryCard(item: CardExpenseMonthlyInsight) {
    val topCategory = item.categoryBreakdowns.firstOrNull()
    Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.monthKey,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = COnSurface
                )
                Text(
                    formatWon(item.totalAmount),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = CPrimary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("후보 ${item.analyzedCandidateCount}건")
                SummaryPill("건수 ${item.totalCount}건")
                if (topCategory != null) {
                    SummaryPill("${topCategory.category} ${topCategory.percentageOfTotal.toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun CategorySegmentBar(breakdowns: List<ExpenseCategoryBreakdown>) {
    val total = breakdowns.sumOf { it.totalAmount }.toFloat()
    if (total <= 0f) {
        // Percentage-only mode when amounts are 0
        val count = breakdowns.size.toFloat().coerceAtLeast(1f)
        Row(
            modifier = Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))
        ) {
            breakdowns.forEach { bd ->
                Box(
                    modifier = Modifier.fillMaxHeight().weight(1f / count)
                        .background(categoryColor(bd.category))
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))
    ) {
        breakdowns.forEach { bd ->
            val fraction = (bd.totalAmount.toFloat() / total).coerceAtLeast(0.02f)
            Box(
                modifier = Modifier.fillMaxHeight().weight(fraction)
                    .background(categoryColor(bd.category))
            )
        }
    }
}

@Composable
private fun CategoryLegendGrid(breakdowns: List<ExpenseCategoryBreakdown>) {
    val rows = breakdowns.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { bd ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(10.dp).background(categoryColor(bd.category), RoundedCornerShape(3.dp))
                        )
                        Text(
                            "${bd.category} ${bd.percentageOfTotal.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = COnSurface,
                            maxLines = 1
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    bd: ExpenseCategoryBreakdown,
    items: List<CardExpenseInsightCandidate> = emptyList()
) {
    val color = categoryColor(bd.category)
    var expanded by rememberSaveable(bd.category) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = CSurfaceHigh) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
                    Text(
                        bd.category,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = COnSurface
                    )
                }
                Text(
                    "${bd.percentageOfTotal.toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            // 비율 미니 바
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(CSurfaceVar)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((bd.percentageOfTotal / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (bd.totalAmount > 0L) formatWon(bd.totalAmount) else "${bd.count}건",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = COnSurface
                )
                Text("${bd.count}건", style = MaterialTheme.typography.bodySmall, color = CMuted)
            }
            if (bd.representativeNames.isNotEmpty()) {
                Text(
                    bd.representativeNames.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = CMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "이 카테고리 내역 ${items.size}건",
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        null,
                        tint = CMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.merchantName.ifBlank { item.title.ifBlank { "카드 내역" } },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = COnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.amount > 0L) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatWon(item.amount),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = CMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailSheet(transaction: CardTransactionRecord) {
    val statusColor = when (transaction.status) {
        TransactionStatus.APPROVED -> CGreen
        TransactionStatus.CANCELLED -> CRed
        TransactionStatus.UNKNOWN -> CMuted
    }
    val statusLabel = when (transaction.status) {
        TransactionStatus.APPROVED -> "승인"
        TransactionStatus.CANCELLED -> "취소"
        TransactionStatus.UNKNOWN -> "알 수 없음"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(statusColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Payments, null, tint = statusColor, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    transaction.merchantName ?: transaction.cardLabel ?: "카드 거래",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = COnSurface
                )
                Box(
                    Modifier
                        .background(statusColor.copy(0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = statusColor)
                }
            }
            Text(
                (if (transaction.status == TransactionStatus.CANCELLED) "-" else "") + formatWon(transaction.amount),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = statusColor
            )
        }

        HorizontalDivider(color = COutline, thickness = 0.5.dp)

        // 상세 필드
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransactionDetailRow("카드", transaction.cardLabel ?: "—")
            TransactionDetailRow("가맹점", transaction.merchantName ?: "—")
            TransactionDetailRow("금액", formatWon(transaction.amount))
            TransactionDetailRow("통화", transaction.currency)
            TransactionDetailRow("상태", statusLabel)
            if (!transaction.approvedAt.isNullOrBlank()) {
                TransactionDetailRow("승인 시각", transaction.approvedAt)
            }
            TransactionDetailRow("기록 시각", formatTransactionTimestamp(transaction.postedAt))
            TransactionDetailRow("월", transaction.monthKey)
        }

        HorizontalDivider(color = COutline, thickness = 0.5.dp)

        // 원본 알림 내용
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("원본 알림", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = CMuted)
            if (transaction.rawTitle.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CSurfaceVar, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("제목", style = MaterialTheme.typography.labelSmall, color = CMuted)
                    Text(transaction.rawTitle, style = MaterialTheme.typography.bodySmall, color = COnSurface)
                }
            }
            if (transaction.rawBody.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CSurfaceVar, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("본문", style = MaterialTheme.typography.labelSmall, color = CMuted)
                    Text(transaction.rawBody, style = MaterialTheme.typography.bodySmall, color = COnSurface)
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CMuted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = COnSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun SummaryPill(text: String) {
    Box(
        modifier = Modifier
            .background(CPrimary.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = CPrimary)
    }
}

@Composable
private fun WalletTransactionItem(transaction: CardTransactionRecord, onClick: () -> Unit) {
    val statusColor = when (transaction.status) {
        TransactionStatus.APPROVED -> CGreen
        TransactionStatus.CANCELLED -> CRed
        TransactionStatus.UNKNOWN -> CMuted
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = CSurfaceHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(statusColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Payments, null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    transaction.merchantName ?: transaction.cardLabel ?: "카드 거래",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = COnSurface
                )
                val meta = listOfNotNull(transaction.cardLabel, transaction.approvedAt?.ifBlank { null }).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = CMuted
                    )
                }
                Text(
                    formatTransactionTimestamp(transaction.postedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = CMuted.copy(alpha = 0.85f)
                )
            }
            Text(
                (if (transaction.status == TransactionStatus.CANCELLED) "-" else "") + formatWon(transaction.amount),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = statusColor
            )
        }
    }
}

private fun formatWon(amount: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(amount)}원"

private fun cardExpenseDisplayAmount(
    summary: MonthlyCardSummary,
    insightReport: CardExpenseInsightReport,
    insightItems: List<CardExpenseInsightCandidate>
): Long {
    val insightCollectedAmount = insightItems.sumOf { it.amount }
    return when {
        insightReport.totalAmount > 0L -> insightReport.totalAmount
        insightCollectedAmount > 0L -> insightCollectedAmount
        else -> summary.netSpent
    }
}

private fun formatTransactionTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))

private fun toolIcon(toolId: String): ImageVector = when (toolId) {
    "finance" -> Icons.Outlined.AccountBalance
    "law"     -> Icons.Outlined.Gavel
    "health"  -> Icons.Outlined.LocalHospital
    "edu"     -> Icons.Outlined.School
    else      -> Icons.AutoMirrored.Outlined.Article
}

private fun buildScheduleFinanceLine(
    subscriptionReport: SubscriptionAnalysisReport,
    cardInsightReport: CardExpenseInsightReport
): String {
    val parts = mutableListOf<String>()
    if (cardInsightReport.categoryBreakdowns.isNotEmpty()) {
        val top = cardInsightReport.categoryBreakdowns.first()
        parts.add("대표 소비: ${top.category} ${top.percentageOfTotal.toInt()}%")
    }
    if (subscriptionReport.candidates.isNotEmpty()) {
        parts.add("정기결제 후보 ${subscriptionReport.candidates.size}건")
    }
    return parts.joinToString(" · ")
}

private fun formatSignedWon(amount: Long): String {
    val sign = if (amount >= 0) "+" else "-"
    return sign + formatWon(kotlin.math.abs(amount))
}

private fun formatSignedCount(count: Int): String {
    val sign = if (count >= 0) "+" else ""
    return "${sign}${count}건"
}

// ─────────────────────── chat screen ───────────────────────

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    kind: ChatKind,
    answerModelLoaded: Boolean,
    answerModelReady: Boolean,
    isBusy: Boolean,
    isGenerating: Boolean,
    chatLoadingMessage: String?,
    onToggleModel: () -> Unit,
    onSendPrompt: (String) -> Unit
) {
    val listState    = rememberLazyListState()
    var prompt       by rememberSaveable(kind.name) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (!answerModelLoaded && !isBusy) {
            ModelBanner(ready = answerModelReady, onToggle = onToggleModel)
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyChatView(kind = kind)
            } else {
                LazyColumn(
                    state           = listState,
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { MessageBubble(it) }
                }
            }

            if (isGenerating) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp)) {
                    ThinkingIndicator(chatLoadingMessage.orEmpty())
                }
            }
        }

        ComposerBar(
            prompt          = prompt,
            enabled         = !isBusy && answerModelLoaded,
            isBusy          = isBusy,
            onPromptChanged = { prompt = it },
            onSend          = {
                val trimmed = prompt.trim()
                if (trimmed.isNotEmpty()) {
                    onSendPrompt(trimmed)
                    prompt = ""
                    focusManager.clearFocus()
                }
            }
        )
    }
}

@Composable
private fun ModelBanner(ready: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick  = { if (ready) onToggle() },
        enabled  = ready,
        color    = CAmber.copy(0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(8.dp).background(CAmber, CircleShape))
            Text(
                if (ready) "모델이 로드되지 않았습니다 — 탭하여 로드" else "먼저 모델 탭에서 모델을 준비하세요",
                style    = MaterialTheme.typography.labelMedium,
                color    = CAmber,
                modifier = Modifier.weight(1f)
            )
            if (ready) Icon(Icons.Outlined.Bolt, null, tint = CAmber, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyChatView(kind: ChatKind) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(88.dp).background(CPrimary.copy(0.08f), CircleShape))
            Box(Modifier.size(68.dp).background(CPrimary.copy(0.12f), CircleShape))
            Image(
                painter = painterResource(R.drawable.ic_walletmate_scene),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "WalletMate AI 어시스턴트",
            style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color     = COnSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "카드 사용내역, 일정, 금융 지식을 한 흐름으로 다루는 온디바이스 어시스턴트입니다.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = CMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        val suggestions = listOf("ETF 포트폴리오 추천", "전세 계약갱신 방법", "ISA 계좌 장점")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.take(2).forEach { SuggestionChip(it) }
        }
        Spacer(Modifier.height(8.dp))
        SuggestionChip(suggestions[2])
    }
}

@Composable
private fun SuggestionChip(text: String) {
    Box(
        Modifier
            .border(1.dp, COutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = CMuted)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                Modifier.size(30.dp).background(GradientAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        val shape = RoundedCornerShape(
            topStart    = 20.dp, topEnd = 20.dp,
            bottomStart = if (isUser) 20.dp else 4.dp,
            bottomEnd   = if (isUser) 4.dp  else 20.dp
        )
        Text(
            message.text,
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.78f else 0.82f)
                .clip(shape)
                .background(if (isUser) GradientUser else Brush.linearGradient(listOf(CSurfaceHigh, CSurfaceHigh)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = if (isUser) Color.White else COnSurface
        )
    }
}

@Composable
private fun ThinkingIndicator(text: String) {
    val t = rememberInfiniteTransition("thinking")
    val d1 by t.animateFloat(0f, -7f, infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d1")
    val d2 by t.animateFloat(0f, -7f, infiniteRepeatable(tween(450, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d2")
    val d3 by t.animateFloat(0f, -7f, infiniteRepeatable(tween(450, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d3")
    Surface(shape = RoundedCornerShape(16.dp), color = CSurfaceHigh) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(d1, d2, d3).forEach { offset ->
                Box(Modifier.size(6.dp).offset(y = offset.dp).background(CPrimary, CircleShape))
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelSmall, color = CMuted)
            }
        }
    }
}

@Composable
private fun ComposerBar(
    prompt: String,
    enabled: Boolean,
    isBusy: Boolean,
    onPromptChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(color = CSurface, shadowElevation = 16.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value          = prompt,
                onValueChange  = onPromptChanged,
                enabled        = enabled,
                modifier       = Modifier.weight(1f),
                placeholder    = { Text("메시지를 입력하세요...", style = MaterialTheme.typography.bodyMedium, color = CMuted) },
                minLines       = 1,
                maxLines       = 5,
                shape          = RoundedCornerShape(22.dp),
                colors         = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = CPrimary.copy(0.7f),
                    unfocusedBorderColor    = COutline,
                    disabledBorderColor     = COutline.copy(0.4f),
                    focusedContainerColor   = CSurfaceHigh,
                    unfocusedContainerColor = CSurfaceHigh,
                    disabledContainerColor  = CSurfaceHigh.copy(0.6f),
                    cursorColor             = CPrimary,
                    focusedTextColor        = COnSurface,
                    unfocusedTextColor      = COnSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            val canSend = enabled && prompt.isNotBlank()
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (canSend) GradientAccent else Brush.linearGradient(listOf(CSurfaceVar, CSurfaceVar)))
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CPrimary)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, "보내기", tint = if (canSend) Color.White else CMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─────────────────────── document viewer screen ───────────────────────

@Composable
private fun DocumentViewerScreen(category: KnowledgeCategory, text: String?) {
    if (text == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CPrimary, modifier = Modifier.size(32.dp))
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().background(CBackground),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = CSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, COutline)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(16.dp), tint = CPrimary)
                    Text(
                        category.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CPrimary
                    )
                }
            }
        }
        item {
            Text(
                text,
                style      = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp),
                color      = COnSurface,
                modifier   = Modifier
                    .fillMaxWidth()
                    .background(CSurface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            )
        }
    }
}

// ─────────────────────── document screen ───────────────────────

@Composable
private fun DocumentScreen(
    uiState: AppUiState,
    onPickDocument: () -> Unit,
    onSectionUpdated: (Int, SectionEditState) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onEnterText: (String, String) -> Unit
) {
    val importState = uiState.documentImportState
    if (importState != null) {
        SectionMappingScreen(
            state            = importState,
            tools            = uiState.knowledgeTools,
            onSectionUpdated = onSectionUpdated,
            onConfirm        = onConfirmImport,
            onCancel         = onCancelImport
        )
    } else {
        DocumentListScreen(
            userDocuments  = uiState.userDocuments,
            onPickDocument = onPickDocument,
            onDelete       = onDeleteDocument,
            onEnterText    = onEnterText
        )
    }
}

@Composable
private fun DocumentListScreen(
    userDocuments: List<UserDocument>,
    onPickDocument: () -> Unit,
    onDelete: (String) -> Unit,
    onEnterText: (String, String) -> Unit
) {
    var showTextInput by rememberSaveable { mutableStateOf(false) }
    var textTitle     by rememberSaveable { mutableStateOf("") }
    var textContent   by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CPrimary.copy(0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CPrimary.copy(0.2f))
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(40.dp).background(CPrimary.copy(0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.UploadFile, null, tint = CPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("문서 추가", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
                            Text("파일 업로드 또는 텍스트를 직접 입력해 분류합니다", style = MaterialTheme.typography.bodySmall, color = CMuted)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick        = onPickDocument,
                            modifier       = Modifier.weight(1f).height(44.dp),
                            shape          = RoundedCornerShape(12.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(GradientAccent, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), Color.White)
                                    Text("파일에서 추가", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                                }
                            }
                        }
                        Button(
                            onClick        = { showTextInput = !showTextInput },
                            modifier       = Modifier.weight(1f).height(44.dp),
                            shape          = RoundedCornerShape(12.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (showTextInput) CPrimary.copy(0.15f) else CSurfaceVar,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (showTextInput) CPrimary.copy(0.5f) else COutline,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.Create, null, Modifier.size(16.dp), if (showTextInput) CPrimary else CMuted)
                                    Text("텍스트 입력", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = if (showTextInput) CPrimary else CMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showTextInput) {
            item {
                TextInputCard(
                    title           = textTitle,
                    content         = textContent,
                    onTitleChanged  = { textTitle = it },
                    onContentChanged = { textContent = it },
                    onSubmit        = {
                        val c = textContent.trim()
                        if (c.isNotBlank()) {
                            onEnterText(textTitle.trim().ifBlank { "직접 입력" }, c)
                            textTitle = ""; textContent = ""; showTextInput = false
                        }
                    },
                    onCancel        = {
                        showTextInput = false; textTitle = ""; textContent = ""
                    }
                )
            }
        }

        if (userDocuments.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(40.dp), tint = CMuted.copy(0.4f))
                    Text("추가된 문서가 없습니다", style = MaterialTheme.typography.bodyMedium, color = CMuted)
                    Text("파일을 추가하면 문서 기반 응답에서 활용됩니다", style = MaterialTheme.typography.bodySmall, color = CMuted.copy(0.6f), textAlign = TextAlign.Center)
                }
            }
        } else {
            item {
                Text(
                    "등록된 문서 (${userDocuments.size}개)",
                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color    = CMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(userDocuments, key = { it.filePath }) { doc ->
                UserDocumentCard(doc = doc, onDelete = { onDelete(doc.filePath) })
            }
        }
    }
}

@Composable
private fun TextInputCard(
    title: String,
    content: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = CPrimary.copy(0.7f),
        unfocusedBorderColor    = COutline,
        focusedContainerColor   = CSurfaceVar,
        unfocusedContainerColor = CSurfaceVar,
        cursorColor             = CPrimary,
        focusedTextColor        = COnSurface,
        unfocusedTextColor      = COnSurface
    )
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CSurfaceHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, COutline)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(32.dp).background(CPrimary.copy(0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Create, null, Modifier.size(16.dp), tint = CPrimary)
                }
                Text("텍스트 직접 입력", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
            }
            OutlinedTextField(
                value         = title,
                onValueChange = onTitleChanged,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("문서 제목 (선택)", style = MaterialTheme.typography.bodySmall, color = CMuted.copy(0.5f)) },
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                textStyle     = MaterialTheme.typography.bodyMedium,
                colors        = fieldColors
            )
            OutlinedTextField(
                value         = content,
                onValueChange = onContentChanged,
                modifier      = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder   = { Text("내용을 입력하거나 붙여넣으세요...", style = MaterialTheme.typography.bodySmall, color = CMuted.copy(0.5f)) },
                minLines      = 5,
                maxLines      = 15,
                shape         = RoundedCornerShape(10.dp),
                textStyle     = MaterialTheme.typography.bodyMedium,
                colors        = fieldColors
            )
            Text(
                "입력한 텍스트는 섹션으로 분류됩니다. 다음 화면에서 분류를 확인하고 저장합니다.",
                style = MaterialTheme.typography.labelSmall,
                color = CMuted.copy(0.7f)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, COutline),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = CMuted)
                ) {
                    Text("취소", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick  = onSubmit,
                    enabled  = content.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = CPrimary,
                        disabledContainerColor = CSurfaceVar
                    )
                ) {
                    Text("분류 검토", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun UserDocumentCard(doc: UserDocument, onDelete: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CSurfaceHigh)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(36.dp).background(CAmber.copy(0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(18.dp), tint = CAmber)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (doc.minor.isNotBlank()) "${doc.major} > ${doc.middle} > ${doc.minor}" else "${doc.major} > ${doc.middle}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = COnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(doc.fileName, style = MaterialTheme.typography.labelSmall, color = CMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CRed.copy(0.1f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DeleteOutline, "삭제", Modifier.size(16.dp), tint = CRed.copy(0.8f))
            }
        }
    }
}

// ─────────────────────── section mapping screen ───────────────────────

@Composable
private fun SectionMappingScreen(
    state: DocumentImportUiState,
    tools: List<KnowledgeTool>,
    onSectionUpdated: (Int, SectionEditState) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = CSurfaceHigh, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, "취소", tint = CMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text("섹션 분류 확인", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
                    Text("${state.fileName} · ${state.sections.size}개 섹션 감지됨", style = MaterialTheme.typography.labelSmall, color = CMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CPrimary)
                } else {
                    Button(
                        onClick = onConfirm,
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = CPrimary)
                    ) {
                        Text("저장", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.sections, key = { it.index }) { section ->
                SectionMappingCard(
                    section  = section,
                    tools    = tools,
                    onUpdate = { updated -> onSectionUpdated(section.index, updated) }
                )
            }
        }
    }
}

@Composable
private fun SectionMappingCard(
    section: SectionEditState,
    tools: List<KnowledgeTool>,
    onUpdate: (SectionEditState) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CSurfaceHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.background(CPrimary.copy(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("섹션 ${section.index + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = CPrimary)
                }
                if (section.major.isNotBlank()) {
                    Box(
                        Modifier.background(CGreen.copy(0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("자동 감지됨", style = MaterialTheme.typography.labelSmall, color = CGreen)
                    }
                }
            }

            // Preview / full text toggle
            var showFull by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CSurfaceVar, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (showFull) section.fullText
                    else section.previewText.take(120) + if (section.previewText.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = CMuted
                )
                if (section.fullText.length > 120) {
                    Text(
                        if (showFull) "간략히 보기" else "전체 보기",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CPrimary,
                        modifier = Modifier.clickable { showFull = !showFull }
                    )
                }
            }

            HorizontalDivider(color = COutline.copy(0.5f), thickness = 0.5.dp)

            // Domain selector
            Text("지식 도메인", style = MaterialTheme.typography.labelSmall, color = CMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tools.forEach { tool ->
                    val selected = section.toolId == tool.id
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CPrimary.copy(0.2f) else CSurfaceVar)
                            .border(1.dp, if (selected) CPrimary.copy(0.5f) else COutline, RoundedCornerShape(10.dp))
                            .clickable { onUpdate(section.copy(toolId = tool.id)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(tool.displayName, style = MaterialTheme.typography.labelMedium, color = if (selected) CPrimary else CMuted)
                    }
                }
            }

            // Major / middle / minor fields
            MappingTextField(
                label     = "대분류",
                value     = section.major,
                hint      = "예: 부동산, 투자",
                onChanged = { onUpdate(section.copy(major = it)) }
            )
            MappingTextField(
                label     = "중분류",
                value     = section.middle,
                hint      = "예: 대출 규제, ETF 포트폴리오",
                onChanged = { onUpdate(section.copy(middle = it)) }
            )
            MappingTextField(
                label     = "소분류 (선택)",
                value     = section.minor,
                hint      = "예: LTV/DSR, 미국 ETF",
                onChanged = { onUpdate(section.copy(minor = it)) }
            )
        }
    }
}

@Composable
private fun MappingTextField(label: String, value: String, hint: String, onChanged: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CMuted)
        OutlinedTextField(
            value         = value,
            onValueChange = onChanged,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text(hint, style = MaterialTheme.typography.bodySmall, color = CMuted.copy(0.5f)) },
            singleLine    = true,
            shape         = RoundedCornerShape(10.dp),
            textStyle     = MaterialTheme.typography.bodyMedium,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = CPrimary.copy(0.7f),
                unfocusedBorderColor    = COutline,
                focusedContainerColor   = CSurfaceVar,
                unfocusedContainerColor = CSurfaceVar,
                cursorColor             = CPrimary,
                focusedTextColor        = COnSurface,
                unfocusedTextColor      = COnSurface
            )
        )
    }
}

// ─────────────────────── model screen ───────────────────────

@Composable
private fun ModelScreen(
    uiState: AppUiState,
    onToggleModel: () -> Unit,
    onPickModel: () -> Unit,
    onDownloadGemma: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.busy.visible) {
            item { ProgressCard(uiState.busy.message, uiState.busy.determinate, uiState.busy.progress) }
        }
        item {
            ModelCard(
                title        = "답변 모델",
                subtitle     = uiState.modelDescription,
                statusLabel  = when {
                    uiState.answerModelLoaded -> "로드됨"
                    uiState.answerModelReady  -> "준비됨 (미로드)"
                    else -> "없음"
                },
                statusActive = uiState.answerModelLoaded,
                actions = listOf(
                    ModelAction("Gemma 4 2bit 다운로드",  Icons.Outlined.Download,    onDownloadGemma, true),
                    ModelAction("로컬 파일 선택",          Icons.Outlined.UploadFile,  onPickModel, true),
                    ModelAction(
                        if (uiState.answerModelLoaded) "언로드" else "로드",
                        Icons.Outlined.Bolt, onToggleModel, uiState.answerModelReady
                    )
                )
            )
        }
    }
}

@Composable
private fun ProgressCard(message: String, determinate: Boolean, progress: Int) {
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CPrimary.copy(0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CPrimary.copy(0.25f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CPrimary)
                Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = COnSurface)
            }
            if (determinate) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        { progress / 100f },
                        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = CPrimary, trackColor = CSurfaceHigh, strokeCap = StrokeCap.Round
                    )
                    Text("$progress%", style = MaterialTheme.typography.labelSmall, color = CMuted)
                }
            } else {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = CPrimary, trackColor = CSurfaceHigh, strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

private data class ModelAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val enabled: Boolean = true)

@Composable
private fun ModelCard(title: String, subtitle: String, statusLabel: String, statusActive: Boolean, actions: List<ModelAction>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CSurfaceHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = COnSurface, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .background(if (statusActive) CGreen.copy(0.15f) else CMuted.copy(0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (statusActive) CGreen else CMuted)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CMuted)
            HorizontalDivider(color = COutline.copy(0.5f), thickness = 0.5.dp)
            actions.forEach { action ->
                Surface(
                    onClick  = action.onClick,
                    enabled  = action.enabled,
                    shape    = RoundedCornerShape(10.dp),
                    color    = CSurfaceVar.copy(if (action.enabled) 1f else 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(action.icon, null, Modifier.size(18.dp), tint = if (action.enabled) CPrimary else CMuted.copy(0.5f))
                        Text(action.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (action.enabled) COnSurface else CMuted.copy(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageScreen(
    permissionGranted: Boolean,
    summary: AppUsageStatsSummary,
    topApps: List<AppUsageTopApp>,
    recentSessions: List<AppUsageSessionRecord>,
    onOpenPermissionSettings: () -> Unit,
    onRunSync: () -> Unit,
    onExportCsv: () -> Unit,
    onClearLog: () -> Unit
) {
    val context = LocalContext.current
    var showAllowedAppsDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = CSurfaceHigh) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { showAllowedAppsDialog = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "허용 앱 보기", tint = CMuted)
                        }
                    }
                    Button(
                        onClick = onOpenPermissionSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (permissionGranted) "권한 설정 열기" else "권한 허용하기")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRunSync,
                            enabled = permissionGranted,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("지금 동기화")
                        }
                        OutlinedButton(
                            onClick = onClearLog,
                            enabled = permissionGranted && summary.totalSessions > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DB 비우기")
                        }
                    }
                    OutlinedButton(
                        onClick = onExportCsv,
                        enabled = permissionGranted && summary.totalSessions > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("학습 CSV 내보내기")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricCard("세션 수", summary.totalSessions.toString(), Modifier.weight(1f))
                SummaryMetricCard("앱 수", summary.distinctPackageCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricCard("누적 시간", formatDurationCompact(summary.totalDurationSeconds), Modifier.weight(1f))
                SummaryMetricCard(
                    "마지막 동기화",
                    formatDateTimeOrDash(summary.lastSyncedAtMillis),
                    Modifier.weight(1f)
                )
            }
        }

        if (topApps.isNotEmpty()) {
            item {
                Text(
                    "상위 앱",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = COnSurface
                )
            }
            items(topApps, key = { it.packageName }) { app ->
                Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                resolveAppLabel(context, app.packageName),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = COnSurface
                            )
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = CMuted)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${app.sessionCount}회", style = MaterialTheme.typography.labelLarge, color = CPrimary)
                            Text(formatDurationCompact(app.totalDurationSeconds), style = MaterialTheme.typography.bodySmall, color = CMuted)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "최근 세션",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = COnSurface
            )
        }

        if (recentSessions.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("저장된 사용 세션이 없습니다.", style = MaterialTheme.typography.titleSmall, color = COnSurface)
                        Text("권한을 허용한 뒤 동기화를 실행하면 DB에 세션이 쌓입니다.", style = MaterialTheme.typography.bodySmall, color = CMuted)
                    }
                }
            }
        } else {
            items(recentSessions, key = { it.id }) { session ->
                Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            resolveAppLabel(context, session.packageName),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = COnSurface
                        )
                        HorizontalDivider(
                            color = COutline.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        UsagePropertyRow("패키지명", session.packageName)
                        UsagePropertyRow("카테고리", AppUsageAllowlistPolicy.categoryLabel(context, session.appCategory))
                        UsagePropertyRow("시작", formatDateTime(session.startedAtMillis))
                        UsagePropertyRow("길이", formatDurationCompact(session.durationSeconds))
                        UsagePropertyRow("요일", weekdayLabel(session.weekday))
                        UsagePropertyRow("HHMM", session.hhmm.toString().padStart(4, '0'))
                    }
                }
            }
        }
    }

    if (showAllowedAppsDialog) {
        AlertDialog(
            onDismissRequest = { showAllowedAppsDialog = false },
            title = { Text("허용 앱 목록") },
            text = { Text(AppUsageAllowlistPolicy.allowedAppNamesLabel()) },
            confirmButton = {
                TextButton(onClick = { showAllowedAppsDialog = false }) { Text("확인") }
            }
        )
    }
}

@Composable
private fun UsagePropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = CMuted,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = COnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = CSurfaceHigh, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = CMuted)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = COnSurface)
        }
    }
}

// ─────────────────────── status screen ───────────────────────

@Composable
private fun StatusScreen(uiState: AppUiState, activeSession: ChatSession?) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CSurfaceHigh)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("런타임 상태", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = COnSurface)
                    HorizontalDivider(color = COutline.copy(0.5f), thickness = 0.5.dp)
                    StatusRow("선택 모델",       uiState.selectedModelLabel, true)
                    StatusRow("채팅 종류",       activeSession?.kind?.label ?: "—", true)
                    StatusRow("답변 모델",       if (uiState.answerModelLoaded) "로드됨" else if (uiState.answerModelReady) "준비됨" else "없음", uiState.answerModelLoaded)
                    StatusRow("지식 도메인",     "${uiState.knowledgeTools.size}개 로드됨", uiState.knowledgeTools.isNotEmpty())
                    StatusRow("사용자 문서",     "${uiState.userDocuments.size}개", uiState.userDocuments.isNotEmpty())
                    StatusRow("사용 패턴 권한",  if (uiState.appUsagePermissionGranted) "허용됨" else "없음", uiState.appUsagePermissionGranted)
                    StatusRow("사용 패턴 세션",  "${uiState.appUsageSummary.totalSessions}건", uiState.appUsageSummary.totalSessions > 0)
                    if (uiState.lastLoadInfo.isNotBlank()) {
                        StatusRow("마지막 로드", uiState.lastLoadInfo, !uiState.lastLoadInfo.contains("실패"))
                    }
                }
            }
        }
        if (uiState.appUsagePermissionGranted) {
            item {
                Card(
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, COutline)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("사용 패턴 DB", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = CMuted)
                        Text(
                            "앱 ${uiState.appUsageSummary.distinctPackageCount}개 · 누적 ${formatDurationCompact(uiState.appUsageSummary.totalDurationSeconds)} · 마지막 동기화 ${formatDateTimeOrDash(uiState.appUsageSummary.lastSyncedAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = COnSurface
                        )
                    }
                }
            }
        }
        if (uiState.modelDescription.isNotBlank()) {
            item {
                Card(
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, COutline)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("모델 경로", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = CMuted)
                        Text(uiState.modelDescription, style = MaterialTheme.typography.bodySmall, color = COnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, active: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value == "로드됨") Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = CGreen)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = when { value == "로드됨" -> CGreen; !active -> CMuted; else -> COnSurface }
            )
        }
    }
}

private fun resolveAppLabel(context: android.content.Context, packageName: String): String {
    val packageManager = context.packageManager
    return runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)
}

private fun weekdayLabel(weekday: Int): String = when (weekday) {
    1 -> "월"
    2 -> "화"
    3 -> "수"
    4 -> "목"
    5 -> "금"
    6 -> "토"
    7 -> "일"
    else -> "-"
}

private fun formatDateTimeOrDash(timestampMillis: Long): String =
    if (timestampMillis <= 0L) "—" else formatDateTime(timestampMillis)

private fun formatDateTime(timestampMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.KOREA).format(Date(timestampMillis))

private fun formatDurationCompact(seconds: Long): String {
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return when {
        hours > 0L -> "${hours}시간 ${minutes}분"
        minutes > 0L -> "${minutes}분"
        else -> "${seconds}초"
    }
}
