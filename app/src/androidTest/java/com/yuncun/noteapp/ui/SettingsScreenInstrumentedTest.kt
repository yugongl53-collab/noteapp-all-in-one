package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.screens.SettingsScreen
import com.yuncun.noteapp.ui.backup.BackupUiState
import com.yuncun.noteapp.data.backup.BackupSummary
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证提醒设置页展示系统事实，并只在用户点击后发起对应授权动作。 */
class SettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingPermissions_showSeparateActions() {
        var notificationRequested = false
        var alarmSettingsOpened = false
        composeRule.setContent {
            NoteAppTheme {
                SettingsScreen(
                    reminderPermissions = ReminderPermissionState(false, false),
                    onRequestNotificationPermission = { notificationRequested = true },
                    onOpenExactAlarmSettings = { alarmSettingsOpened = true }
                )
            }
        }

        composeRule.onNodeWithText("通知权限：未授权").assertIsDisplayed()
        composeRule.onNodeWithText("授予通知权限").performClick()
        composeRule.onNodeWithText("闹钟和提醒权限：未授权").assertIsDisplayed()
        composeRule.onNodeWithText("打开系统设置").performClick()
        assertTrue(notificationRequested)
        assertTrue(alarmSettingsOpened)
    }

    @Test
    fun backupActions_showPlaintextWarningAndReplacementSummary() {
        var exportRequested = false
        var importConfirmed = false
        composeRule.setContent {
            NoteAppTheme {
                SettingsScreen(
                    backupState = BackupUiState(
                        showExportWarning = true,
                        pendingImport = BackupSummary("backup.json", 2, 1, 1, 3, 4, 5)
                    ),
                    onConfirmExportWarning = { exportRequested = true },
                    onConfirmImport = { importConfirmed = true }
                )
            }
        }

        composeRule.onNodeWithText("导出明文个人数据").assertIsDisplayed()
        composeRule.onNodeWithText("选择保存位置").performClick()
        assertTrue(exportRequested)
        composeRule.onNodeWithText("替换全部现有数据？").assertIsDisplayed()
        composeRule.onNodeWithText("确认替换").performClick()
        assertTrue(importConfirmed)
    }
}
