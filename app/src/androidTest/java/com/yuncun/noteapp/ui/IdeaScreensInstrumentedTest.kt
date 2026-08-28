package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.ui.idea.IdeaDraftState
import com.yuncun.noteapp.ui.idea.IdeaUiState
import com.yuncun.noteapp.ui.screens.IdeaEditScreen
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.IdeaTrashScreen
import com.yuncun.noteapp.ui.theme.NoteAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    onAdd = {},
                    onEdit = {},
                    onOpenTrash = {}
                )
            }
        }

        composeRule.onNodeWithText("暂无灵感记录，点击右下角「+」开始记录吧").assertIsDisplayed()
    }

    @Test
    fun ideaList_rendersLoadingState() {
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = true),
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
    fun ideaList_clickingFabTriggersAdd() {
        var addClicked = false
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = false),
                    onAdd = { addClicked = true },
                    onEdit = {},
                    onOpenTrash = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("新增灵感").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun ideaList_doesNotDisplayIdeaHeaderTitle_andTrashButtonWorks() {
        var trashClicked = false
        composeRule.setContent {
            NoteAppTheme {
                IdeaScreen(
                    state = IdeaUiState(isLoading = false),
                    onAdd = {},
                    onEdit = {},
                    onOpenTrash = { trashClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("灵感").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("回收站").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("回收站").performClick()
        assertTrue(trashClicked)
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
        composeRule.onNodeWithText("确认永久删除").performClick()

        assertEquals("idea", deletedId)
    }
}
