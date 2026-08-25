package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.reminder.ReminderPermissionState

/** 设置页读取 Android 当前权限事实；数据备份仍保留为 M7 的明确占位。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    reminderPermissions: ReminderPermissionState = ReminderPermissionState(),
    onRequestNotificationPermission: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("提醒权限", style = MaterialTheme.typography.headlineSmall)
            Text(
                "课程和普通事件需要通知权限与“闹钟和提醒”权限；权限不足不会阻止保存，但不会调度系统提醒。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionCard(
                title = "通知权限",
                granted = reminderPermissions.notificationGranted,
                explanation = "允许应用在日程提醒时显示通知。",
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
            Text("JSON 导入与导出将在 M7 接入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
