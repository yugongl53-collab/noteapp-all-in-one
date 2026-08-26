package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.rules.EventStreamItem
import com.yuncun.noteapp.domain.rules.ScheduleViewRules
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.schedule.ScheduleUiState
import com.yuncun.noteapp.ui.schedule.ScheduleViewMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.ui.statistics.StatisticsPeriod
import com.yuncun.noteapp.ui.statistics.StatisticsRanking
import com.yuncun.noteapp.ui.statistics.StatisticsUiState
import com.yuncun.noteapp.ui.statistics.TimeRecordDraftState

private enum class ScheduleManager { TASKS, COURSES, TERMS }
private val weekDateFormatter = DateTimeFormatter.ofPattern("M.d")
private val cardTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val chineseWeekdays = mapOf(
    DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二", DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四", DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日"
)

/** 日程首页整合课表、事件流与时间统计三大视图。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onSelectView: (ScheduleViewMode) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onSaveTerm: (String?, AcademicTermInput) -> Unit,
    onDeleteTerm: (String) -> Unit,
    onSaveTask: (String?, ScheduleTaskInput) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSaveCourse: (String?, CourseScheduleInput) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onConfirmOverlap: () -> Unit,
    onCancelOverlap: () -> Unit,
    modifier: Modifier = Modifier,
    reminderPermissions: ReminderPermissionState = ReminderPermissionState(true, true),
    onOpenReminderSettings: () -> Unit = {},
    statisticsState: StatisticsUiState? = null,
    statisticsDraft: TimeRecordDraftState? = null,
    onSelectPeriod: (StatisticsPeriod) -> Unit = {},
    onSelectRanking: (StatisticsRanking) -> Unit = {},
    onPreviousPeriod: () -> Unit = {},
    onNextPeriod: () -> Unit = {},
    onCurrentPeriod: () -> Unit = {},
    onRetryStatistics: () -> Unit = {},
    onAddRecord: () -> Unit = {},
    onEditRecord: (String) -> Unit = {},
    onDeleteRecord: (String) -> Unit = {},
    onUpdateRecordTitle: (String) -> Unit = {},
    onUpdateRecordCategory: (EventCategory) -> Unit = {},
    onUpdateRecordStartDate: (String) -> Unit = {},
    onUpdateRecordStartTime: (String) -> Unit = {},
    onUpdateRecordEndDate: (String) -> Unit = {},
    onUpdateRecordEndTime: (String) -> Unit = {},
    onSaveRecordDraft: () -> Unit = {},
    onDismissRecordEditor: () -> Unit = {}
) {
    var manager by remember { mutableStateOf<ScheduleManager?>(null) }
    var editingTerm by remember { mutableStateOf<AcademicTermEntity?>(null) }
    var editingTask by remember { mutableStateOf<ScheduleTaskEntity?>(null) }
    var editingCourse by remember { mutableStateOf<CourseScheduleEntity?>(null) }
    var createTerm by remember { mutableStateOf(false) }
    var createTask by remember { mutableStateOf(false) }
    var createCourse by remember { mutableStateOf(false) }
    var selectedInstance by remember { mutableStateOf<ScheduleInstance?>(null) }
    var observedCompletion by remember { mutableStateOf(state.actionCompletedVersion) }

    // 只有持久化真正成功后才关闭表单，数据库失败时继续保留用户输入。
    LaunchedEffect(state.actionCompletedVersion) {
        if (state.actionCompletedVersion > observedCompletion) {
            editingTerm = null; editingTask = null; editingCourse = null
            createTerm = false; createTask = false; createCourse = false
            observedCompletion = state.actionCompletedVersion
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { manager = ScheduleManager.TERMS },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.School, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(state.currentPeriodLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.viewMode == ScheduleViewMode.TIMETABLE,
                onClick = { onSelectView(ScheduleViewMode.TIMETABLE) },
                label = { Text("课表") }
            )
            FilterChip(
                selected = state.viewMode == ScheduleViewMode.EVENT_STREAM,
                onClick = { onSelectView(ScheduleViewMode.EVENT_STREAM) },
                label = { Text("事件流") }
            )
            FilterChip(
                selected = state.viewMode == ScheduleViewMode.STATISTICS,
                onClick = { onSelectView(ScheduleViewMode.STATISTICS) },
                label = { Text("时间统计") }
            )
        }
        if (state.viewMode == ScheduleViewMode.STATISTICS) {
            if (statisticsState != null && statisticsDraft != null) {
                StatisticsScreen(
                    state = statisticsState,
                    draft = statisticsDraft,
                    onSelectPeriod = onSelectPeriod,
                    onSelectRanking = onSelectRanking,
                    onPreviousPeriod = onPreviousPeriod,
                    onNextPeriod = onNextPeriod,
                    onCurrentPeriod = onCurrentPeriod,
                    onRetry = onRetryStatistics,
                    onAddRecord = onAddRecord,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = onDeleteRecord,
                    onUpdateTitle = onUpdateRecordTitle,
                    onUpdateCategory = onUpdateRecordCategory,
                    onUpdateStartDate = onUpdateRecordStartDate,
                    onUpdateStartTime = onUpdateRecordStartTime,
                    onUpdateEndDate = onUpdateRecordEndDate,
                    onUpdateEndTime = onUpdateRecordEndTime,
                    onSaveDraft = onSaveRecordDraft,
                    onDismissEditor = onDismissRecordEditor
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("统计数据加载中…")
                }
            }
        } else {
            WeekControls(state, onPreviousWeek, onNextWeek, onCurrentWeek)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { manager = ScheduleManager.TASKS }) {
                    Icon(Icons.Default.Event, null); Text("普通事件")
                }
                OutlinedButton(onClick = { manager = ScheduleManager.COURSES }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null); Text("课程")
                }
            }
            val hasConfiguredReminder = state.tasks.any { it.isEnabled && it.reminderEnabled } ||
                state.courses.any { it.reminderEnabled }
            if (hasConfiguredReminder && !reminderPermissions.isEffective) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "提醒已配置但未生效：缺少${reminderPermissions.missingReason()}",
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onOpenReminderSettings) { Text("提醒设置") }
                    }
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.viewMode == ScheduleViewMode.TIMETABLE -> Timetable(state) { selectedInstance = it }
                else -> EventStream(state.eventStream) { selectedInstance = it }
            }
        }
    }

    when (manager) {
        ScheduleManager.TASKS -> EntityManagerDialog(
            title = "普通事件配置",
            items = state.tasks.sortedWith(compareBy({ it.type }, { it.title }, { it.id })),
            itemContent = { TaskSummary(it, reminderPermissions) },
            onAdd = { createTask = true },
            onEdit = { editingTask = it },
            onDelete = onDeleteTask,
            itemId = { it.id },
            onDismiss = { manager = null }
        )
        ScheduleManager.COURSES -> EntityManagerDialog(
            title = "课程配置",
            items = state.courses.sortedWith(compareBy({ it.termId }, { it.courseName }, { it.id })),
            itemContent = { CourseSummary(it, state.terms, reminderPermissions) },
            onAdd = { createCourse = true },
            onEdit = { editingCourse = it },
            onDelete = onDeleteCourse,
            itemId = { it.id },
            onDismiss = { manager = null }
        )
        ScheduleManager.TERMS -> EntityManagerDialog(
            title = "学期设置",
            items = state.terms,
            itemContent = { TermSummary(it) },
            onAdd = { createTerm = true },
            onEdit = { editingTerm = it },
            onDelete = onDeleteTerm,
            itemId = { it.id },
            onDismiss = { manager = null }
        )
        null -> Unit
    }

    if (createTerm || editingTerm != null) TermEditorDialog(editingTerm, onSaveTerm) {
        createTerm = false; editingTerm = null
    }
    if (createTask || editingTask != null) TaskEditorDialog(
        entity = editingTask,
        suggestions = state.taskNameSuggestions,
        onSave = onSaveTask,
        onDismiss = { createTask = false; editingTask = null },
        reminderPermissions = reminderPermissions,
        onOpenReminderSettings = onOpenReminderSettings
    )
    if (createCourse || editingCourse != null) CourseEditorDialog(
        entity = editingCourse,
        terms = state.terms,
        suggestions = state.courseNameSuggestions,
        onSave = onSaveCourse,
        onDismiss = { createCourse = false; editingCourse = null },
        reminderPermissions = reminderPermissions,
        onOpenReminderSettings = onOpenReminderSettings
    )
    if (state.overlapConfirmationRequired) OverlapConfirmationDialog(onConfirmOverlap, onCancelOverlap)
    selectedInstance?.let { instance ->
        ScheduleDetailDialog(
            instance = instance,
            onEdit = {
                if (instance.source == ScheduleSource.TASK) {
                    editingTask = state.tasks.firstOrNull { it.id == instance.sourceId }
                } else {
                    editingCourse = state.courses.firstOrNull { it.id == instance.sourceId }
                }
                selectedInstance = null
            },
            onDelete = {
                if (instance.source == ScheduleSource.TASK) {
                    onDeleteTask(instance.sourceId)
                } else {
                    onDeleteCourse(instance.sourceId)
                }
                selectedInstance = null
            },
            onDismiss = { selectedInstance = null }
        )
    }
}

@Composable
private fun WeekControls(
    state: ScheduleUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    val monday = state.selectedWeek
    val sunday = monday.plusDays(6)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "上一周") }
            Text("${weekDateFormatter.format(monday)}—${weekDateFormatter.format(sunday)}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "下一周") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onCurrent) { Text("本周") }
        }
        Text(
            state.weekLabels.ifEmpty { listOf("尚未设置学期") }.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 七个固定日列允许横向与纵向双向滚动，重叠实例仍以独立卡片完整展示。 */
@Composable
private fun Timetable(state: ScheduleUiState, onOpen: (ScheduleInstance) -> Unit) {
    val zoneId = ZoneId.systemDefault()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0L..6L).forEach { offset ->
            val date = state.selectedWeek.plusDays(offset)
            val dayInstances = state.instances.filter { it.startAt.atZone(zoneId).toLocalDate() == date }
            Column(modifier = Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${chineseWeekdays[date.dayOfWeek]} ${weekDateFormatter.format(date)}", style = MaterialTheme.typography.titleSmall)
                if (dayInstances.isEmpty()) {
                    Text("无事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dayInstances.forEach { instance ->
                        InstanceCard(instance, instance.sourceId in state.overlappingIds, onOpen)
                    }
                }
            }
        }
    }
}

/** 事件流采用纵向卡片列表，自动按上下午（以 12:00 为界）分块排布并以空白间距自然隔开，无显式文字标签。 */
@Composable
private fun EventStream(items: List<EventStreamItem>, onOpen: (ScheduleInstance) -> Unit) {
    if (items.isEmpty()) {
        Text("本周没有接下来的事件", modifier = Modifier.padding(top = 24.dp))
        return
    }
    val chunks = remember(items) { ScheduleViewRules.chunkEventStream(items) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        chunks.forEach { chunk ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { item ->
                    val border = if (item.isNext) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    Card(
                        onClick = { onOpen(item.instance) },
                        modifier = Modifier.fillMaxWidth(),
                        border = border
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (item.isOngoing) Text("进行中", color = MaterialTheme.colorScheme.tertiary)
                            if (item.isNext) Text("下一个事件", color = MaterialTheme.colorScheme.primary)
                            InstanceText(item.instance, includeDate = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: ScheduleInstance, overlapping: Boolean, onOpen: (ScheduleInstance) -> Unit) {
    Card(
        onClick = { onOpen(instance) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            InstanceText(instance, includeDate = false)
            if (overlapping) Text("与其他日程重叠", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InstanceText(instance: ScheduleInstance, includeDate: Boolean) {
    val start = instance.startAt.atZone(ZoneId.systemDefault())
    val end = instance.endAt.atZone(ZoneId.systemDefault())
    if (includeDate) Text("${weekDateFormatter.format(start)} ${chineseWeekdays[start.dayOfWeek]}")
    Text(instance.title, style = MaterialTheme.typography.titleMedium)
    if (instance.source == ScheduleSource.COURSE) Text("地点：${instance.location}")
    Text("${cardTimeFormatter.format(start)}—${cardTimeFormatter.format(end)}")
    Text(instance.category.displayName, style = MaterialTheme.typography.labelMedium)
}
