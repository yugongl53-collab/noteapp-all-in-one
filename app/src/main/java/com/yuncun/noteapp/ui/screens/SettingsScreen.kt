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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.R
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.backup.BackupUiState

/** 设置页读取 Android 当前权限事实，并把备份风险与整体替换放在明确确认之后。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    reminderPermissions: ReminderPermissionState = ReminderPermissionState(),
    backupState: BackupUiState = BackupUiState(),
    onBack: () -> Unit = {},
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            Text("数据备份", style = MaterialTheme.typography.headlineSmall)
            Text(
                "手动导入、导出 UTF-8 明文 JSON。备份包含全部业务数据、回收站灵感和番茄钟时长设置，不包含活动计时或系统权限。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRequestExport, enabled = !backupState.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("导出 JSON 备份")
            }
            OutlinedButton(onClick = onChooseImportFile, enabled = !backupState.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("导入 JSON 备份")
            }
            if (backupState.isBusy) Text("正在处理，请稍候……")
        }
    }

    if (backupState.showExportWarning) {
        AlertDialog(
            onDismissRequest = onDismissExportWarning,
            title = { Text("导出明文个人数据") },
            text = { Text("JSON 文件可被直接读取，包含灵感、日程和时间记录等个人数据。请保存到可信位置并妥善保管。") },
            confirmButton = { TextButton(onClick = onConfirmExportWarning) { Text("选择保存位置") } },
            dismissButton = { TextButton(onClick = onDismissExportWarning) { Text("取消") } }
        )
    }
    backupState.pendingImport?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissImportConfirmation,
            title = { Text("替换全部现有数据？") },
            text = {
                Text(
                    "文件：${summary.fileName}\n" +
                        "灵感 ${summary.ideaCount}，普通事件 ${summary.scheduleCount}，学期 ${summary.termCount}，" +
                        "课程 ${summary.courseCount}，事件池 ${summary.poolItemCount}，时间记录 ${summary.timeRecordCount}。\n" +
                        "确认后将整体替换当前业务数据和设置，此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport, enabled = !backupState.isBusy) { Text("确认替换") }
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
