package com.yuncun.noteapp.ui.statistics

import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.data.repository.TimeRecordRepository
import com.yuncun.noteapp.domain.model.EventCategory
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 验证 M6 状态层的范围切换、CRUD 回读、快捷名称和错误保留。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-25T02:15:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_calculatesDayAndRecentDistinctSuggestions() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf(
            record("old", "阅读", EventCategory.STUDY, "2026-08-25T00:00:00Z", "2026-08-25T01:00:00Z", now),
            record("new", "阅读", EventCategory.STUDY, "2026-08-25T01:00:00Z", "2026-08-25T02:00:00Z", now.plusSeconds(1))
        ))

        val viewModel = StatisticsViewModel(repository, { now }, { zone })
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(120L, viewModel.uiState.value.statistics?.recordedMinutes)
        assertEquals(listOf("阅读"), viewModel.uiState.value.nameSuggestions)
    }

    @Test
    fun weekNavigation_usesMondayToSundayAndKeepsRankingSelection() = runTest(dispatcher) {
        val viewModel = StatisticsViewModel(FakeRepository(mutableListOf()), { now }, { zone })
        advanceUntilIdle()

        viewModel.selectPeriod(StatisticsPeriod.WEEK)
        viewModel.selectRanking(StatisticsRanking.TITLE)
        viewModel.previousPeriod()

        assertEquals("2026-08-17", viewModel.uiState.value.statistics?.startDate.toString())
        assertEquals("2026-08-23", viewModel.uiState.value.statistics?.endDate.toString())
        assertEquals(StatisticsRanking.TITLE, viewModel.uiState.value.ranking)
    }

    @Test
    fun saveDraft_reloadsStatisticsAndNormalizesMinutePrecision() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf())
        val viewModel = StatisticsViewModel(repository, { now }, { zone })
        advanceUntilIdle()
        viewModel.prepareNewRecord()
        viewModel.updateTitle("写作")
        viewModel.updateStartTime("09:00")
        viewModel.updateEndTime("10:30")

        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals(90L, viewModel.uiState.value.statistics?.recordedMinutes)
        assertEquals("写作", viewModel.uiState.value.visibleRecords.single().title)
        assertFalse(viewModel.draft.value.isOpen)
    }

    @Test
    fun invalidInputAndRepositoryFailure_keepDraftForRetry() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf(), failSave = true)
        val viewModel = StatisticsViewModel(repository, { now }, { zone })
        advanceUntilIdle()
        viewModel.prepareNewRecord()
        viewModel.updateTitle("写作")
        viewModel.updateStartDate("错误日期")

        viewModel.saveDraft()
        assertTrue(viewModel.draft.value.error.orEmpty().contains("格式无效"))

        viewModel.updateStartDate("2026-08-25")
        viewModel.saveDraft()
        advanceUntilIdle()
        assertTrue(viewModel.draft.value.isOpen)
        assertEquals("写作", viewModel.draft.value.title)
        assertTrue(viewModel.draft.value.error.orEmpty().contains("磁盘不可用"))
    }

    @Test
    fun permanentDelete_immediatelyUpdatesStatisticsAndSuggestions() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf(
            record("only", "写作", EventCategory.WORK, "2026-08-25T01:00:00Z", "2026-08-25T02:00:00Z", now)
        ))
        val viewModel = StatisticsViewModel(repository, { now }, { zone })
        advanceUntilIdle()

        viewModel.deleteRecord("only")
        advanceUntilIdle()

        assertEquals(0L, viewModel.uiState.value.statistics?.recordedMinutes)
        assertTrue(viewModel.uiState.value.nameSuggestions.isEmpty())
        assertEquals("时间记录已永久删除", viewModel.uiState.value.feedback)
    }

    private fun record(
        id: String,
        title: String,
        category: EventCategory,
        start: String,
        end: String,
        updatedAt: Instant
    ) = TimeRecordEntity(
        id, title, category, Instant.parse(start), Instant.parse(end), "manual", null, null, now, updatedAt
    )

    private class FakeRepository(
        val records: MutableList<TimeRecordEntity>,
        private val failSave: Boolean = false
    ) : TimeRecordRepository {
        override suspend fun load() = records.sortedByDescending { it.startAt }

        override suspend fun save(
            id: String?,
            title: String,
            category: EventCategory,
            startAt: Instant,
            endAt: Instant,
            now: Instant
        ): String {
            if (failSave) error("磁盘不可用")
            val targetId = id ?: "record-${records.size}"
            records.removeAll { it.id == targetId }
            records += TimeRecordEntity(targetId, title.trim(), category, startAt, endAt, "manual", null, null, now, now)
            return targetId
        }

        override suspend fun delete(id: String) {
            require(records.removeAll { it.id == id }) { "时间记录不存在" }
        }
    }
}
