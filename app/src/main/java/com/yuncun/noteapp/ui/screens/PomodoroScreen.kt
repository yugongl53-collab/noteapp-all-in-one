package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.ui.pomodoro.PomodoroUiState

/** M5 工具箱承载事件池抽取与纯番茄计时，两条流程只通过可选事项名称连接。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    initialSection: String,
    notificationGranted: Boolean,
    onSavePoolItem: (String?, String, EventCategory, Boolean) -> Unit,
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
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var editingItem by remember { mutableStateOf<EventPoolItemEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<EventPoolItemEntity?>(null) }
    var carriedTitle by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("工具箱") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (section == SECTION_POOL) {
                FloatingActionButton(onClick = { editingItem = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增事件池项目")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionButton("事件池与抽奖", section == SECTION_POOL, Modifier.weight(1f)) { section = SECTION_POOL }
                SectionButton("番茄钟", section == SECTION_TIMER, Modifier.weight(1f)) { section = SECTION_TIMER }
            }
            HorizontalDivider()
            if (section == SECTION_POOL) {
                EventPoolContent(
                    state = state,
                    onDraw = onDraw,
                    onEdit = { editingItem = it; showEditor = true },
                    onSetEnabled = onSetPoolItemEnabled,
                    onDelete = { deletingItem = it },
                    onCarryToPomodoro = { candidate ->
                        carriedTitle = candidate.title
                        section = SECTION_TIMER
                    }
                )
            } else {
                PomodoroContent(
                    state = state,
                    carriedTitle = carriedTitle,
                    notificationGranted = notificationGranted,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onReset = onReset,
                    onFinishEarly = onFinishEarly,
                    onStartRest = onStartRest,
                    onClearSession = onClearSession,
                    onRequestNotificationPermission = onRequestNotificationPermission
                )
            }
        }
    }

    if (showEditor) {
        PoolItemEditorDialog(
            item = editingItem,
            suggestions = state.poolNameSuggestions,
            onSave = { id, title, category, enabled ->
                onSavePoolItem(id, title, category, enabled)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
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

@Composable
private fun SectionButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun EventPoolContent(
    state: PomodoroUiState,
    onDraw: () -> Unit,
    onEdit: (EventPoolItemEntity) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDelete: (EventPoolItemEntity) -> Unit,
    onCarryToPomodoro: (EventPoolCandidate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("让系统帮我选一个", style = MaterialTheme.typography.titleLarge)
                    if (state.selectedCandidate == null) {
                        Text(
                            if (state.poolItems.any { it.isEnabled }) "每次抽取独立且等概率。"
                            else "没有启用项目，请先添加或启用一项。"
                        )
                    } else {
                        Text(state.selectedCandidate.title, style = MaterialTheme.typography.headlineSmall)
                        Text(state.selectedCandidate.category.displayName)
                        Button(onClick = { onCarryToPomodoro(state.selectedCandidate) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Text("带入番茄钟", Modifier.padding(start = 8.dp))
                        }
                    }
                    Button(onClick = onDraw, enabled = state.poolItems.any { it.isEnabled }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(if (state.selectedCandidate == null) "帮我选一个" else "再抽一次", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { Text("事件池管理", style = MaterialTheme.typography.titleLarge) }
        if (!state.isLoading && state.poolItems.isEmpty()) {
            item { Text("事件池还是空的，点击右下角添加第一个项目。") }
        }
        items(state.poolItems, key = EventPoolItemEntity::id) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.category.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = item.isEnabled, onCheckedChange = { onSetEnabled(item.id, it) })
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, contentDescription = "编辑${item.title}") }
                    IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, contentDescription = "删除${item.title}") }
                }
            }
        }
    }
}

/** 编辑表单的历史快捷项只填名称，不联动覆盖事件性质。 */
@Composable
fun PoolItemEditorDialog(
    item: EventPoolItemEntity?,
    suggestions: List<String>,
    onSave: (String?, String, EventCategory, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category ?: EventCategory.WORK) }
    var enabled by rememberSaveable(item?.id) { mutableStateOf(item?.isEnabled ?: true) }
    val titleValid = title.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增事件池项目" else "编辑事件池项目") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        FilterChip(selected = title == suggestion, onClick = { title = suggestion }, label = { Text(suggestion) })
                    }
                }
                Text("事件性质")
                EventCategory.selectable.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.displayName) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(item?.id, title, category, enabled) }, enabled = titleValid) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PomodoroContent(
    state: PomodoroUiState,
    carriedTitle: String,
    notificationGranted: Boolean,
    onStart: (String?, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val session = state.session
    if (session == null) {
        PomodoroSetup(state, carriedTitle, notificationGranted, onStart, onRequestNotificationPermission)
    } else {
        ActivePomodoro(
            session, state.remainingSeconds, state.operationInProgress,
            onPause, onResume, onReset, onFinishEarly, onStartRest, onClearSession
        )
    }
}

@Composable
private fun PomodoroSetup(
    state: PomodoroUiState,
    carriedTitle: String,
    notificationGranted: Boolean,
    onStart: (String?, Int, Int) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(carriedTitle) }
    var focusText by rememberSaveable { mutableStateOf(state.settings.lastFocusMinutes.toString()) }
    var restText by rememberSaveable { mutableStateOf(state.settings.lastRestMinutes.toString()) }
    LaunchedEffect(carriedTitle) { if (carriedTitle.isNotBlank()) title = carriedTitle }
    LaunchedEffect(state.settings) {
        focusText = state.settings.lastFocusMinutes.toString()
        restText = state.settings.lastRestMinutes.toString()
    }
    val focus = focusText.toIntOrNull()
    val rest = restText.toIntOrNull()
    val valid = focus != null && focus in 1..180 && rest != null && rest in 1..60
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("开始一轮专注", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("事项（可选）") }, modifier = Modifier.fillMaxWidth())
        if (state.poolNameSuggestions.isNotEmpty()) {
            Text("从事件池带入名称")
            state.poolNameSuggestions.take(5).forEach { suggestion ->
                FilterChip(selected = title == suggestion, onClick = { title = suggestion }, label = { Text(suggestion) })
            }
        }
        OutlinedTextField(
            focusText, { focusText = it.filter(Char::isDigit) }, label = { Text("专注分钟（1–180）") },
            isError = focus == null || focus !in 1..180, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            restText, { restText = it.filter(Char::isDigit) }, label = { Text("休息分钟（1–60）") },
            isError = rest == null || rest !in 1..60, modifier = Modifier.fillMaxWidth()
        )
        if (!notificationGranted) Text("未授权通知：倒计时仍会恢复，但后台阶段结束提醒无法显示。")
        Button(
            onClick = {
                if (!notificationGranted) onRequestNotificationPermission()
                onStart(title.ifBlank { null }, requireNotNull(focus), requireNotNull(rest))
            },
            enabled = valid && !state.operationInProgress,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始专注") }
        Text("番茄钟仅辅助计时，不生成时间记录，也不进入统计。")
    }
}

@Composable
private fun ActivePomodoro(
    session: PomodoroSession,
    remainingSeconds: Long,
    operationInProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit,
    onStartRest: () -> Unit,
    onClearSession: () -> Unit
) {
    var confirmFinish by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(if (session.phase == PomodoroPhase.FOCUS) "专注阶段" else "休息阶段", style = MaterialTheme.typography.titleLarge)
        session.title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) }
        Text(formatRemaining(remainingSeconds), style = MaterialTheme.typography.displayLarge)
        Text(
            when (session.state) {
                PomodoroState.RUNNING -> "进行中"
                PomodoroState.PAUSED -> "已暂停"
                PomodoroState.COMPLETED -> "阶段已完成，等待你的确认"
            }
        )
        when (session.state) {
            PomodoroState.RUNNING -> Button(onClick = onPause, enabled = !operationInProgress) { Text("暂停") }
            PomodoroState.PAUSED -> Button(onClick = onResume, enabled = !operationInProgress) { Text("继续") }
            PomodoroState.COMPLETED -> {
                if (session.phase == PomodoroPhase.FOCUS) {
                    Button(onClick = onStartRest, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
                        Text("开始休息")
                    }
                }
                Button(onClick = onClearSession, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
                    Text(if (session.phase == PomodoroPhase.REST) "完成并返回" else "结束本轮")
                }
            }
        }
        if (session.state != PomodoroState.COMPLETED) {
            OutlinedButton(onClick = onReset, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
                Text("重置当前阶段")
            }
            TextButton(onClick = { confirmFinish = true }, enabled = !operationInProgress) { Text("提前结束") }
        }
        Text("阶段之间不会自动切换，计时结果不会保存为时间记录。")
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("提前结束当前阶段？") },
            text = { Text("当前倒计时会立即标记为完成，之后仍由你确认下一步。") },
            confirmButton = {
                TextButton(onClick = { onFinishEarly(); confirmFinish = false }) { Text("提前结束") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("继续计时") } }
        )
    }
}

/** 主要页面共用的紧凑状态条，只展示阶段、事项和绝对时间派生的剩余值。 */
@Composable
fun PomodoroCompactBar(session: PomodoroSession, remainingSeconds: Long, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Icon(Icons.Default.Timer, contentDescription = null)
        Spacer(Modifier.padding(start = 6.dp))
        val phase = if (session.phase == PomodoroPhase.FOCUS) "专注" else "休息"
        val state = if (session.state == PomodoroState.COMPLETED) "已完成" else formatRemaining(remainingSeconds)
        Text("$phase · ${session.title ?: "未命名事项"} · $state")
    }
}

private fun formatRemaining(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

const val SECTION_POOL = "pool"
const val SECTION_TIMER = "timer"
