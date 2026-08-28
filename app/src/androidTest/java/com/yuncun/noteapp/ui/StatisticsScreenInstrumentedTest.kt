package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import com.yuncun.noteapp.domain.rules.TimeRecordSnapshot
import com.yuncun.noteapp.ui.screens.StatisticsScreen
import com.yuncun.noteapp.ui.screens.TimeRecordEditorDialog
import com.yuncun.noteapp.ui.statistics.StatisticsPeriod
import com.yuncun.noteapp.ui.statistics.StatisticsUiState
import com.yuncun.noteapp.ui.statistics.TimeRecordDraftState
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** 验证 M6 空状态、榜单呈现、快捷名称边界和永久删除确认。 */
class StatisticsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyDay_showsEmptyRankingAndManualEntry() {
        composeRule.setContent { NoteAppTheme { screen(emptyState()) } }

        composeRule.onNodeWithText("该时段还没有时间记录。").assertIsDisplayed()
        composeRule.onNodeWithText("手动录入").assertIsDisplayed()
    }

    @Test
    fun categoryRanking_showsRankNameAndExactDuration() {
        val record = record()
        val statistics = TimeRecordRules.calculateStatistics(
            listOf(TimeRecordSnapshot(record.id, record.title, record.category, record.startAt, record.endAt)),
            LocalDate.parse("2026-08-25"),
            LocalDate.parse("2026-08-25"),
            zone
        )
        composeRule.setContent {
            NoteAppTheme {
                screen(emptyState().copy(statistics = statistics, visibleRecords = listOf(record), records = listOf(record)))
            }
        }

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onAllNodesWithText("工作")[0].assertIsDisplayed()
        composeRule.onNodeWithText("1小时").assertIsDisplayed()
    }

    @Test
    fun suggestion_onlyFillsTitleAndDoesNotChangeCategory() {
        var selectedTitle: String? = null
        var selectedCategory: EventCategory? = null
        composeRule.setContent {
            NoteAppTheme {
                TimeRecordEditorDialog(
                    draft = TimeRecordDraftState(
                        startDate = "2026-08-25",
                        startTime = "09:00",
                        endDate = "2026-08-25",
                        endTime = "10:00",
                        isOpen = true
                    ),
                    suggestions = listOf("阅读"),
                    onTitleChange = { selectedTitle = it },
                    onCategoryChange = { selectedCategory = it },
                    onStartDateChange = {}, onStartTimeChange = {}, onEndDateChange = {}, onEndTimeChange = {},
                    onSave = {}, onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("阅读").performClick()
        assertEquals("阅读", selectedTitle)
        assertNull(selectedCategory)
    }

    @Test
    fun delete_requiresExplicitIrrecoverableConfirmation() {
        var deletedId: String? = null
        val record = record()
        val state = emptyState().copy(visibleRecords = listOf(record), records = listOf(record))
        composeRule.setContent { NoteAppTheme { screen(state, onDelete = { deletedId = it }) } }

        composeRule.onNodeWithContentDescription("删除写作").performScrollTo().performClick()
        composeRule.onNodeWithText("永久删除后无法恢复，相关日周统计和快捷名称会立即重算。").assertIsDisplayed()
        composeRule.onNodeWithText("永久删除").performClick()

        assertEquals("record", deletedId)
    }

    @androidx.compose.runtime.Composable
    private fun screen(state: StatisticsUiState, onDelete: (String) -> Unit = {}) {
        StatisticsScreen(
            state = state,
            draft = TimeRecordDraftState(),
            onSelectPeriod = {}, onSelectRanking = {}, onPreviousPeriod = {}, onNextPeriod = {},
            onCurrentPeriod = {}, onRetry = {}, onAddRecord = {}, onEditRecord = {}, onDeleteRecord = onDelete,
            onUpdateTitle = {}, onUpdateCategory = {}, onUpdateStartDate = {}, onUpdateStartTime = {},
            onUpdateEndDate = {}, onUpdateEndTime = {}, onSaveDraft = {}, onDismissEditor = {}
        )
    }

    private fun emptyState(): StatisticsUiState {
        val date = LocalDate.parse("2026-08-25")
        return StatisticsUiState(
            isLoading = false,
            selectedDate = date,
            period = StatisticsPeriod.DAY,
            statistics = TimeRecordRules.calculateStatistics(emptyList(), date, date, zone)
        )
    }

    private fun record() = TimeRecordEntity(
        id = "record",
        title = "写作",
        category = EventCategory.WORK,
        startAt = Instant.parse("2026-08-25T01:00:00Z"),
        endAt = Instant.parse("2026-08-25T02:00:00Z"),
        source = "manual",
        relatedTaskId = null,
        relatedPoolItemId = null,
        createdAt = Instant.parse("2026-08-25T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-25T00:00:00Z")
    )

    companion object {
        private val zone = ZoneId.of("Asia/Shanghai")
    }
}
