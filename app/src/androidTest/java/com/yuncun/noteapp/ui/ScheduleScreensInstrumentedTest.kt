package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.schedule.ScheduleUiState
import com.yuncun.noteapp.ui.schedule.ScheduleViewMode
import com.yuncun.noteapp.ui.screens.CourseEditorDialog
import com.yuncun.noteapp.ui.screens.OverlapConfirmationDialog
import com.yuncun.noteapp.ui.screens.ScheduleScreen
import com.yuncun.noteapp.ui.screens.TaskEditorDialog
import com.yuncun.noteapp.ui.screens.TaskSummary
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证 M3 空状态、双视图入口、无学期边界与重叠确认回调。 */
class ScheduleScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timetableEmptyState_keepsSevenDayGridAndManagementEntries() {
        composeRule.setContent { NoteAppTheme { scheduleScreen() } }

        composeRule.onNodeWithText("课表").assertIsDisplayed()
        composeRule.onAllNodesWithText("无事件").fetchSemanticsNodes().let { assertEquals(7, it.size) }
        composeRule.onNodeWithText("普通事件").assertIsDisplayed()
        composeRule.onNodeWithText("课程").assertIsDisplayed()
        composeRule.onNodeWithText("学期").assertIsDisplayed()
    }

    @Test
    fun eventStreamTab_forwardsViewSelection() {
        var selected: ScheduleViewMode? = null
        composeRule.setContent { NoteAppTheme { scheduleScreen(onSelectView = { selected = it }) } }

        composeRule.onNodeWithText("事件流").performClick()

        assertEquals(ScheduleViewMode.EVENT_STREAM, selected)
    }

    @Test
    fun courseForm_withoutTermExplainsBlockAndDisablesSave() {
        composeRule.setContent {
            NoteAppTheme {
                CourseEditorDialog(null, emptyList(), emptyList(), { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("尚未设置学期，请先关闭表单并前往“学期”设置。").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun taskSuggestion_onlyFillsNameBeforeSaving() {
        var saved: ScheduleTaskInput? = null
        composeRule.setContent {
            NoteAppTheme {
                TaskEditorDialog(null, listOf("周会"), { _, input -> saved = input }, {})
            }
        }

        composeRule.onNodeWithText("周会").performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("周会", saved?.title)
        assertEquals(ScheduleType.ONE_OFF, saved?.type)
        assertEquals("09:00", saved?.startTime.toString())
    }

    @Test
    fun taskEditorDialog_defaultsToOneOffAndDisplaysSimplifiedLabels() {
        composeRule.setContent {
            NoteAppTheme {
                TaskEditorDialog(null, emptyList(), { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("单次").assertIsDisplayed()
        composeRule.onNodeWithText("每周").assertIsDisplayed()
        composeRule.onNodeWithText("事件日期 YYYY-MM-DD").assertIsDisplayed()
    }

    @Test
    fun overlapDialog_requiresExplicitConfirmation() {
        var confirmed = false
        composeRule.setContent {
            NoteAppTheme { OverlapConfirmationDialog({ confirmed = true }, {}) }
        }

        composeRule.onNodeWithText("仍然保存").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun enabledReminder_withoutNotificationPermission_isMarkedInactive() {
        composeRule.setContent {
            NoteAppTheme {
                TaskSummary(
                    task = task(),
                    reminderPermissions = ReminderPermissionState(
                        notificationGranted = false,
                        exactAlarmGranted = true
                    )
                )
            }
        }

        composeRule.onNodeWithText("提醒已配置但未生效：缺少通知权限").assertIsDisplayed()
    }

    @Test
    fun reminderFormWarning_opensReminderSettings() {
        var opened = false
        composeRule.setContent {
            NoteAppTheme {
                TaskEditorDialog(
                    entity = null,
                    suggestions = emptyList(),
                    onSave = { _, _ -> },
                    onDismiss = {},
                    reminderPermissions = ReminderPermissionState(false, false),
                    onOpenReminderSettings = { opened = true }
                )
            }
        }

        composeRule.onNodeWithText("提醒已配置但未生效：缺少通知权限、“闹钟和提醒”权限").assertIsDisplayed()
        composeRule.onNodeWithText("前往提醒设置").performClick()
        assertTrue(opened)
    }

    @androidx.compose.runtime.Composable
    private fun scheduleScreen(onSelectView: (ScheduleViewMode) -> Unit = {}) {
        ScheduleScreen(
            state = ScheduleUiState(isLoading = false, selectedWeek = LocalDate.parse("2026-08-24")),
            onSelectView = onSelectView,
            onPreviousWeek = {}, onNextWeek = {}, onCurrentWeek = {},
            onSaveTerm = { _, _ -> }, onDeleteTerm = {},
            onSaveTask = { _, _ -> }, onDeleteTask = {},
            onSaveCourse = { _, _ -> }, onDeleteCourse = {},
            onConfirmOverlap = {}, onCancelOverlap = {}
        )
    }

    private fun task() = ScheduleTaskEntity(
        id = "task",
        title = "周会",
        category = EventCategory.WORK,
        type = ScheduleType.WEEKLY,
        weekdays = setOf(DayOfWeek.TUESDAY),
        effectiveFrom = LocalDate.parse("2026-08-24"),
        date = null,
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = Instant.parse("2026-08-25T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-25T00:00:00Z")
    )
}
