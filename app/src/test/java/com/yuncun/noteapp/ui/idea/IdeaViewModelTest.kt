package com.yuncun.noteapp.ui.idea

import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.repository.IdeaRepository
import com.yuncun.noteapp.data.repository.IdeaSnapshot
import com.yuncun.noteapp.domain.rules.IdeaRules
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 验证 M2 加载、空状态、成功、校验失败及持久化失败状态。 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdeaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-25T08:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialRefresh_transitionsFromLoadingToEmptyState() = runTest(dispatcher) {
        val viewModel = IdeaViewModel(FakeIdeaRepository(), clock = { now })

        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.activeIdeas.isEmpty())
        assertTrue(viewModel.uiState.value.recycledIdeas.isEmpty())
    }

    @Test
    fun saveEditor_createsNewIdeaAndRefreshesList() = runTest(dispatcher) {
        val repository = FakeIdeaRepository()
        val viewModel = IdeaViewModel(repository, clock = { now })
        advanceUntilIdle()
        viewModel.prepareEditor(null)
        viewModel.updateEditorContent("  新想法  ")
        viewModel.updateEditorTags("学习，学习")

        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(listOf("新想法"), viewModel.uiState.value.activeIdeas.map { it.content })
        assertEquals(listOf("学习"), viewModel.uiState.value.activeIdeas.single().tags)
        assertEquals("灵感已保存", viewModel.uiState.value.feedback)
        assertEquals(1L, viewModel.uiState.value.saveCompletedVersion)
    }

    @Test
    fun saveEditor_updatesExistingIdeaAndRefreshesList() = runTest(dispatcher) {
        val repository = FakeIdeaRepository()
        repository.active += IdeaEntity("idea-1", "旧内容", listOf("旧标签"), now, now, null)
        val viewModel = IdeaViewModel(repository, clock = { now })
        advanceUntilIdle()

        viewModel.prepareEditor("idea-1")
        assertEquals("旧内容", viewModel.editorDraft.value.content)
        viewModel.updateEditorContent("更新后的内容")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(listOf("更新后的内容"), viewModel.uiState.value.activeIdeas.map { it.content })
        assertEquals("灵感已保存", viewModel.uiState.value.feedback)
    }

    @Test
    fun saveEditor_validationFailureKeepsInputAndShowsFieldError() = runTest(dispatcher) {
        val repository = FakeIdeaRepository()
        val viewModel = IdeaViewModel(repository, clock = { now })
        advanceUntilIdle()
        viewModel.prepareEditor(null)
        viewModel.updateEditorContent("   ")
        viewModel.updateEditorTags("待办")

        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals("   ", viewModel.editorDraft.value.content)
        assertEquals("待办", viewModel.editorDraft.value.tagsInput)
        assertEquals("灵感正文不能为空", viewModel.editorDraft.value.contentError)
        assertTrue(repository.active.isEmpty())
    }

    @Test
    fun saveEditor_persistenceFailureKeepsInputAndExplainsRetry() = runTest(dispatcher) {
        val repository = FakeIdeaRepository(saveFailure = IllegalStateException("磁盘不可用"))
        val viewModel = IdeaViewModel(repository, clock = { now })
        advanceUntilIdle()
        viewModel.prepareEditor(null)
        viewModel.updateEditorContent("不能丢失")

        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals("不能丢失", viewModel.editorDraft.value.content)
        assertEquals("保存失败，请重试：磁盘不可用", viewModel.uiState.value.feedback)
    }

    @Test
    fun recycledIdea_isAutomaticallyRemovedAtExactRetentionBoundary() = runTest(dispatcher) {
        val repository = FakeIdeaRepository()
        repository.active += IdeaEntity("idea", "待到期", emptyList(), now, now, null)
        val viewModel = IdeaViewModel(
            repository = repository,
            clock = { now.plusMillis(testScheduler.currentTime) }
        )
        runCurrent()

        viewModel.moveToTrash("idea")
        runCurrent()
        assertEquals(1, viewModel.uiState.value.recycledIdeas.size)

        advanceTimeBy(IdeaRules.retention.toMillis())
        runCurrent()

        assertTrue(viewModel.uiState.value.recycledIdeas.isEmpty())
    }

    private class FakeIdeaRepository(
        private val saveFailure: Throwable? = null
    ) : IdeaRepository {
        val active = mutableListOf<IdeaEntity>()
        private val recycled = mutableListOf<IdeaEntity>()

        override suspend fun refresh(now: Instant): IdeaSnapshot {
            recycled.removeAll { IdeaRules.isExpired(requireNotNull(it.deletedAt), now) }
            return IdeaSnapshot(
                activeIdeas = active.sortedByDescending { it.updatedAt },
                recycledIdeas = recycled.sortedByDescending { it.deletedAt }
            )
        }

        override suspend fun create(content: String, tags: List<String>, now: Instant): IdeaEntity {
            saveFailure?.let { throw it }
            val entity = IdeaEntity("idea-${active.size}", content, tags, now, now, null)
            active += entity
            return entity
        }

        override suspend fun update(id: String, content: String, tags: List<String>, now: Instant) {
            val index = active.indexOfFirst { it.id == id }
            check(index >= 0) { "灵感不存在" }
            active[index] = active[index].copy(content = content, tags = tags, updatedAt = now)
        }

        override suspend fun moveToTrash(id: String, now: Instant) {
            val entity = active.first { it.id == id }
            active.remove(entity)
            recycled += entity.copy(deletedAt = now)
        }

        override suspend fun restore(id: String) {
            val entity = recycled.first { it.id == id }
            recycled.remove(entity)
            active += entity.copy(deletedAt = null)
        }

        override suspend fun permanentlyDelete(id: String) {
            recycled.removeAll { it.id == id }
        }
    }
}
