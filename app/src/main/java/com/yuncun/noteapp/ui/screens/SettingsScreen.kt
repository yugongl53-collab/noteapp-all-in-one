package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.R
import com.yuncun.noteapp.domain.model.AppThemeMode
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.backup.BackupUiState

/** 设置页提供外观主题切换、Android 系统权限状态与通俗化的数据备份恢复。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    reminderPermissions: ReminderPermissionState = ReminderPermissionState(),
    backupState: BackupUiState = BackupUiState(),
    onBack: (() -> Unit)? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onRequestExport: () -> Unit = {},
    onDismissExportWarning: () -> Unit = {},
    onConfirmExportWarning: () -> Unit = {},
    onChooseImportFile: () -> Unit = {},
    onDismissImportConfirmation: () -> Unit = {},
    onConfirmImport: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 品牌信息与官方 Logo 展示
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "一站笔记 Logo",
                        modifier = Modifier.size(56.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "一站笔记",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "一站式个人效率工具 · 灵感 · 日程 · 专注 · 统计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 外观主题切换
            Text("外观主题", style = MaterialTheme.typography.headlineSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            AppThemeMode.SYSTEM -> "📱 跟随系统"
                            AppThemeMode.LIGHT -> "☀️ 浅色模式"
                            AppThemeMode.DARK -> "🌙 深色模式"
                        }
                        val selected = currentThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    onClick = { onThemeModeChange(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        text = "跟随系统模式下，Android 12+ 设备将自动适配系统壁纸动态取色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text("提醒权限", style = MaterialTheme.typography.headlineSmall)
            Text(
                "番茄钟阶段提醒需要通知权限；课程和普通事件还需要“闹钟和提醒”权限。权限不足不会阻止保存或计时。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionCard(
                title = "通知权限",
                granted = reminderPermissions.notificationGranted,
                explanation = "允许应用显示番茄钟阶段结束、课程和普通事件提醒。",
                actionLabel = "授予通知权限",
                onAction = onRequestNotificationPermission
            )
            PermissionCard(
                title = "闹钟和提醒权限",
                granted = reminderPermissions.exactAlarmGranted,
                explanation = "允许 Android 在指定时刻唤醒并触发日程提醒。",
                actionLabel = "打开系统设置",
                onAction = onOpenExactAlarmSettings
            )
            Text("数据备份与恢复", style = MaterialTheme.typography.headlineSmall)
            Text(
                "将您所有的笔记、日程、专注历史和统计记录打包保存为一个备份文件。该文件可以存放在手机或发送给其他设备，请妥善保管。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRequestExport, enabled = !backupState.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("导出数据备份")
            }
            OutlinedButton(onClick = onChooseImportFile, enabled = !backupState.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("从备份文件恢复数据")
            }
            if (backupState.isBusy) Text("正在处理，请稍候……")
        }
    }

    if (backupState.showExportWarning) {
        AlertDialog(
            onDismissRequest = onDismissExportWarning,
            title = { Text("导出数据备份") },
            text = { Text("将您所有的笔记、日程、专注历史和统计记录打包保存为一个备份文件。该文件可以存放在手机或发送给其他设备，请妥善保管。") },
            confirmButton = { TextButton(onClick = onConfirmExportWarning) { Text("选择保存位置") } },
            dismissButton = { TextButton(onClick = onDismissExportWarning) { Text("取消") } }
        )
    }
    backupState.pendingImport?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissImportConfirmation,
            title = { Text("确认恢复数据？") },
            text = {
                Text(
                    "从已有的备份文件中恢复数据。\n\n" +
                        "备份文件：${summary.fileName}\n" +
                        "包含：灵感笔记 ${summary.ideaCount} 条、日程事件 ${summary.scheduleCount} 项、学期 ${summary.termCount} 个、" +
                        "课程 ${summary.courseCount} 门、决策池项目 ${summary.poolItemCount} 个、时间记录 ${summary.timeRecordCount} 条。\n\n" +
                        "⚠️ 请注意：恢复数据将会覆盖并替换当前应用内的全部现有数据，此操作无法撤销。是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport, enabled = !backupState.isBusy) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = onDismissImportConfirmation, enabled = !backupState.isBusy) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    explanation: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$title：${if (granted) "已授权" else "未授权"}", style = MaterialTheme.typography.titleMedium)
            Text(explanation)
            if (!granted) {
                if (title == "通知权限") {
                    Button(onClick = onAction) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
