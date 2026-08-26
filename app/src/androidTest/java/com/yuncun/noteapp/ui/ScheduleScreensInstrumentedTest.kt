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
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.rules.EventStreamItem
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.ui.schedule.ScheduleUiState
import com.yuncun.noteapp.ui.schedule.ScheduleViewMode
import com.yuncun.noteapp.ui.screens.CourseEditorDialog
import com.yuncun.noteapp.ui.screens.OverlapConfirmationDialog
import com.yuncun.noteapp.ui.screens.ScheduleDetailDialog
import com.yuncun.noteapp.ui.screens.ScheduleScreen
import com.yuncun.noteapp.ui.screens.TaskEditorDialog
import com.yuncun.noteapp.ui.screens.TaskSummary
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证日程三视图、顶部时期入口、无学期边界与重叠确认回调。 */
class ScheduleScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timetableEmptyState_keepsSevenDayGridAndScheduleManagementEntries() {
        composeRule.setContent { NoteAppTheme { scheduleScreen() } }

        composeRule.onNodeWithText("课表").assertIsDisplayed()
        composeRule.onAllNodesWithText("无事件").fetchSemanticsNodes().let { assertEquals(7, it.size) }
        composeRule.onNodeWithText("普通事件").assertIsDisplayed()
        composeRule.onNodeWithText("课程").assertIsDisplayed()
        composeRule.onNodeWithText("未设置学期").assertIsDisplayed()
    }

    @Test
    fun currentPeriodButton_opensTermSettingsInsteadOfUsingPeerManagementEntry() {
        composeRule.setContent { NoteAppTheme { scheduleScreen(currentPeriodLabel = "2026-2027秋季学期 · 第3周") } }

        composeRule.onNodeWithText("2026-2027秋季学期 · 第3周").performClick()

        composeRule.onNodeWithText("学期设置").assertIsDisplayed()
    }

    @Test
    fun eventStreamTab_forwardsViewSelection() {
        var selected: ScheduleViewMode? = null
        composeRule.setContent { NoteAppTheme { scheduleScreen(onSelectView = { selected = it }) } }

        composeRule.onNodeWithText("事件流").performClick()

        assertEquals(ScheduleViewMode.EVENT_STREAM, selected)
    }

    @Test
    fun eventStream_rendersChunkedInstancesAndOpensDetailDialog() {
        val morningInstance = ScheduleInstance(
            sourceId = "task-am",
            source = ScheduleSource.TASK,
            title = "上午例会",
            category = EventCategory.WORK,
            startAt = Instant.parse("2026-08-25T01:00:00Z"),
            endAt = Instant.parse("2026-08-25T02:00:00Z")
        )
        val afternoonInstance = ScheduleInstance(
            sourceId = "task-pm",
            source = ScheduleSource.TASK,
            title = "下午复盘",
            category = EventCategory.STUDY,
            startAt = Instant.parse("2026-08-25T06:00:00Z"),
            endAt = Instant.parse("2026-08-25T07:00:00Z")
        )
        val streamItems = listOf(
            EventStreamItem(morningInstance, isOngoing = false, isNext = true),
            EventStreamItem(afternoonInstance, isOngoing = false, isNext = false)
        )
        composeRule.setContent {
            NoteAppTheme {
                ScheduleScreen(
                    state = ScheduleUiState(
                        isLoading = false,
                        selectedWeek = LocalDate.parse("2026-08-24"),
                        viewMode = ScheduleViewMode.EVENT_STREAM,
                        eventStream = streamItems,
                        tasks = listOf(
                            ScheduleTaskEntity(
                                id = "task-am",
                                title = "上午例会",
                                category = EventCategory.WORK,
                                type = ScheduleType.ONE_OFF,
                                weekdays = emptySet(),
                                effectiveFrom = null,
                                date = LocalDate.parse("2026-08-25"),
                                startTime = LocalTime.of(9, 0),
                                endTime = LocalTime.of(10, 0),
                                isEnabled = true,
                                reminderEnabled = false,
                                reminderAdvanceMinutes = null,
                                createdAt = Instant.parse("2026-08-25T00:00:00Z"),
                                updatedAt = Instant.parse("2026-08-25T00:00:00Z")
                            )
                        )
                    ),
                    onSelectView = {},
                    onPreviousWeek = {}, onNextWeek = {}, onCurrentWeek = {},
                    onSaveTerm = { _, _ -> }, onDeleteTerm = {},
                    onSaveTask = { _, _ -> }, onDeleteTask = {},
                    onSaveCourse = { _, _ -> }, onDeleteCourse = {},
                    onConfirmOverlap = {}, onCancelOverlap = {}
                )
            }
        }

        composeRule.onNodeWithText("上午例会").assertIsDisplayed()
        composeRule.onNodeWithText("下午复盘").assertIsDisplayed()
        composeRule.onNodeWithText("下一个事件").assertIsDisplayed()

        // 验证没有显式的“上午”或“下午”文字标题
        assertEquals(0, composeRule.onAllNodesWithText("上午").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("下午").fetchSemanticsNodes().size)

        // 点击卡片打开详情弹窗
        composeRule.onNodeWithText("上午例会").performClick()
        composeRule.onNodeWithText("事件详情").assertIsDisplayed()
    }

    @Test
    fun statisticsTab_forwardsIntegratedViewSelection() {
        var selected: ScheduleViewMode? = null
        composeRule.setContent { NoteAppTheme { scheduleScreen(onSelectView = { selected = it }) } }

        composeRule.onNodeWithText("时间统计").performClick()

        assertEquals(ScheduleViewMode.STATISTICS, selected)
    }

    @Test
    fun courseForm_withoutTermExplainsBlockAndDisablesSave() {
        composeRule.setContent {
            NoteAppTheme {
                CourseEditorDialog(null, emptyList(), emptyList(), { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("尚未设置学期，请先关闭表单并点击顶部时期状态按钮。").assertIsDisplayed()
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

    @Test
    fun scheduleDetailDialog_displaysActionsAndConfirmationTriggersDelete() {
        var deleted = false
        val instance = ScheduleInstance(
            sourceId = "task-1",
            source = ScheduleSource.TASK,
            title = "周会",
            category = EventCategory.WORK,
            startAt = Instant.parse("2026-08-25T01:00:00Z"),
            endAt = Instant.parse("2026-08-25T02:00:00Z")
        )
        composeRule.setContent {
            NoteAppTheme {
                ScheduleDetailDialog(
                    instance = instance,
                    onEdit = {},
                    onDelete = { deleted = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("前往编辑").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()

        // 点击删除弹出二次确认
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确认删除普通事件？").assertIsDisplayed()
        composeRule.onNodeWithText("确定要删除「周会」吗？删除后无法恢复，并会一并清除相关提醒。").assertIsDisplayed()

        // 点击确认删除
        composeRule.onNodeWithText("确认删除").performClick()
        assertTrue(deleted)
    }

    @Test
    fun scheduleDetailDialog_courseConfirmationShowsCourseTitleAndCancelKeepsData() {
        var deleted = false
        val instance = ScheduleInstance(
            sourceId = "course-1",
            source = ScheduleSource.COURSE,
            title = "高等数学",
            category = EventCategory.STUDY,
            startAt = Instant.parse("2026-08-25T01:00:00Z"),
            endAt = Instant.parse("2026-08-25T02:00:00Z"),
            location = "理学楼 101"
        )
        composeRule.setContent {
            NoteAppTheme {
                ScheduleDetailDialog(
                    instance = instance,
                    onEdit = {},
                    onDelete = { deleted = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确认删除课程？").assertIsDisplayed()
        composeRule.onNodeWithText("确定要删除「高等数学」吗？删除后无法恢复，并会一并清除相关提醒。").assertIsDisplayed()

        // 取消不触发删除
        composeRule.onNodeWithText("取消").performClick()
        assertFalse(deleted)
    }

    @androidx.compose.runtime.Composable
    private fun scheduleScreen(
        currentPeriodLabel: String = "未设置学期",
        onSelectView: (ScheduleViewMode) -> Unit = {}
    ) {
        ScheduleScreen(
            state = ScheduleUiState(
                isLoading = false,
                selectedWeek = LocalDate.parse("2026-08-24"),
                currentPeriodLabel = currentPeriodLabel
            ),
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
