package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val weekdayLabels = mapOf(
    DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二", DayOfWeek.WEDNESDAY to "三",
    DayOfWeek.THURSDAY to "四", DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六",
    DayOfWeek.SUNDAY to "日"
)

/** 通用配置列表只负责选择编辑或确认删除，不承载实体表单逻辑。 */
@Composable
fun <T> EntityManagerDialog(
    title: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
    onAdd: () -> Unit,
    onEdit: (T) -> Unit,
    onDelete: (String) -> Unit,
    itemId: (T) -> String,
    onDismiss: () -> Unit
) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.84f)) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Add, null)
                    Text("新增")
                }
                if (items.isEmpty()) Text("还没有配置")
                items.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemContent(item)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEdit(item) }) { Text("编辑") }
                                TextButton(onClick = { deleteId = itemId(item) }) { Text("删除") }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("确认删除？") },
            text = { Text("删除后无法恢复；有关联课程的学期不能删除。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteId ?: return@TextButton
                    deleteId = null
                    onDelete(id)
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } }
        )
    }
}

@Composable
fun TermSummary(term: AcademicTermEntity) {
    Text(term.season.displayName(term.academicYearStart), style = MaterialTheme.typography.titleMedium)
    Text("${term.startDate}—${term.endDate}")
}

@Composable
fun TaskSummary(task: ScheduleTaskEntity) {
    Text(task.title, style = MaterialTheme.typography.titleMedium)
    val applicable = if (task.type == ScheduleType.WEEKLY) {
        task.weekdays.sortedBy { it.value }.joinToString("、") { "周${weekdayLabels[it]}" }
    } else task.date.toString()
    Text("${task.category.displayName} · $applicable · ${task.startTime}—${task.endTime}")
    Text(if (task.isEnabled) "已启用" else "已停用")
}

@Composable
fun CourseSummary(course: CourseScheduleEntity, terms: List<AcademicTermEntity>) {
    Text(course.courseName, style = MaterialTheme.typography.titleMedium)
    Text("${terms.firstOrNull { it.id == course.termId }?.toPeriod()?.displayName ?: "未知学期"} · ${course.location}")
    Text("第 ${course.startWeek}—${course.endWeek} 周 · ${course.startTime}—${course.endTime}")
}

/** 学期表单使用 ISO 日期文本，错误输入留在原字段供用户修正。 */
@Composable
fun TermEditorDialog(
    entity: AcademicTermEntity?,
    onSave: (String?, AcademicTermInput) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember(entity?.id) { mutableStateOf((entity?.academicYearStart ?: LocalDate.now().year).toString()) }
    var season by remember(entity?.id) { mutableStateOf(entity?.season ?: TermSeason.FALL) }
    var startDate by remember(entity?.id) { mutableStateOf(entity?.startDate?.toString() ?: "") }
    var endDate by remember(entity?.id) { mutableStateOf(entity?.endDate?.toString() ?: "") }
    var error by remember(entity?.id) { mutableStateOf<String?>(null) }
    EditorDialog(entity?.let { "编辑学期" } ?: "新增学期", onDismiss) {
        OutlinedTextField(year, { year = it; error = null }, label = { Text("学年起始年份") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermSeason.entries.forEach { value ->
                FilterChip(selected = season == value, onClick = { season = value }, label = { Text(if (value == TermSeason.FALL) "秋季" else "春季") })
            }
        }
        OutlinedTextField(startDate, { startDate = it; error = null }, label = { Text("开始日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(endDate, { endDate = it; error = null }, label = { Text("结束日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        FormError(error)
        Button(onClick = {
            runCatching {
                AcademicTermInput(year.toInt(), season, LocalDate.parse(startDate), LocalDate.parse(endDate))
            }.onSuccess { onSave(entity?.id, it) }
                .onFailure { error = "请填写四位年份和 YYYY-MM-DD 日期" }
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }
}

/** 普通事件表单保持 weekly 与 one_off 条件字段互斥，快捷名称只写入名称。 */
@Composable
fun TaskEditorDialog(
    entity: ScheduleTaskEntity?,
    suggestions: List<String>,
    onSave: (String?, ScheduleTaskInput) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(entity?.id) { mutableStateOf(entity?.title ?: "") }
    var category by remember(entity?.id) { mutableStateOf(entity?.category ?: EventCategory.WORK) }
    var type by remember(entity?.id) { mutableStateOf(entity?.type ?: ScheduleType.WEEKLY) }
    var weekdays by remember(entity?.id) { mutableStateOf(entity?.weekdays ?: setOf(DayOfWeek.MONDAY)) }
    var dateInput by remember(entity?.id) { mutableStateOf((entity?.effectiveFrom ?: entity?.date ?: LocalDate.now()).toString()) }
    var startTime by remember(entity?.id) { mutableStateOf(entity?.startTime?.toString() ?: "09:00") }
    var endTime by remember(entity?.id) { mutableStateOf(entity?.endTime?.toString() ?: "10:00") }
    var enabled by remember(entity?.id) { mutableStateOf(entity?.isEnabled ?: true) }
    var reminder by remember(entity?.id) { mutableStateOf(entity?.reminderEnabled ?: true) }
    var advance by remember(entity?.id) { mutableStateOf((entity?.reminderAdvanceMinutes ?: 5).toString()) }
    var error by remember(entity?.id) { mutableStateOf<String?>(null) }
    EditorDialog(entity?.let { "编辑普通事件" } ?: "新增普通事件", onDismiss) {
        OutlinedTextField(title, { title = it; error = null }, label = { Text("事件名称") }, modifier = Modifier.fillMaxWidth())
        SuggestionRow(suggestions) { title = it }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventCategory.selectable.forEach { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.displayName) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == ScheduleType.WEEKLY, onClick = { type = ScheduleType.WEEKLY }, label = { Text("每周循环") })
            FilterChip(selected = type == ScheduleType.ONE_OFF, onClick = { type = ScheduleType.ONE_OFF }, label = { Text("单次") })
        }
        if (type == ScheduleType.WEEKLY) WeekdaySelector(weekdays) { weekdays = it }
        OutlinedTextField(
            dateInput, { dateInput = it; error = null },
            label = { Text(if (type == ScheduleType.WEEKLY) "生效日期 YYYY-MM-DD" else "事件日期 YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth()
        )
        TimeFields(startTime, { startTime = it }, endTime, { endTime = it })
        BooleanRow("启用事件", enabled) { enabled = it }
        BooleanRow("启用提醒（M4 接入系统调度）", reminder) { reminder = it }
        if (reminder) OutlinedTextField(advance, { advance = it }, label = { Text("提前分钟数") }, modifier = Modifier.fillMaxWidth())
        FormError(error)
        Button(onClick = {
            runCatching {
                val date = LocalDate.parse(dateInput)
                require(title.isNotBlank()) { "事件名称不能为空" }
                ScheduleTaskInput(
                    title, category, type,
                    if (type == ScheduleType.WEEKLY) weekdays else emptySet(),
                    if (type == ScheduleType.WEEKLY) date else null,
                    if (type == ScheduleType.ONE_OFF) date else null,
                    LocalTime.parse(startTime), LocalTime.parse(endTime), enabled, reminder,
                    if (reminder) advance.toInt() else null
                )
            }.onSuccess { onSave(entity?.id, it) }
                .onFailure { error = it.message ?: "请检查日期、时间和必填项" }
        }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }
}

/** 课程表单固定学习性质，并在提交前按所选学期校验周次数值。 */
@Composable
fun CourseEditorDialog(
    entity: CourseScheduleEntity?,
    terms: List<AcademicTermEntity>,
    suggestions: List<String>,
    onSave: (String?, CourseScheduleInput) -> Unit,
    onDismiss: () -> Unit
) {
    var termId by remember(entity?.id, terms) { mutableStateOf(entity?.termId ?: terms.firstOrNull()?.id.orEmpty()) }
    var name by remember(entity?.id) { mutableStateOf(entity?.courseName ?: "") }
    var location by remember(entity?.id) { mutableStateOf(entity?.location ?: "") }
    var weekdays by remember(entity?.id) { mutableStateOf(entity?.weekdays ?: setOf(DayOfWeek.MONDAY)) }
    var startTime by remember(entity?.id) { mutableStateOf(entity?.startTime?.toString() ?: "08:00") }
    var endTime by remember(entity?.id) { mutableStateOf(entity?.endTime?.toString() ?: "09:30") }
    var startWeek by remember(entity?.id) { mutableStateOf((entity?.startWeek ?: 1).toString()) }
    var endWeek by remember(entity?.id) { mutableStateOf((entity?.endWeek ?: 1).toString()) }
    var reminder by remember(entity?.id) { mutableStateOf(entity?.reminderEnabled ?: true) }
    var advance by remember(entity?.id) { mutableStateOf((entity?.reminderAdvanceMinutes ?: 25).toString()) }
    var error by remember(entity?.id) { mutableStateOf<String?>(null) }
    EditorDialog(entity?.let { "编辑课程" } ?: "新增课程", onDismiss) {
        if (terms.isEmpty()) {
            Text("尚未设置学期，请先关闭表单并前往“学期”设置。", color = MaterialTheme.colorScheme.error)
        } else {
            Text("所属学期")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                terms.forEach { term ->
                    FilterChip(selected = termId == term.id, onClick = { termId = term.id }, label = { Text(term.toPeriod().displayName) })
                }
            }
        }
        OutlinedTextField(name, { name = it; error = null }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth())
        SuggestionRow(suggestions) { name = it }
        OutlinedTextField(location, { location = it; error = null }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth())
        Text("事件性质：学习")
        WeekdaySelector(weekdays) { weekdays = it }
        TimeFields(startTime, { startTime = it }, endTime, { endTime = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(startWeek, { startWeek = it }, label = { Text("开始周") }, modifier = Modifier.weight(1f))
            OutlinedTextField(endWeek, { endWeek = it }, label = { Text("结束周") }, modifier = Modifier.weight(1f))
        }
        BooleanRow("启用提醒（M4 接入系统调度）", reminder) { reminder = it }
        if (reminder) OutlinedTextField(advance, { advance = it }, label = { Text("提前分钟数") }, modifier = Modifier.fillMaxWidth())
        FormError(error)
        Button(
            enabled = terms.isNotEmpty(),
            onClick = {
                runCatching {
                    require(name.isNotBlank()) { "课程名称不能为空" }
                    require(location.isNotBlank()) { "地点不能为空" }
                    CourseScheduleInput(
                        termId, name, location, weekdays, LocalTime.parse(startTime), LocalTime.parse(endTime),
                        startWeek.toInt(), endWeek.toInt(), reminder, if (reminder) advance.toInt() else null
                    )
                }.onSuccess { onSave(entity?.id, it) }
                    .onFailure { error = it.message ?: "请检查时间、周次和必填项" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
    }
}

@Composable
fun OverlapConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("日程时间重叠") },
        text = { Text("该配置会与已有日程重叠。仍可保存，课表会同时显示全部事件。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("仍然保存") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("返回修改") } }
    )
}

/** 日程卡片先进入只读详情，再由用户明确跳转到对应配置表单。 */
@Composable
fun ScheduleDetailDialog(instance: ScheduleInstance, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val zoneId = ZoneId.systemDefault()
    val start = instance.startAt.atZone(zoneId)
    val end = instance.endAt.atZone(zoneId)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(instance.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${formatter.format(start)}—${DateTimeFormatter.ofPattern("HH:mm").format(end)}")
                Text("事件性质：${instance.category.displayName}")
                if (instance.source == ScheduleSource.COURSE) Text("地点：${instance.location}")
                Text("这里只查看计划，不会生成时间记录。")
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("前往编辑") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun EditorDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f)) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestions: List<String>, onSelect: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    Text("历史名称快捷选项", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { name -> FilterChip(false, { onSelect(name) }, label = { Text(name) }) }
    }
}

@Composable
private fun WeekdaySelector(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Text("星期（至少选择一天）")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(weekdayLabels.getValue(day)) }
            )
        }
    }
}

@Composable
private fun TimeFields(start: String, onStart: (String) -> Unit, end: String, onEnd: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, onStart, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f))
        OutlinedTextField(end, onEnd, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BooleanRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun FormError(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
}
