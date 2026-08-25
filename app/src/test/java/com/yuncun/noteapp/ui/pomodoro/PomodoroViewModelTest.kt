package com.yuncun.noteapp.ui.pomodoro

import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.repository.EventPoolRepository
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.pomodoro.PomodoroCoordinator
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 验证 M5 状态层刷新、只抽启用项、CRUD 回读与失败反馈。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-25T00:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_derivesDistinctNameSuggestions() = runTest(dispatcher) {
        val repository = FakePoolRepository(mutableListOf(item("one", "阅读", true), item("two", "阅读", false)))
        val viewModel = PomodoroViewModel(repository, FakeCoordinator(), clock = { now }, nextIndex = { 0 })

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("阅读"), viewModel.uiState.value.poolNameSuggestions)
    }

    @Test
    fun draw_usesOnlyEnabledCandidatesAndEmptyPoolShowsGuidance() = runTest(dispatcher) {
        val repository = FakePoolRepository(mutableListOf(item("off", "停用", false), item("on", "启用", true)))
        val viewModel = PomodoroViewModel(repository, FakeCoordinator(), clock = { now }, nextIndex = { 0 })
        advanceUntilIdle()

        viewModel.draw()
        assertEquals("on", viewModel.uiState.value.selectedCandidate?.id)

        repository.items.replaceAll { it.copy(isEnabled = false) }
        viewModel.refreshPool()
        advanceUntilIdle()
        viewModel.draw()
        assertNull(viewModel.uiState.value.selectedCandidate)
        assertEquals("没有启用项目，请先添加或启用一项", viewModel.uiState.value.feedback)
    }

    @Test
    fun savePoolItem_reloadsPersistedSnapshot() = runTest(dispatcher) {
        val repository = FakePoolRepository(mutableListOf())
        val viewModel = PomodoroViewModel(repository, FakeCoordinator(), clock = { now })
        advanceUntilIdle()

        viewModel.savePoolItem(null, "写作", EventCategory.WORK, true)
        advanceUntilIdle()

        assertEquals(listOf("写作"), viewModel.uiState.value.poolItems.map { it.title })
        assertEquals("事件池项目已保存", viewModel.uiState.value.feedback)
        assertEquals(1, viewModel.uiState.value.actionCompletedVersion)
    }

    @Test
    fun repositoryFailure_doesNotClaimSuccessfulWrite() = runTest(dispatcher) {
        val repository = FakePoolRepository(mutableListOf(), failSave = true)
        val viewModel = PomodoroViewModel(repository, FakeCoordinator(), clock = { now })
        advanceUntilIdle()

        viewModel.savePoolItem(null, "写作", EventCategory.WORK, true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.feedback.orEmpty().startsWith("事件池操作失败"))
        assertEquals(0, viewModel.uiState.value.actionCompletedVersion)
    }

    private fun item(id: String, title: String, enabled: Boolean) = EventPoolItemEntity(
        id, title, EventCategory.STUDY, enabled, now, now
    )

    private class FakePoolRepository(
        val items: MutableList<EventPoolItemEntity>,
        private val failSave: Boolean = false
    ) : EventPoolRepository {
        override suspend fun load() = items.toList()

        override suspend fun save(
            id: String?,
            title: String,
            category: EventCategory,
            isEnabled: Boolean,
            now: Instant
        ): String {
            if (failSave) error("磁盘不可用")
            val targetId = id ?: "item-${items.size}"
            items.removeAll { it.id == targetId }
            items += EventPoolItemEntity(targetId, title, category, isEnabled, now, now)
            return targetId
        }

        override suspend fun setEnabled(id: String, enabled: Boolean, now: Instant) {
            items.replaceAll { if (it.id == id) it.copy(isEnabled = enabled, updatedAt = now) else it }
        }

        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }
    }

    private class FakeCoordinator : PomodoroCoordinator {
        private val settingsState = MutableStateFlow(AppSettings())
        private val sessionState = MutableStateFlow<PomodoroSession?>(null)
        override val settings: Flow<AppSettings> = settingsState
        override val session: Flow<PomodoroSession?> = sessionState
        override suspend fun synchronize() = Unit
        override suspend fun start(title: String?, focusMinutes: Int, restMinutes: Int) = Unit
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun reset() = Unit
        override suspend fun finishEarly() = Unit
        override suspend fun startRest() = Unit
        override suspend fun clear() = Unit
        override suspend fun handleAlarm(sessionId: String, targetEndAt: Instant) = Unit
    }
}
