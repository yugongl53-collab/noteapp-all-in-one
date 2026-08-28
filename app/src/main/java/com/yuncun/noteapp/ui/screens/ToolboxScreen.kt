package com.yuncun.noteapp.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.model.WheelSegment
import com.yuncun.noteapp.domain.rules.EventPoolRules
import com.yuncun.noteapp.ui.pomodoro.PomodoroUiState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val SECTION_POOL = "pool"
const val SECTION_TIMER = "timer"

/**
 * 工具箱主页：重构为 App 桌面网格门户（App Grid Launcher），以方形图标卡片展示番茄钟与幸运大转盘等独立小工具。
 */
@Composable
fun ToolboxScreen(
    state: PomodoroUiState,
    onNavigateToPomodoro: () -> Unit,
    onNavigateToWheel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCount = state.poolItems.count { it.isEnabled }
    val totalCount = state.poolItems.size

    val pomodoroSubtitle = when (state.session?.state) {
        PomodoroState.RUNNING -> {
            val phaseText = if (state.session.phase == PomodoroPhase.FOCUS) "专注中" else "休息中"
            "$phaseText · ${formatRemaining(state.remainingSeconds)}"
        }
        PomodoroState.PAUSED -> "已暂停 · ${formatRemaining(state.remainingSeconds)}"
        PomodoroState.COMPLETED -> "阶段已完成 · 待确认"
        null -> "沉浸专注 · 阶段倒计时"
    }

    val wheelSubtitle = if (totalCount == 0) {
        "加权随机 · 快速决策"
    } else {
        "已启用 $enabledCount / $totalCount 项"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2 列网格布局展示方形小工具磁贴
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 番茄钟入口磁贴
            ToolLauncherCard(
                title = "番茄钟",
                subtitle = pomodoroSubtitle,
                icon = Icons.Default.Timer,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onNavigateToPomodoro,
                modifier = Modifier.weight(1f),
                isActive = state.session != null
            )

            // 幸运大转盘入口磁贴
            ToolLauncherCard(
                title = "幸运大转盘",
                subtitle = wheelSubtitle,
                icon = Icons.Default.Shuffle,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onNavigateToWheel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 桌面网格应用图标磁贴组件，具有圆角方形图标、主题配色与点击反馈。
 */
@Composable
fun ToolLauncherCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Card(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconContentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (isActive) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    ) {}
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 独立番茄专注页面：具备顶部导航返回、充裕倒计时排版、阶段控制与沉浸式全屏支持。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PomodoroToolScreen(
    state: PomodoroUiState,
    notificationGranted: Boolean,
    onStart: (String?, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String? = null
) {
    var carriedTitle by rememberSaveable(initialTitle) { mutableStateOf(initialTitle.orEmpty()) }
    var showFullscreenPomodoro by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    LaunchedEffect(initialTitle) {
        if (!initialTitle.isNullOrBlank()) {
            carriedTitle = initialTitle
        }
    }

    val session = state.session
    var title by rememberSaveable(carriedTitle) { mutableStateOf(carriedTitle) }
    var focusText by rememberSaveable { mutableStateOf(state.settings.lastFocusMinutes.toString()) }
    var restText by rememberSaveable { mutableStateOf(state.settings.lastRestMinutes.toString()) }

    LaunchedEffect(carriedTitle) {
        if (carriedTitle.isNotBlank()) title = carriedTitle
    }
    LaunchedEffect(state.settings) {
        focusText = state.settings.lastFocusMinutes.toString()
        restText = state.settings.lastRestMinutes.toString()
    }

    val focus = focusText.toIntOrNull()
    val rest = restText.toIntOrNull()
    val valid = focus != null && focus in 1..180 && rest != null && rest in 1..60

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("番茄钟", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { showFullscreenPomodoro = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "展开全屏沉浸视图")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (session == null) {
                        // 未开始状态配置表单
                        Text(
                            "专注设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("事项（可选）") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (state.poolNameSuggestions.isNotEmpty()) {
                            Text("从事件池带入名称", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.poolNameSuggestions.take(6).forEach { suggestion ->
                                    FilterChip(
                                        selected = title == suggestion,
                                        onClick = { title = suggestion },
                                        label = { Text(suggestion) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = focusText,
                                onValueChange = { focusText = it.filter(Char::isDigit) },
                                label = { Text("专注 (1–180 分钟)") },
                                isError = focus == null || focus !in 1..180,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = restText,
                                onValueChange = { restText = it.filter(Char::isDigit) },
                                label = { Text("休息 (1–60 分钟)") },
                                isError = rest == null || rest !in 1..60,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!notificationGranted) {
                            Text(
                                "未授权通知：倒计时仍会恢复，但后台阶段结束提醒无法显示。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (!notificationGranted) onRequestNotificationPermission()
                                onStart(title.ifBlank { null }, requireNotNull(focus), requireNotNull(rest))
                            },
                            enabled = valid && !state.operationInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("开始专注", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        // 进行中 / 暂停 / 已完成状态
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (session.phase == PomodoroPhase.FOCUS) "专注阶段" else "休息阶段",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )

                            session.title?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Text(
                                formatRemaining(state.remainingSeconds),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                when (session.state) {
                                    PomodoroState.RUNNING -> "进行中"
                                    PomodoroState.PAUSED -> "已暂停"
                                    PomodoroState.COMPLETED -> "阶段已完成，等待你的确认"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(8.dp))

                            // 快捷操作按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (session.state) {
                                    PomodoroState.RUNNING -> {
                                        Button(
                                            onClick = onPause,
                                            enabled = !state.operationInProgress,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Pause, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("暂停")
                                        }
                                        OutlinedButton(
                                            onClick = onReset,
                                            enabled = !state.operationInProgress,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("重置")
                                        }
                                        TextButton(
                                            onClick = { confirmFinish = true },
                                            enabled = !state.operationInProgress
                                        ) {
                                            Text("提前结束")
                                        }
                                    }
                                    PomodoroState.PAUSED -> {
                                        Button(
                                            onClick = onResume,
                                            enabled = !state.operationInProgress,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("继续")
                                        }
                                        OutlinedButton(
                                            onClick = onReset,
                                            enabled = !state.operationInProgress,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("重置")
                                        }
                                        TextButton(
                                            onClick = { confirmFinish = true },
                                            enabled = !state.operationInProgress
                                        ) {
                                            Text("提前结束")
                                        }
                                    }
                                    PomodoroState.COMPLETED -> {
                                        if (session.phase == PomodoroPhase.FOCUS) {
                                            Button(
                                                onClick = onStartRest,
                                                enabled = !state.operationInProgress,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("开始休息")
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onClearSession,
                                            enabled = !state.operationInProgress,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (session.phase == PomodoroPhase.REST) "完成并返回" else "结束本轮")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "番茄钟仅辅助计时，不生成时间记录，也不进入统计。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 全屏沉浸番茄钟视图
    if (showFullscreenPomodoro && session != null) {
        PomodoroFullscreenDialog(
            session = session,
            remainingSeconds = state.remainingSeconds,
            operationInProgress = state.operationInProgress,
            onDismiss = { showFullscreenPomodoro = false },
            onPause = onPause,
            onResume = onResume,
            onReset = onReset,
            onRequestFinishEarly = { confirmFinish = true },
            onStartRest = onStartRest,
            onClearSession = {
                onClearSession()
                showFullscreenPomodoro = false
            }
        )
    }

    // 提前结束当前番茄钟阶段确认弹窗
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("提前结束当前阶段？") },
            text = { Text("当前倒计时会立即标记为完成，之后仍由你确认下一步。") },
            confirmButton = {
                TextButton(onClick = {
                    onFinishEarly()
                    confirmFinish = false
                }) { Text("提前结束") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("继续计时") } }
        )
    }
}

/**
 * 独立幸运大转盘页面：具备顶部导航返回、加权轮盘绘制、旋转动效与事件池配置入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuckyWheelToolScreen(
    state: PomodoroUiState,
    onDraw: () -> Unit,
    onCarryToPomodoro: (String) -> Unit,
    onSavePoolItem: (String?, String, EventCategory, Int, Boolean) -> Unit,
    onSetPoolItemEnabled: (String, Boolean) -> Unit,
    onDeletePoolItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCount = state.poolItems.count { it.isEnabled }
    val totalCount = state.poolItems.size
    val segments = remember(state.poolItems) {
        EventPoolRules.wheelSegments(
            state.poolItems.map {
                EventPoolCandidate(it.id, it.title, it.category, it.isEnabled, it.weight)
            }
        )
    }
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var revealedCandidate by remember { mutableStateOf(state.selectedCandidate?.takeIf { state.drawVersion == 0L }) }

    var showPoolManager by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<EventPoolItemEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<EventPoolItemEntity?>(null) }

    LaunchedEffect(state.drawVersion, state.selectedCandidate?.id, segments) {
        val selected = state.selectedCandidate
        if (selected == null) {
            revealedCandidate = null
            isSpinning = false
            return@LaunchedEffect
        }
        if (state.drawVersion == 0L) {
            revealedCandidate = selected
            return@LaunchedEffect
        }

        revealedCandidate = null
        val target = EventPoolRules.targetRotation(
            currentRotation = rotation.value.toDouble(),
            selectedId = selected.id,
            segments = segments,
            fullTurns = if (segments.size == 1) 0 else 5
        ).toFloat()
        if (segments.size == 1) {
            rotation.snapTo(target % 360f)
        } else {
            isSpinning = true
            rotation.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 3_200, easing = LinearOutSlowInEasing)
            )
            rotation.snapTo(target % 360f)
        }
        isSpinning = false
        revealedCandidate = selected
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("幸运大转盘", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showPoolManager = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "管理事件池")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "加权随机抽奖",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "启用 $enabledCount / $totalCount 项",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    if (enabledCount == 0) {
                        Text(
                            "没有启用项目，请先添加或启用一项。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onDraw,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("抽一下")
                        }
                    } else {
                        Text(
                            "扇区大小就是本次独立抽取的概率。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LuckyWheel(
                            segments = segments,
                            rotation = rotation.value,
                            isSpinning = isSpinning,
                            onDraw = onDraw,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        WheelLegend(segments)

                        revealedCandidate?.let { candidate ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "抽中结果",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        candidate.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${candidate.category.displayName} · 权重 ${candidate.weight}") }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onCarryToPomodoro(candidate.title) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Timer, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("带入番茄钟")
                                        }
                                        OutlinedButton(
                                            onClick = onDraw,
                                            enabled = !isSpinning,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("再抽一次")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showPoolManager = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("管理事件池")
                    }
                }
            }
        }
    }

    // 事件池管理全屏/弹窗界面
    if (showPoolManager) {
        EventPoolManagerDialog(
            state = state,
            onDismiss = { showPoolManager = false },
            onAddNew = {
                editingItem = null
                showEditor = true
            },
            onEdit = {
                editingItem = it
                showEditor = true
            },
            onSetEnabled = onSetPoolItemEnabled,
            onDelete = { deletingItem = it }
        )
    }

    // 事件池项目新增/编辑弹窗
    if (showEditor) {
        PoolItemEditorDialog(
            item = editingItem,
            suggestions = state.poolNameSuggestions,
            poolItems = state.poolItems,
            onSave = { id, title, category, weight, enabled ->
                onSavePoolItem(id, title, category, weight, enabled)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    // 删除事件池项目确认弹窗
    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("删除事件池项目？") },
            text = { Text("“${item.title}”将从候选池中永久删除。") },
            confirmButton = {
                TextButton(onClick = { onDeletePoolItem(item.id); deletingItem = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("取消") } }
        )
    }
}

/**
 * 兼容旧入口与包含全量参数调用的包装函数。
 */
@Composable
fun ToolboxScreen(
    state: PomodoroUiState,
    notificationGranted: Boolean,
    onSavePoolItem: (String?, String, EventCategory, Int, Boolean) -> Unit,
    onSetPoolItemEnabled: (String, Boolean) -> Unit,
    onDeletePoolItem: (String) -> Unit,
    onDraw: () -> Unit,
    onStart: (String?, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    initialSection: String? = null,
    onBack: (() -> Unit)? = null
) {
    var activeSubscreen by rememberSaveable(initialSection) {
        mutableStateOf(
            when (initialSection) {
                SECTION_POOL -> SECTION_POOL
                SECTION_TIMER -> SECTION_TIMER
                else -> null
            }
        )
    }
    var carriedTitle by rememberSaveable { mutableStateOf("") }

    when (activeSubscreen) {
        SECTION_TIMER -> {
            PomodoroToolScreen(
                state = state,
                notificationGranted = notificationGranted,
                initialTitle = carriedTitle,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onReset = onReset,
                onFinishEarly = onFinishEarly,
                onStartRest = onStartRest,
                onClearSession = onClearSession,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onBack = {
                    if (onBack != null) onBack() else activeSubscreen = null
                },
                modifier = modifier
            )
        }
        SECTION_POOL -> {
            LuckyWheelToolScreen(
                state = state,
                onDraw = onDraw,
                onCarryToPomodoro = { title ->
                    carriedTitle = title
                    activeSubscreen = SECTION_TIMER
                },
                onSavePoolItem = onSavePoolItem,
                onSetPoolItemEnabled = onSetPoolItemEnabled,
                onDeletePoolItem = onDeletePoolItem,
                onBack = {
                    if (onBack != null) onBack() else activeSubscreen = null
                },
                modifier = modifier
            )
        }
        else -> {
            ToolboxScreen(
                state = state,
                onNavigateToPomodoro = { activeSubscreen = SECTION_TIMER },
                onNavigateToWheel = { activeSubscreen = SECTION_POOL },
                modifier = modifier
            )
        }
    }
}

/**
 * 兼容旧入口调用的包装函数，委托给 ToolboxScreen。
 */
@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    initialSection: String,
    notificationGranted: Boolean,
    onSavePoolItem: (String?, String, EventCategory, Int, Boolean) -> Unit,
    onSetPoolItemEnabled: (String, Boolean) -> Unit,
    onDeletePoolItem: (String) -> Unit,
    onDraw: () -> Unit,
    onStart: (String?, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    ToolboxScreen(
        state = state,
        notificationGranted = notificationGranted,
        onSavePoolItem = onSavePoolItem,
        onSetPoolItemEnabled = onSetPoolItemEnabled,
        onDeletePoolItem = onDeletePoolItem,
        onDraw = onDraw,
        onStart = onStart,
        onPause = onPause,
        onResume = onResume,
        onReset = onReset,
        onFinishEarly = onFinishEarly,
        onStartRest = onStartRest,
        onClearSession = onClearSession,
        onRequestNotificationPermission = onRequestNotificationPermission,
        modifier = modifier,
        initialSection = initialSection,
        onBack = onBack
    )
}

/**
 * 番茄钟专注模块卡片（保留作为可复用卡片组件）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PomodoroCard(
    state: PomodoroUiState,
    carriedTitle: String,
    notificationGranted: Boolean,
    onStart: (String?, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onRequestFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit,
    onExpandFullscreen: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.session
    var title by rememberSaveable(carriedTitle) { mutableStateOf(carriedTitle) }
    var focusText by rememberSaveable { mutableStateOf(state.settings.lastFocusMinutes.toString()) }
    var restText by rememberSaveable { mutableStateOf(state.settings.lastRestMinutes.toString()) }

    LaunchedEffect(carriedTitle) {
        if (carriedTitle.isNotBlank()) title = carriedTitle
    }
    LaunchedEffect(state.settings) {
        focusText = state.settings.lastFocusMinutes.toString()
        restText = state.settings.lastRestMinutes.toString()
    }

    val focus = focusText.toIntOrNull()
    val rest = restText.toIntOrNull()
    val valid = focus != null && focus in 1..180 && rest != null && rest in 1..60

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 卡片头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("番茄钟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (session != null) {
                    IconButton(onClick = onExpandFullscreen) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "展开全屏沉浸视图")
                    }
                }
            }

            HorizontalDivider()

            if (session == null) {
                // 未开始状态配置表单
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事项（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.poolNameSuggestions.isNotEmpty()) {
                    Text("从事件池带入名称", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.poolNameSuggestions.take(5).forEach { suggestion ->
                            FilterChip(
                                selected = title == suggestion,
                                onClick = { title = suggestion },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = focusText,
                        onValueChange = { focusText = it.filter(Char::isDigit) },
                        label = { Text("专注 (1–180 分钟)") },
                        isError = focus == null || focus !in 1..180,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = restText,
                        onValueChange = { restText = it.filter(Char::isDigit) },
                        label = { Text("休息 (1–60 分钟)") },
                        isError = rest == null || rest !in 1..60,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!notificationGranted) {
                    Text(
                        "未授权通知：倒计时仍会恢复，但后台阶段结束提醒无法显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (!notificationGranted) onRequestNotificationPermission()
                        onStart(title.ifBlank { null }, requireNotNull(focus), requireNotNull(rest))
                    },
                    enabled = valid && !state.operationInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始专注")
                }
            } else {
                // 进行中 / 暂停 / 已完成状态
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (session.phase == PomodoroPhase.FOCUS) "专注阶段" else "休息阶段",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )

                    session.title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        formatRemaining(state.remainingSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        when (session.state) {
                            PomodoroState.RUNNING -> "进行中"
                            PomodoroState.PAUSED -> "已暂停"
                            PomodoroState.COMPLETED -> "阶段已完成，等待你的确认"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 快捷操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (session.state) {
                            PomodoroState.RUNNING -> {
                                Button(
                                    onClick = onPause,
                                    enabled = !state.operationInProgress,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Pause, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("暂停")
                                }
                                OutlinedButton(
                                    onClick = onReset,
                                    enabled = !state.operationInProgress,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("重置")
                                }
                                TextButton(
                                    onClick = onRequestFinishEarly,
                                    enabled = !state.operationInProgress
                                ) {
                                    Text("提前结束")
                                }
                            }
                            PomodoroState.PAUSED -> {
                                Button(
                                    onClick = onResume,
                                    enabled = !state.operationInProgress,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("继续")
                                }
                                OutlinedButton(
                                    onClick = onReset,
                                    enabled = !state.operationInProgress,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("重置")
                                }
                                TextButton(
                                    onClick = onRequestFinishEarly,
                                    enabled = !state.operationInProgress
                                ) {
                                    Text("提前结束")
                                }
                            }
                            PomodoroState.COMPLETED -> {
                                if (session.phase == PomodoroPhase.FOCUS) {
                                    Button(
                                        onClick = onStartRest,
                                        enabled = !state.operationInProgress,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("开始休息")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onClearSession,
                                    enabled = !state.operationInProgress,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (session.phase == PomodoroPhase.REST) "完成并返回" else "结束本轮")
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "番茄钟仅辅助计时，不生成时间记录，也不进入统计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 事件抽奖卡片让加权算法、扇区比例和最终指针共用同一组领域几何结果。 */
@Composable
fun EventDrawCard(
    state: PomodoroUiState,
    onDraw: () -> Unit,
    onCarryToPomodoro: (String) -> Unit,
    onOpenPoolManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCount = state.poolItems.count { it.isEnabled }
    val totalCount = state.poolItems.size
    val segments = remember(state.poolItems) {
        EventPoolRules.wheelSegments(
            state.poolItems.map {
                EventPoolCandidate(it.id, it.title, it.category, it.isEnabled, it.weight)
            }
        )
    }
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var revealedCandidate by remember { mutableStateOf(state.selectedCandidate?.takeIf { state.drawVersion == 0L }) }

    LaunchedEffect(state.drawVersion, state.selectedCandidate?.id, segments) {
        val selected = state.selectedCandidate
        if (selected == null) {
            revealedCandidate = null
            isSpinning = false
            return@LaunchedEffect
        }
        if (state.drawVersion == 0L) {
            revealedCandidate = selected
            return@LaunchedEffect
        }

        revealedCandidate = null
        val target = EventPoolRules.targetRotation(
            currentRotation = rotation.value.toDouble(),
            selectedId = selected.id,
            segments = segments,
            fullTurns = if (segments.size == 1) 0 else 5
        ).toFloat()
        if (segments.size == 1) {
            rotation.snapTo(target % 360f)
        } else {
            isSpinning = true
            rotation.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 3_200, easing = LinearOutSlowInEasing)
            )
            rotation.snapTo(target % 360f)
        }
        isSpinning = false
        revealedCandidate = selected
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 卡片头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("事件池与抽奖", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    "启用 $enabledCount / $totalCount 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            if (enabledCount == 0) {
                Text(
                    "没有启用项目，请先添加或启用一项。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDraw,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("抽一下")
                }
            } else {
                Text(
                    "扇区大小就是本次独立抽取的概率。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LuckyWheel(
                    segments = segments,
                    rotation = rotation.value,
                    isSpinning = isSpinning,
                    onDraw = onDraw,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                WheelLegend(segments)

                revealedCandidate?.let { candidate ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "抽中结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                candidate.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${candidate.category.displayName} · 权重 ${candidate.weight}") }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onCarryToPomodoro(candidate.title) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("带入番茄钟")
                                }
                                OutlinedButton(
                                    onClick = onDraw,
                                    enabled = !isSpinning,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("再抽一次")
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenPoolManager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("管理事件池")
            }
        }
    }
}

/** Canvas 只负责扇区和标签，中心按钮独立于旋转图层以保持可操作。 */
@Composable
fun LuckyWheel(
    segments: List<WheelSegment>,
    rotation: Float,
    isSpinning: Boolean,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = segments.map { categoryColor(it.candidate.category) }
    val labelColor = Color.White
    Box(modifier = modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(250.dp)
                .aspectRatio(1f)
                .graphicsLayer { rotationZ = rotation }
        ) {
            segments.forEachIndexed { index, segment ->
                drawArc(
                    color = colors[index],
                    startAngle = (segment.startAngle - 90.0).toFloat(),
                    sweepAngle = segment.sweepAngle.toFloat(),
                    useCenter = true
                )
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = (segment.startAngle - 90.0).toFloat(),
                    sweepAngle = segment.sweepAngle.toFloat(),
                    useCenter = true,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                if (segment.sweepAngle >= 12.0 || segments.size == 1) {
                    val angleRadians = (segment.centerAngle - 90.0) * PI / 180.0
                    val radius = size.minDimension * 0.32f
                    val x = center.x + cos(angleRadians).toFloat() * radius
                    val y = center.y + sin(angleRadians).toFloat() * radius
                    val paint = Paint().apply {
                        color = labelColor.toArgb()
                        textAlign = Paint.Align.CENTER
                        textSize = 12.dp.toPx()
                        isFakeBoldText = true
                    }
                    val title = segment.candidate.title.take(6)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(title, x, y - 2.dp.toPx(), paint)
                        paint.textSize = 10.dp.toPx()
                        canvas.nativeCanvas.drawText(
                            percentageText(segment.percentage), x, y + 12.dp.toPx(), paint
                        )
                    }
                }
            }
        }
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = "转盘指针",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(42.dp)
        )
        Button(
            onClick = onDraw,
            enabled = !isSpinning,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(82.dp)
        ) {
            Text(if (isSpinning) "旋转中" else "抽一下", fontWeight = FontWeight.Bold)
        }
    }
}

/** 图例始终展示完整名称和精确占比，弥补极小扇区无法容纳文字的限制。 */
@Composable
fun WheelLegend(segments: List<WheelSegment>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = categoryColor(segment.candidate.category),
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(6.dp))
                Text(
                    "${segment.candidate.title} · 权重 ${segment.candidate.weight} · ${percentageText(segment.percentage)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun percentageText(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value)

private fun categoryColor(category: EventCategory): Color = when (category) {
    EventCategory.WORK -> Color(0xFF2563EB)
    EventCategory.STUDY -> Color(0xFF7C3AED)
    EventCategory.HIGH_QUALITY_ENTERTAINMENT -> Color(0xFF059669)
    EventCategory.LOW_QUALITY_ENTERTAINMENT -> Color(0xFFD97706)
    EventCategory.SOCIAL -> Color(0xFFDB2777)
    EventCategory.OTHER -> Color(0xFF64748B)
}

/**
 * 全屏沉浸式番茄钟专注视图。
 */
@Composable
fun PomodoroFullscreenDialog(
    session: PomodoroSession,
    remainingSeconds: Long,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onRequestFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "退出全屏")
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    if (session.phase == PomodoroPhase.FOCUS) "专注阶段" else "休息阶段",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))

                session.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    formatRemaining(remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    when (session.state) {
                        PomodoroState.RUNNING -> "保持专注，屏蔽干扰"
                        PomodoroState.PAUSED -> "计时已暂停"
                        PomodoroState.COMPLETED -> "阶段已完成，等待你的确认"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(32.dp))

                when (session.state) {
                    PomodoroState.RUNNING -> {
                        Button(
                            onClick = onPause,
                            enabled = !operationInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(Modifier.width(8.dp))
                            Text("暂停", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    PomodoroState.PAUSED -> {
                        Button(
                            onClick = onResume,
                            enabled = !operationInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("继续", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    PomodoroState.COMPLETED -> {
                        if (session.phase == PomodoroPhase.FOCUS) {
                            Button(
                                onClick = onStartRest,
                                enabled = !operationInProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text("开始休息", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        OutlinedButton(
                            onClick = onClearSession,
                            enabled = !operationInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (session.phase == PomodoroPhase.REST) "完成并返回" else "结束本轮")
                        }
                    }
                }

                if (session.state != PomodoroState.COMPLETED) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReset,
                            enabled = !operationInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重置")
                        }
                        TextButton(
                            onClick = onRequestFinishEarly,
                            enabled = !operationInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("提前结束")
                        }
                    }
                }

                Spacer(Modifier.weight(1.5f))
            }
        }
    }
}

/**
 * 事件池管理对话框：支持浏览、新增、修改、停用和删除事件项目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPoolManagerDialog(
    state: PomodoroUiState,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (EventPoolItemEntity) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDelete: (EventPoolItemEntity) -> Unit
) {
    val enabledWeight = state.poolItems.filter { it.isEnabled }.sumOf { it.weight }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("管理事件池") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Default.Add, contentDescription = "新增事件池项目")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!state.isLoading && state.poolItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "事件池还是空的，点击右下角添加第一个项目。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(state.poolItems, key = EventPoolItemEntity::id) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                val share = if (item.isEnabled && enabledWeight > 0) {
                                    100.0 * item.weight / enabledWeight
                                } else {
                                    0.0
                                }
                                Text(
                                    if (item.isEnabled) {
                                        "${item.category.displayName} · 权重 ${item.weight} · ${percentageText(share)}"
                                    } else {
                                        "${item.category.displayName} · 权重 ${item.weight} · 未参与"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = item.isEnabled,
                                onCheckedChange = { onSetEnabled(item.id, it) }
                            )
                            IconButton(onClick = { onEdit(item) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑${item.title}")
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除${item.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 编辑表单的历史快捷项只填名称，不联动覆盖事件性质。
 */
@Composable
fun PoolItemEditorDialog(
    item: EventPoolItemEntity?,
    suggestions: List<String>,
    poolItems: List<EventPoolItemEntity>,
    onSave: (String?, String, EventCategory, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category ?: EventCategory.WORK) }
    var enabled by rememberSaveable(item?.id) { mutableStateOf(item?.isEnabled ?: true) }
    var weight by rememberSaveable(item?.id) { mutableStateOf(item?.weight ?: 1) }
    val titleValid = title.isNotBlank()
    val otherEnabledWeight = poolItems
        .filter { it.isEnabled && it.id != item?.id }
        .sumOf { it.weight }
    val enabledWeight = otherEnabledWeight + if (enabled) weight else 0
    val currentShare = if (enabled && enabledWeight > 0) 100.0 * weight / enabledWeight else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增事件池项目" else "编辑事件池项目") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    isError = !titleValid,
                    supportingText = if (!titleValid) ({ Text("名称不能为空") }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Text("名称快捷选项")
                    suggestions.take(5).forEach { suggestion ->
                        FilterChip(
                            selected = title == suggestion,
                            onClick = { title = suggestion },
                            label = { Text(suggestion) }
                        )
                    }
                }
                Text("事件性质")
                EventCategory.selectable.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option.displayName) }
                    )
                }
                Text("权重")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { weight-- }, enabled = weight > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "减少权重")
                    }
                    Text(
                        weight.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    IconButton(onClick = { weight++ }, enabled = weight < 100) {
                        Icon(Icons.Default.Add, contentDescription = "增加权重")
                    }
                }
                Text(
                    if (enabled) "权重 $weight · 占 ${percentageText(currentShare)}" else "权重 $weight · 当前停用，不参与抽取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(item?.id, title, category, weight, enabled) },
                enabled = titleValid
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 主要页面共用的紧凑状态条，只展示阶段、事项和绝对时间派生的剩余值。
 */
@Composable
fun PomodoroCompactBar(session: PomodoroSession, remainingSeconds: Long, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Default.Timer, contentDescription = null)
        Spacer(Modifier.padding(start = 6.dp))
        val phase = if (session.phase == PomodoroPhase.FOCUS) "专注" else "休息"
        val state = if (session.state == PomodoroState.COMPLETED) "已完成" else formatRemaining(remainingSeconds)
        Text("$phase · ${session.title ?: "未命名事项"} · $state")
    }
}

private fun formatRemaining(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
