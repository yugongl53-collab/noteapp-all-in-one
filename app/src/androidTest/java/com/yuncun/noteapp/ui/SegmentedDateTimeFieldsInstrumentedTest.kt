package com.yuncun.noteapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.ui.screens.CourseEditorDialog
import com.yuncun.noteapp.ui.screens.SegmentedDateField
import com.yuncun.noteapp.ui.screens.SegmentedTimeField
import com.yuncun.noteapp.ui.screens.TaskEditorDialog
import com.yuncun.noteapp.ui.screens.TermEditorDialog
import com.yuncun.noteapp.ui.screens.TimeRecordEditorDialog
import com.yuncun.noteapp.ui.statistics.TimeRecordDraftState
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * 验证分段输入器在 Compose 渲染、表单集成和多场景下的行为兼容性。
 */
class SegmentedDateTimeFieldsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun segmentedTimeField_rendersPlaceholderAndLabel() {
        var time by mutableStateOf("")
        composeRule.setContent {
            NoteAppTheme {
                SegmentedTimeField(
                    value = time,
                    onValueChange = { time = it },
                    label = "开始时间 HH:mm"
                )
            }
        }

        composeRule.onNodeWithText("开始时间 HH:mm").assertIsDisplayed()
        composeRule.onNodeWithText("HH").assertIsDisplayed()
        composeRule.onNodeWithText("mm").assertIsDisplayed()
    }

    @Test
    fun segmentedDateField_rendersYearMonthDayWithPlaceholders() {
        var date by mutableStateOf("")
        composeRule.setContent {
            NoteAppTheme {
                SegmentedDateField(
                    value = date,
                    onValueChange = { date = it },
                    label = "开始日期 YYYY-MM-DD",
                    includeYear = true
                )
            }
        }

        composeRule.onNodeWithText("开始日期 YYYY-MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("YYYY").assertIsDisplayed()
        composeRule.onNodeWithText("MM").assertIsDisplayed()
        composeRule.onNodeWithText("DD").assertIsDisplayed()
    }

    @Test
    fun segmentedDateField_withoutYear_rendersMonthDay() {
        var date by mutableStateOf("08-26")
        composeRule.setContent {
            NoteAppTheme {
                SegmentedDateField(
                    value = date,
                    onValueChange = { date = it },
                    label = "事件日期 MM-DD",
                    includeYear = false
                )
            }
        }

        composeRule.onNodeWithText("事件日期 MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("08").assertIsDisplayed()
        composeRule.onNodeWithText("26").assertIsDisplayed()
    }

    @Test
    fun taskEditorDialog_integratesSegmentedDateAndTimeFields() {
        composeRule.setContent {
            NoteAppTheme {
                TaskEditorDialog(
                    entity = null,
                    suggestions = listOf("读书"),
                    onSave = { _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("事件日期 MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("开始 HH:mm").assertIsDisplayed()
        composeRule.onNodeWithText("结束 HH:mm").assertIsDisplayed()
    }

    @Test
    fun courseEditorDialog_integratesSegmentedTimeFields() {
        val term = AcademicTermEntity(
            id = "term-1",
            academicYearStart = 2026,
            season = TermSeason.FALL,
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2027-01-15"),
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-01T00:00:00Z")
        )
        composeRule.setContent {
            NoteAppTheme {
                CourseEditorDialog(
                    entity = null,
                    terms = listOf(term),
                    suggestions = emptyList(),
                    onSave = { _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("开始 HH:mm").assertIsDisplayed()
        composeRule.onNodeWithText("结束 HH:mm").assertIsDisplayed()
    }

    @Test
    fun termEditorDialog_integratesSegmentedDateFields() {
        composeRule.setContent {
            NoteAppTheme {
                TermEditorDialog(
                    entity = null,
                    onSave = { _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("开始日期 YYYY-MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("结束日期 YYYY-MM-DD").assertIsDisplayed()
    }

    @Test
    fun timeRecordEditorDialog_integratesSegmentedDateTimeFields() {
        composeRule.setContent {
            NoteAppTheme {
                TimeRecordEditorDialog(
                    draft = TimeRecordDraftState(
                        startDate = "2026-08-26",
                        startTime = "09:00",
                        endDate = "2026-08-26",
                        endTime = "10:00",
                        isOpen = true
                    ),
                    suggestions = emptyList(),
                    onTitleChange = {},
                    onCategoryChange = {},
                    onStartDateChange = {},
                    onStartTimeChange = {},
                    onEndDateChange = {},
                    onEndTimeChange = {},
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("开始日期 YYYY-MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("开始时间 HH:mm").assertIsDisplayed()
        composeRule.onNodeWithText("结束日期 YYYY-MM-DD").assertIsDisplayed()
        composeRule.onNodeWithText("结束时间 HH:mm").assertIsDisplayed()
    }
}
