package com.example.gemma4ondevicetest.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── color tokens (재사용) ───────────────────────────────────
private val CPrimary     = Color(0xFF9C8FFF)
private val CPrimaryDeep = Color(0xFF6A5FCC)
private val CBackground  = Color(0xFF0C0C18)
private val CSurface     = Color(0xFF141421)
private val CSurfaceHigh = Color(0xFF1C1C2E)
private val CSurfaceVar  = Color(0xFF23233A)
private val COnSurface   = Color(0xFFEBE8F8)
private val CMuted       = Color(0xFF8C89AC)
private val COutline     = Color(0xFF353458)
private val CGreen       = Color(0xFF4ECBA0)
private val CAmber       = Color(0xFFFFB547)
private val CRed         = Color(0xFFFF6B6B)
private val CTeal        = Color(0xFF4DD0E1)

private val GradientHeader = Brush.linearGradient(listOf(CPrimaryDeep, CPrimary))

// ─── UI state ────────────────────────────────────────────────

enum class ScheduleLoadState { IDLE, LOADING, DONE, ERROR }

data class ScheduleUiState(
    val loadState: ScheduleLoadState = ScheduleLoadState.IDLE,
    val entries: List<ScheduleEntry> = emptyList(),
    val gemmaLines: List<String> = emptyList(),
    val notifEnabled: Boolean = false,
    val notifHour: Int = 8,
    val notifMinute: Int = 0,
    val calendarPermission: Boolean = false,
    val notifPermission: Boolean = false,
    val errorMessage: String = ""
)

// ─── main screen ─────────────────────────────────────────────

@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    modelLoaded: Boolean,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onTimeChanged: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    onRunNow: () -> Unit,
    onTestAlarm: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CBackground)
    ) {
        // ── Permission banner ──
        if (!uiState.calendarPermission || !uiState.notifPermission) {
            PermissionBanner(uiState, onRequestPermissions)
        }

        // ── Header card ──
        ScheduleHeaderCard(
            notifEnabled  = uiState.notifEnabled,
            notifHour     = uiState.notifHour,
            notifMinute   = uiState.notifMinute,
            modelLoaded   = modelLoaded,
            onToggle      = onToggleNotification,
            onTimeChanged = onTimeChanged,
            onRunNow      = onRunNow,
            onTestAlarm   = onTestAlarm
        )

        // ── Content ──
        AnimatedContent(
            targetState = uiState.loadState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "schedule_content"
        ) { state ->
            when (state) {
                ScheduleLoadState.IDLE    -> EmptyHint(onRefresh)
                ScheduleLoadState.LOADING -> LoadingView()
                ScheduleLoadState.ERROR   -> ErrorView(uiState.errorMessage, onRefresh)
                ScheduleLoadState.DONE    -> ScheduleList(
                    entries    = uiState.entries,
                    gemmaLines = uiState.gemmaLines,
                    onRefresh  = onRefresh
                )
            }
        }
    }
}

// ─── permission banner ────────────────────────────────────────

@Composable
private fun PermissionBanner(uiState: ScheduleUiState, onRequest: () -> Unit) {
    val missing = buildList {
        if (!uiState.calendarPermission) add("캘린더")
        if (!uiState.notifPermission)    add("알림")
    }
    Surface(
        color    = CAmber.copy(alpha = 0.12f),
        shape    = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Warning, null, tint = CAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "${missing.joinToString(", ")} 권한이 필요합니다",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = CAmber
            )
            TextButton(onClick = onRequest) {
                Text("허용", color = CAmber, fontSize = 12.sp)
            }
        }
    }
}

// ─── header card ─────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleHeaderCard(
    notifEnabled: Boolean,
    notifHour: Int,
    notifMinute: Int,
    modelLoaded: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    onRunNow: () -> Unit,
    onTestAlarm: (() -> Unit)? = null
) {
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val timeState = androidx.compose.material3.rememberTimePickerState(
            initialHour   = notifHour,
            initialMinute = notifMinute,
            is24Hour      = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChanged(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("확인", color = CPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("취소", color = CMuted)
                }
            },
            containerColor = CSurfaceHigh,
            text = {
                androidx.compose.material3.TimeInput(
                    state  = timeState,
                    colors = androidx.compose.material3.TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor   = CPrimary.copy(alpha = 0.2f),
                        timeSelectorUnselectedContainerColor = CSurfaceVar,
                        timeSelectorSelectedContentColor     = CPrimary,
                        timeSelectorUnselectedContentColor   = CMuted,
                        containerColor                       = CSurfaceHigh,
                        periodSelectorBorderColor            = COutline,
                    )
                )
            }
        )
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CSurfaceHigh)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GradientHeader),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "일주일 일정 요약",
                        style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color      = COnSurface
                    )
                    Text(
                        "Gemma가 일정을 요약해 알림으로 알려줍니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = CMuted
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = COutline, thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))

            // Notification toggle row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = if (notifEnabled) CGreen else CMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (notifEnabled) "알림 켜짐" else "알림 꺼짐",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notifEnabled) CGreen else CMuted
                )
                Switch(
                    checked = notifEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor  = Color.White,
                        checkedTrackColor  = CGreen,
                        uncheckedTrackColor= CSurfaceVar
                    )
                )
            }

            // Time picker row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = CMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "매일 %02d:%02d".format(notifHour, notifMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notifEnabled) COnSurface else CMuted
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { showTimePicker = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(14.dp), tint = CPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("시간 변경", fontSize = 12.sp, color = CPrimary)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Model status + run-now
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelChip(loaded = modelLoaded)
                Spacer(Modifier.weight(1f))
                if (onTestAlarm != null) {
                    OutlinedButton(
                        onClick = onTestAlarm,
                        shape   = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = CAmber)
                    ) {
                        Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("60초 테스트", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = onRunNow,
                    enabled = modelLoaded,
                    shape   = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = CPrimary)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("지금 요약", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelChip(loaded: Boolean) {
    val color = if (loaded) CGreen else CMuted
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (loaded) "Gemma 로드됨" else "Gemma 미로드",
            fontSize = 12.sp,
            color    = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── schedule list ────────────────────────────────────────────

@Composable
private fun ScheduleList(
    entries: List<ScheduleEntry>,
    gemmaLines: List<String>,
    onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding     = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement= Arrangement.spacedBy(0.dp)
    ) {
        // Gemma summary section
        if (gemmaLines.isNotEmpty()) {
            item { SectionHeader("Gemma 요약", Icons.Outlined.AutoAwesome, CPrimary) }
            item {
                GemmaSummaryCard(gemmaLines)
                Spacer(Modifier.height(16.dp))
            }
        }

        // Raw entries by date
        if (entries.isNotEmpty()) {
            item { SectionHeader("전체 일정", Icons.Outlined.DateRange, CTeal) }

            // entries are pre-sorted by sortMillis; groupBy preserves insertion order
            val grouped = entries.sortedBy { it.sortMillis }.groupBy { it.dateLabel }
            grouped.forEach { (date, dayEntries) ->
                item { DateGroupHeader(date) }
                items(dayEntries) { entry -> EntryRow(entry) }
                item { Spacer(Modifier.height(6.dp)) }
            }
        } else {
            item {
                EmptyDayCard()
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp), tint = CMuted)
                    Spacer(Modifier.width(4.dp))
                    Text("새로고침", color = CMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GemmaSummaryCard(lines: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CPrimary.copy(0.08f))
    ) {
        Column(Modifier.padding(16.dp)) {
            lines.forEachIndexed { idx, line ->
                if (idx > 0) Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CPrimary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = COnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DateGroupHeader(date: String) {
    Text(
        date,
        Modifier.padding(top = 12.dp, bottom = 4.dp),
        style      = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color      = CPrimary
    )
}

@Composable
private fun EntryRow(entry: ScheduleEntry) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape  = RoundedCornerShape(14.dp),
        color  = CSurfaceHigh
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CTeal.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Event, null, tint = CTeal, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color    = COnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = CMuted
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, icon: ImageVector, color: Color) {
    Row(
        Modifier.padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

// ─── helper views ─────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CPrimary)
            Spacer(Modifier.height(16.dp))
            Text("Gemma가 일정을 분석 중입니다...", color = CMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorView(msg: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = CRed, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(msg.ifBlank { "불러오기 실패" }, color = CMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = CSurfaceHigh)) {
                Text("다시 시도", color = CPrimary)
            }
        }
    }
}

@Composable
private fun EmptyHint(onLoad: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = CMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("일정을 불러오려면 아래 버튼을 누르세요", color = CMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLoad, colors = ButtonDefaults.buttonColors(containerColor = CPrimary)) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("일정 불러오기", color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyDayCard() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.EventAvailable, null, tint = CGreen, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("앞으로 7일 이내 일정이 없습니다", color = CMuted, fontSize = 13.sp)
        }
    }
}
