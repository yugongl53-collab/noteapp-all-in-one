package com.yuncun.noteapp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.screens.SettingsScreen
import com.yuncun.noteapp.ui.backup.BackupUiState
import com.yuncun.noteapp.data.backup.BackupSummary
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import com.yuncun.noteapp.domain.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证设置页展示系统事实、外观主题切换与通俗化数据备份恢复。 */
class SettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeModeSelection_triggersCallback() {
        var selectedMode: AppThemeMode? = null
        composeRule.setContent {
            NoteAppTheme {
                SettingsScreen(
                    currentThemeMode = AppThemeMode.SYSTEM,
                    onThemeModeChange = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithText("外观主题").assertIsDisplayed()
        composeRule.onNodeWithText("📱 跟随系统").assertIsDisplayed()
        composeRule.onNodeWithText("☀️ 浅色模式").assertIsDisplayed()
        composeRule.onNodeWithText("🌙 深色模式").assertIsDisplayed()

        composeRule.onNodeWithText("🌙 深色模式").performClick()
        assertEquals(AppThemeMode.DARK, selectedMode)
    }

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
    fun exportAction_showsPlaintextWarningBeforeChoosingLocation() {
        var exportRequested = false
        composeRule.setContent {
            NoteAppTheme {
                SettingsScreen(
                    backupState = BackupUiState(showExportWarning = true),
                    onConfirmExportWarning = { exportRequested = true }
                )
            }
        }

        composeRule.onNodeWithText("导出数据备份").assertIsDisplayed()
        composeRule.onNodeWithText("选择保存位置").performClick()
        assertTrue(exportRequested)
    }

    @Test
    fun validatedImport_showsReplacementSummaryBeforeCommit() {
        var importConfirmed = false
        composeRule.setContent {
            NoteAppTheme {
                SettingsScreen(
                    backupState = BackupUiState(
                        pendingImport = BackupSummary("backup.json", 2, 1, 1, 3, 4, 5)
                    ),
                    onConfirmImport = { importConfirmed = true }
                )
            }
        }

        composeRule.onNodeWithText("确认恢复数据？").assertIsDisplayed()
        composeRule.onNodeWithText("确认恢复").performClick()
        assertTrue(importConfirmed)
    }

    @Test
    fun largeFont_canScrollToBackupActionsAndReturn() {
        var backRequested = false
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f)
            ) {
                NoteAppTheme(darkTheme = true) {
                    SettingsScreen(onBack = { backRequested = true })
                }
            }
        }

        // 大字体与深色主题组合下，核心备份操作仍可通过滚动访问。
        composeRule.onNodeWithText("从备份文件恢复数据").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertTrue(backRequested)
    }
}
