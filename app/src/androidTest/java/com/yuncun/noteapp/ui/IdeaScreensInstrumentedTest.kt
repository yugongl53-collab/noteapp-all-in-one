package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.ui.idea.IdeaDraftState
import com.yuncun.noteapp.ui.idea.IdeaUiState
import com.yuncun.noteapp.ui.screens.IdeaEditScreen
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.IdeaTrashScreen
import com.yuncun.noteapp.ui.screens.TodayScreen
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 验证 M2 页面关键状态、输入回调和两级删除确认，不依赖真实数据库。 */
class IdeaScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ideaList_rendersEmptyState() {
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = false),
                    onBack = {},
                    onAdd = {},
                    onEdit = {},
                    onOpenTrash = {}
                )
            }
        }

        composeRule.onNodeWithText("还没有灵感，记录第一条想法吧").assertIsDisplayed()
    }

    @Test
    fun ideaList_rendersLoadingState() {
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = true),
                    onBack = {},
                    onAdd = {},
                    onEdit = {},
                    onOpenTrash = {}
                )
            }
        }

        composeRule.onNodeWithText("正在加载灵感…").assertIsDisplayed()
    }

    @Test
    fun ideaList_rendersPersistedContentAndTags() {
        val now = Instant.parse("2026-08-25T08:00:00Z")
        val idea = IdeaEntity("idea", "已经保存的灵感", listOf("学习"), now, now, null)
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = false, activeIdeas = listOf(idea)),
                    onBack = {},
                    onAdd = {},
                    onEdit = {},
                    onOpenTrash = {}
                )
            }
        }

        composeRule.onNodeWithText("已经保存的灵感").assertIsDisplayed()
        composeRule.onNodeWithText("标签：学习").assertIsDisplayed()
    }

    @Test
    fun todayQuickInput_forwardsTypedContentAndSaveAction() {
        var typed = ""
        var saveCount = 0
        composeRule.setContent {
            NoteAppTheme {
                TodayScreen(
                    draft = IdeaDraftState(),
                    onContentChange = { typed = it },
                    onTagsChange = {},
                    onSave = { saveCount += 1 },
                    onOpenIdeas = {}
                )
            }
        }

        composeRule.onNodeWithText("现在想到了什么？").performTextInput("需要保留的输入")
        composeRule.onNodeWithText("保存灵感").performClick()

        assertEquals("需要保留的输入", typed)
        assertEquals(1, saveCount)
    }

    @Test
    fun editor_requiresConfirmationBeforeMovingIdeaToTrash() {
        var deletedId: String? = null
        composeRule.setContent {
            NoteAppTheme {
                IdeaEditScreen(
                    draft = IdeaDraftState(id = "idea", content = "内容"),
                    state = IdeaUiState(isLoading = false),
                    onPrepare = {},
                    onContentChange = {},
                    onTagsChange = {},
                    onSave = {},
                    onMoveToTrash = { deletedId = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("移入回收站").performClick()
        composeRule.onNodeWithText("移入回收站？").assertIsDisplayed()
        composeRule.onNodeWithText("确认删除").performClick()

        assertEquals("idea", deletedId)
    }

    @Test
    fun trash_requiresConfirmationBeforePermanentDeletion() {
        var deletedId: String? = null
        val deletedAt = Instant.now()
        val idea = IdeaEntity("idea", "待清理", emptyList(), deletedAt, deletedAt, deletedAt)
        composeRule.setContent {
            NoteAppTheme {
                IdeaTrashScreen(
                    state = IdeaUiState(isLoading = false, recycledIdeas = listOf(idea)),
                    onBack = {},
                    onRestore = {},
                    onPermanentlyDelete = { deletedId = it },
                    now = deletedAt
                )
            }
        }

        composeRule.onAllNodesWithText("永久删除")[0].performClick()
        composeRule.onNodeWithText("永久删除灵感？").assertIsDisplayed()
        composeRule.onAllNodesWithText("永久删除")[1].performClick()

        assertEquals("idea", deletedId)
    }
}
