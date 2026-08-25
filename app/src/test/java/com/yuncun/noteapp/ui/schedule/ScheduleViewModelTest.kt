package com.yuncun.noteapp.ui.schedule

import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleSnapshot
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/** 验证 M3 状态层从同一快照派生双视图，并保留重叠二次确认。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-25T02:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_derivesWeekInstancesAndNameSuggestions() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf(task("task", " 周会 ", LocalTime.of(9, 0))))
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("task"), state.instances.map { it.sourceId })
        assertEquals(listOf("周会"), state.taskNameSuggestions)
    }

    @Test
    fun overlappingSave_waitsForConfirmationThenPersists() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf(task("existing", "周会", LocalTime.of(9, 0))))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.saveTask(null, input("复盘", LocalTime.of(9, 30)))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.overlapConfirmationRequired)
        assertEquals(1, repository.tasks.size)

        viewModel.confirmOverlapSave()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.overlapConfirmationRequired)
        assertEquals(2, repository.tasks.size)
        assertEquals("普通事件已保存", viewModel.uiState.value.feedback)
    }

    private fun viewModel(repository: ScheduleRepository) = ScheduleViewModel(
        repository = repository,
        clock = { now },
        zoneId = ZoneId.of("Asia/Shanghai"),
        today = { LocalDate.parse("2026-08-25") }
    )

    private fun input(title: String, start: LocalTime) = ScheduleTaskInput(
        title = title,
        category = EventCategory.WORK,
        type = ScheduleType.WEEKLY,
        weekdays = setOf(DayOfWeek.TUESDAY),
        effectiveFrom = LocalDate.parse("2026-08-24"),
        date = null,
        startTime = start,
        endTime = start.plusHours(1),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5
    )

    private fun task(id: String, title: String, start: LocalTime) = ScheduleTaskEntity(
        id = id,
        title = title,
        category = EventCategory.WORK,
        type = ScheduleType.WEEKLY,
        weekdays = setOf(DayOfWeek.TUESDAY),
        effectiveFrom = LocalDate.parse("2026-08-24"),
        date = null,
        startTime = start,
        endTime = start.plusHours(1),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = now,
        updatedAt = now
    )

    private class FakeScheduleRepository(
        val tasks: MutableList<ScheduleTaskEntity>
    ) : ScheduleRepository {
        override suspend fun load() = ScheduleSnapshot(emptyList(), tasks.toList(), emptyList())
        override suspend fun saveTerm(id: String?, input: AcademicTermInput, now: Instant) = error("测试未使用")
        override suspend fun deleteTerm(id: String) = Unit

        override suspend fun saveTask(id: String?, input: ScheduleTaskInput, now: Instant): String {
            val entity = ScheduleTaskEntity(
                id = id ?: "task-${tasks.size}",
                title = input.title,
                category = input.category,
                type = input.type,
                weekdays = input.weekdays,
                effectiveFrom = input.effectiveFrom,
                date = input.date,
                startTime = input.startTime,
                endTime = input.endTime,
                isEnabled = input.isEnabled,
                reminderEnabled = input.reminderEnabled,
                reminderAdvanceMinutes = input.reminderAdvanceMinutes,
                createdAt = now,
                updatedAt = now
            )
            tasks.removeAll { it.id == entity.id }
            tasks += entity
            return entity.id
        }

        override suspend fun deleteTask(id: String) {
            tasks.removeAll { it.id == id }
        }

        override suspend fun saveCourse(id: String?, input: CourseScheduleInput, now: Instant) = error("测试未使用")
        override suspend fun deleteCourse(id: String) = Unit
    }
}
