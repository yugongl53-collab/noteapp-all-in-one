package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.ui.pomodoro.PomodoroUiState
import com.yuncun.noteapp.ui.screens.PomodoroScreen
import com.yuncun.noteapp.ui.screens.ToolboxScreen
import com.yuncun.noteapp.ui.screens.PoolItemEditorDialog
import com.yuncun.noteapp.ui.screens.SECTION_POOL
import com.yuncun.noteapp.ui.screens.SECTION_TIMER
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证事件池空状态、快捷名称边界、计时输入和阶段确认主交互。 */
class PomodoroScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyPool_disablesDrawAndShowsAddGuidance() {
        composeRule.setContent { NoteAppTheme { screen(PomodoroUiState(isLoading = false), SECTION_POOL) } }

        composeRule.onNodeWithText("没有启用项目，请先添加或启用一项。").assertIsDisplayed()
        composeRule.onNodeWithText("帮我选一个").assertIsNotEnabled()
    }

    @Test
    fun nameSuggestion_onlyFillsTitleAndKeepsDefaultCategory() {
        var category: EventCategory? = null
        composeRule.setContent {
            NoteAppTheme {
                PoolItemEditorDialog(
                    item = null,
                    suggestions = listOf("阅读"),
                    onSave = { _, _, selected, _ -> category = selected },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("阅读").performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(EventCategory.WORK, category)
    }

    @Test
    fun setup_usesRecentDurationsAndRequestsNotificationWhenStarting() {
        var requested = false
        var started = false
        composeRule.setContent {
            NoteAppTheme {
                screen(
                    PomodoroUiState(isLoading = false, settings = AppSettings(30, 10)),
                    SECTION_TIMER,
                    notificationGranted = false,
                    onStart = { _, focus, rest -> started = focus == 30 && rest == 10 },
                    onRequestNotificationPermission = { requested = true }
                )
            }
        }

        composeRule.onNodeWithText("开始专注").performClick()

        assertTrue(requested)
        assertTrue(started)
    }

    @Test
    fun completedFocus_waitsForExplicitRestConfirmation() {
        var restStarted = false
        val completed = session().copy(state = PomodoroState.COMPLETED, targetEndAt = null)
        composeRule.setContent {
            NoteAppTheme {
                screen(
                    PomodoroUiState(isLoading = false, session = completed),
                    SECTION_TIMER,
                    onStartRest = { restStarted = true }
                )
            }
        }

        composeRule.onNodeWithText("阶段已完成，等待你的确认").assertIsDisplayed()
        composeRule.onNodeWithText("开始休息").performClick()
        assertTrue(restStarted)
    }

    @Test
    fun toolbox_displaysBothPomodoroAndEventPoolCards() {
        composeRule.setContent {
            NoteAppTheme {
                screen(
                    PomodoroUiState(
                        isLoading = false,
                        poolItems = listOf(
                            com.yuncun.noteapp.data.local.entity.EventPoolItemEntity(
                                "1", "背单词", EventCategory.STUDY, true, Instant.now(), Instant.now()
                            )
                        )
                    ),
                    SECTION_TIMER
                )
            }
        }

        composeRule.onNodeWithText("番茄钟").assertIsDisplayed()
        composeRule.onNodeWithText("事件池与抽奖").assertIsDisplayed()
        composeRule.onNodeWithText("启用 1 / 1 项").assertIsDisplayed()
        composeRule.onNodeWithText("管理事件池").assertIsDisplayed()
    }

    @Test
    fun toolbox_drawCandidateAndCarryToPomodoro() {
        val candidate = com.yuncun.noteapp.domain.model.EventPoolCandidate("1", "算法刷题", EventCategory.STUDY, true)
        composeRule.setContent {
            NoteAppTheme {
                screen(
                    PomodoroUiState(
                        isLoading = false,
                        poolItems = listOf(
                            com.yuncun.noteapp.data.local.entity.EventPoolItemEntity(
                                "1", "算法刷题", EventCategory.STUDY, true, Instant.now(), Instant.now()
                            )
                        ),
                        selectedCandidate = candidate
                    ),
                    SECTION_TIMER
                )
            }
        }

        composeRule.onNodeWithText("算法刷题").assertIsDisplayed()
        composeRule.onNodeWithText("带入番茄钟").performClick()
    }

    @androidx.compose.runtime.Composable
    private fun screen(
        state: PomodoroUiState,
        section: String,
        notificationGranted: Boolean = true,
        onStart: (String?, Int, Int) -> Unit = { _, _, _ -> },
        onRequestNotificationPermission: () -> Unit = {},
        onStartRest: () -> Unit = {}
    ) {
        ToolboxScreen(
            state = state,
            initialSection = section,
            notificationGranted = notificationGranted,
            onBack = {}, onSavePoolItem = { _, _, _, _ -> }, onSetPoolItemEnabled = { _, _ -> },
            onDeletePoolItem = {}, onDraw = {}, onStart = onStart, onPause = {}, onResume = {},
            onReset = {}, onFinishEarly = {}, onStartRest = onStartRest, onClearSession = {},
            onRequestNotificationPermission = onRequestNotificationPermission
        )
    }

    private fun session() = PomodoroSession(
        id = "session",
        title = "阅读",
        phase = PomodoroPhase.FOCUS,
        plannedFocusMinutes = 25,
        plannedRestMinutes = 5,
        startedAt = Instant.parse("2026-08-25T00:00:00Z"),
        targetEndAt = Instant.parse("2026-08-25T00:25:00Z"),
        remainingSeconds = null,
        state = PomodoroState.RUNNING,
        updatedAt = Instant.parse("2026-08-25T00:00:00Z")
    )
}
