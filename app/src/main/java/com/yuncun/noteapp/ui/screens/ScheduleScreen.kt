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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.schedule.ScheduleUiState
import com.yuncun.noteapp.ui.schedule.ScheduleViewMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ScheduleManager { TASKS, COURSES, TERMS }
private val weekDateFormatter = DateTimeFormatter.ofPattern("M.d")
private val cardTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val chineseWeekdays = mapOf(
    DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二", DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四", DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日"
)

/** M3 日程首页只负责查看和打开配置入口，所有写入仍在独立表单中完成。 */
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
    reminderPermissions: ReminderPermissionState = ReminderPermissionState(true, true),
    onOpenReminderSettings: () -> Unit = {},
    modifier: Modifier = Modifier
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("日程") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            }
            WeekControls(state, onPreviousWeek, onNextWeek, onCurrentWeek)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { manager = ScheduleManager.TASKS }) {
                    Icon(Icons.Default.Event, null); Text("普通事件")
                }
                OutlinedButton(onClick = { manager = ScheduleManager.COURSES }) {
                    Icon(Icons.Default.MenuBook, null); Text("课程")
                }
                OutlinedButton(onClick = { manager = ScheduleManager.TERMS }) {
                    Icon(Icons.Default.School, null); Text("学期")
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

/** 七个固定日列允许横向滚动，重叠实例仍以独立卡片完整展示。 */
@Composable
private fun Timetable(state: ScheduleUiState, onOpen: (ScheduleInstance) -> Unit) {
    val zoneId = ZoneId.systemDefault()
    Row(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
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

@Composable
private fun EventStream(items: List<EventStreamItem>, onOpen: (ScheduleInstance) -> Unit) {
    if (items.isEmpty()) {
        Text("本周没有接下来的事件", modifier = Modifier.padding(top = 24.dp))
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            val border = if (item.isNext) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            Card(onClick = { onOpen(item.instance) }, modifier = Modifier.width(220.dp), border = border) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isOngoing) Text("进行中", color = MaterialTheme.colorScheme.tertiary)
                    if (item.isNext) Text("下一个事件", color = MaterialTheme.colorScheme.primary)
                    InstanceText(item.instance, includeDate = true)
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
