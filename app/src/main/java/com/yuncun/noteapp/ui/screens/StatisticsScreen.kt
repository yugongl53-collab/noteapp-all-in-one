package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TimeStatistics
import com.yuncun.noteapp.ui.statistics.StatisticsPeriod
import com.yuncun.noteapp.ui.statistics.StatisticsRanking
import com.yuncun.noteapp.ui.statistics.StatisticsUiState
import com.yuncun.noteapp.ui.statistics.TimeRecordDraftState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** M6 统计页：展示手动时间记录生成的日周榜单与明细。 */
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    draft: TimeRecordDraftState,
    onSelectPeriod: (StatisticsPeriod) -> Unit,
    onSelectRanking: (StatisticsRanking) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onCurrentPeriod: () -> Unit,
    onRetry: () -> Unit,
    onAddRecord: () -> Unit,
    onEditRecord: (String) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateCategory: (EventCategory) -> Unit,
    onUpdateStartDate: (String) -> Unit,
    onUpdateStartTime: (String) -> Unit,
    onUpdateEndDate: (String) -> Unit,
    onUpdateEndTime: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onDismissEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val statistics = state.statistics

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("时间统计", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("统计只来自手动录入的实际时间记录。", style = MaterialTheme.typography.bodySmall)
        PeriodSelector(state.period, onSelectPeriod)
        RangeNavigator(state, onPreviousPeriod, onNextPeriod, onCurrentPeriod)

        if (state.isLoading && statistics == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (state.loadError != null && statistics == null) {
            ErrorCard(state.loadError, onRetry)
        } else if (statistics != null) {
            if (state.loadError != null) ErrorCard(state.loadError, onRetry)
            RankingSelector(state.ranking, onSelectRanking)
            RankingList(statistics, state.ranking)
            if (state.period == StatisticsPeriod.WEEK) DailyBreakdown(statistics)
            FilledTonalButton(onClick = onAddRecord, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("手动录入")
            }
            RecordList(
                records = state.visibleRecords,
                onEdit = onEditRecord,
                onDelete = { pendingDeleteId = it }
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (draft.isOpen) {
        TimeRecordEditorDialog(
            draft = draft,
            suggestions = state.nameSuggestions,
            onTitleChange = onUpdateTitle,
            onCategoryChange = onUpdateCategory,
            onStartDateChange = onUpdateStartDate,
            onStartTimeChange = onUpdateStartTime,
            onEndDateChange = onUpdateEndDate,
            onEndTimeChange = onUpdateEndTime,
            onSave = onSaveDraft,
            onDismiss = onDismissEditor
        )
    }
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("永久删除时间记录？") },
            text = { Text("永久删除后无法恢复，相关日周统计和快捷名称会立即重算。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    onDeleteRecord(id)
                }) { Text("永久删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PeriodSelector(period: StatisticsPeriod, onSelect: (StatisticsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(period == StatisticsPeriod.DAY, { onSelect(StatisticsPeriod.DAY) }, label = { Text("日") })
        FilterChip(period == StatisticsPeriod.WEEK, { onSelect(StatisticsPeriod.WEEK) }, label = { Text("周") })
    }
}

@Composable
private fun RangeNavigator(
    state: StatisticsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    val statistics = state.statistics
    val label = if (state.period == StatisticsPeriod.DAY || statistics == null) {
        state.selectedDate.toString()
    } else {
        "${statistics.startDate} 至 ${statistics.endDate}"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, contentDescription = "上一时段") }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onCurrent) { Icon(Icons.Default.Today, contentDescription = "回到当前时段") }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, contentDescription = "下一时段") }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun RankingSelector(ranking: StatisticsRanking, onSelect: (StatisticsRanking) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            ranking == StatisticsRanking.CATEGORY,
            { onSelect(StatisticsRanking.CATEGORY) },
            label = { Text("按事件性质") }
        )
        FilterChip(
            ranking == StatisticsRanking.TITLE,
            { onSelect(StatisticsRanking.TITLE) },
            label = { Text("按事件名称") }
        )
    }
}

@Composable
private fun RankingList(statistics: TimeStatistics, ranking: StatisticsRanking) {
    val isEmpty = if (ranking == StatisticsRanking.CATEGORY) {
        statistics.categoryRanking.isEmpty()
    } else {
        statistics.titleRanking.isEmpty()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("排行榜", style = MaterialTheme.typography.titleMedium)
            if (isEmpty) {
                Text("该时段还没有时间记录。")
            } else if (ranking == StatisticsRanking.CATEGORY) {
                statistics.categoryRanking.forEach { item ->
                    RankingRow(item.rank, item.category.displayName, item.minutes)
                }
            } else {
                statistics.titleRanking.forEach { item -> RankingRow(item.rank, item.title, item.minutes) }
            }
        }
    }
}

@Composable
private fun RankingRow(rank: Int, label: String, minutes: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank.", modifier = Modifier.width(32.dp), fontWeight = FontWeight.Bold)
        Text(label, modifier = Modifier.weight(1f))
        Text(formatMinutes(minutes), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DailyBreakdown(statistics: TimeStatistics) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("每日精确时长", style = MaterialTheme.typography.titleMedium)
            statistics.dailySummaries.forEach { day ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("MM-dd E")), modifier = Modifier.weight(1f))
                    Text(formatMinutes(day.recordedMinutes), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RecordList(
    records: List<TimeRecordEntity>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Text("本时段记录明细", style = MaterialTheme.typography.titleMedium)
    if (records.isEmpty()) {
        Text("暂无明细，点击“手动录入”记录实际活动。")
        return
    }
    records.forEach { record ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(record.title, fontWeight = FontWeight.Medium)
                    Text(record.category.displayName, style = MaterialTheme.typography.bodySmall)
                    Text(formatRange(record), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onEdit(record.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑${record.title}")
                }
                IconButton(onClick = { onDelete(record.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除${record.title}")
                }
            }
        }
    }
}

/** 录入弹窗保留原始文本，格式或持久化失败后用户可以原地修正并重试。 */
@Composable
fun TimeRecordEditorDialog(
    draft: TimeRecordDraftState,
    suggestions: List<String>,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (EventCategory) -> Unit,
    onStartDateChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "手动录入时间" else "编辑时间记录") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitleChange,
                    label = { Text("事件名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Text("快捷名称", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            FilterChip(
                                selected = draft.title == suggestion,
                                onClick = { onTitleChange(suggestion) },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
                Text("事件性质", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventCategory.selectable.forEach { category ->
                        FilterChip(
                            selected = draft.category == category,
                            onClick = { onCategoryChange(category) },
                            label = { Text(category.displayName) }
                        )
                    }
                }
                HorizontalDivider()
                DateTimeFields("开始", draft.startDate, draft.startTime, onStartDateChange, onStartTimeChange)
                DateTimeFields("结束", draft.endDate, draft.endTime, onEndDateChange, onEndTimeChange)
                draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !draft.isSaving) {
                Text(if (draft.isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !draft.isSaving) { Text("取消") } }
    )
}

@Composable
private fun DateTimeFields(
    prefix: String,
    date: String,
    time: String,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedDateField(
            value = date,
            onValueChange = onDateChange,
            label = "${prefix}日期 YYYY-MM-DD",
            includeYear = true,
            modifier = Modifier.weight(1.35f)
        )
        SegmentedTimeField(
            value = time,
            onValueChange = onTimeChange,
            label = "${prefix}时间 HH:mm",
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours == 0L -> "${remaining}分钟"
        remaining == 0L -> "${hours}小时"
        else -> "${hours}小时${remaining}分钟"
    }
}

private fun formatRange(record: TimeRecordEntity): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val zone = ZoneId.systemDefault()
    return "${formatter.format(record.startAt.atZone(zone))} — ${formatter.format(record.endAt.atZone(zone))}"
}
