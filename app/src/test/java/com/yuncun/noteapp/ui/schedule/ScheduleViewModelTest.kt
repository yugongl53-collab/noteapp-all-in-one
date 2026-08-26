package com.yuncun.noteapp.ui.schedule

import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleSnapshot
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.reminder.ReminderCoordinator
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.reminder.ReminderSyncResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun initialLoad_derivesCurrentPeriodIndependentlyFromBrowsedWeek() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(
            tasks = mutableListOf(),
            terms = listOf(term())
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.previousWeek()

        assertEquals("2026-2027秋季学期 · 第1周", viewModel.uiState.value.currentPeriodLabel)
        assertEquals(LocalDate.parse("2026-08-17"), viewModel.uiState.value.selectedWeek)
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

    @Test
    fun successfulSave_rebuildsRemindersFromPersistedSnapshot() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf())
        val reminders = FakeReminderCoordinator()
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.saveTask(null, input("复盘", LocalTime.of(11, 0)))
        advanceUntilIdle()

        assertEquals(1, reminders.synchronizeCount)
        assertEquals("普通事件已保存", viewModel.uiState.value.feedback)
    }

    @Test
    fun reminderRebuildFailure_keepsSavedDataAndReportsSpecificFailure() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf())
        val reminders = FakeReminderCoordinator(
            result = ReminderSyncResult(
                permissions = ReminderPermissionState(true, true),
                errorMessage = "系统拒绝精确闹钟"
            )
        )
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.saveTask(null, input("复盘", LocalTime.of(11, 0)))
        advanceUntilIdle()

        assertEquals(1, repository.tasks.size)
        assertEquals("普通事件已保存，但提醒未生效：系统拒绝精确闹钟", viewModel.uiState.value.feedback)
    }

    @Test
    fun reminderEnabledSave_withoutPermissions_reportsConfiguredButInactive() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf())
        val reminders = FakeReminderCoordinator(
            result = ReminderSyncResult(ReminderPermissionState(false, false))
        )
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.saveTask(null, input("复盘", LocalTime.of(11, 0)))
        advanceUntilIdle()

        assertEquals(
            "普通事件已保存；提醒已配置但未生效：缺少通知权限、“闹钟和提醒”权限",
            viewModel.uiState.value.feedback
        )
    }

    @Test
    fun reminderDisabledSave_withoutPermissions_doesNotClaimReminderConfigured() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(mutableListOf())
        val reminders = FakeReminderCoordinator(
            result = ReminderSyncResult(ReminderPermissionState(false, false))
        )
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.saveTask(
            null,
            input("复盘", LocalTime.of(11, 0)).copy(
                reminderEnabled = false,
                reminderAdvanceMinutes = null
            )
        )
        advanceUntilIdle()

        assertEquals("普通事件已保存", viewModel.uiState.value.feedback)
    }

    @Test
    fun deleteTask_removesTaskAndSynchronizesReminders() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(tasks = mutableListOf(task("task", "周会", LocalTime.of(9, 0))))
        val reminders = FakeReminderCoordinator()
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        viewModel.deleteTask("task")
        advanceUntilIdle()

        assertEquals(0, repository.tasks.size)
        assertEquals(0, viewModel.uiState.value.instances.size)
        assertEquals(1, reminders.synchronizeCount)
        assertEquals("普通事件已删除", viewModel.uiState.value.feedback)
    }

    @Test
    fun deleteCourse_removesCourseAndSynchronizesReminders() = runTest(dispatcher) {
        val courseEntity = CourseScheduleEntity(
            id = "course-1",
            termId = "fall",
            courseName = "高等数学",
            location = "一教 101",
            category = EventCategory.STUDY,
            weekdays = setOf(DayOfWeek.TUESDAY),
            startWeek = 1,
            endWeek = 16,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 35),
            reminderEnabled = true,
            reminderAdvanceMinutes = 10,
            createdAt = now,
            updatedAt = now
        )
        val repository = FakeScheduleRepository(
            tasks = mutableListOf(),
            courses = mutableListOf(courseEntity),
            terms = listOf(term())
        )
        val reminders = FakeReminderCoordinator()
        val viewModel = viewModel(repository, reminders)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.instances.size)

        viewModel.deleteCourse("course-1")
        advanceUntilIdle()

        assertEquals(0, repository.courses.size)
        assertEquals(0, viewModel.uiState.value.instances.size)
        assertEquals(1, reminders.synchronizeCount)
        assertEquals("课程已删除", viewModel.uiState.value.feedback)
    }

    @Test
    fun saveTask_synchronizesSettlement() = runTest(dispatcher) {
        val repository = FakeScheduleRepository()
        var settlementSyncCount = 0
        val fakeSettlement = object : com.yuncun.noteapp.settlement.ScheduleSettlementCoordinator {
            override suspend fun synchronize(): com.yuncun.noteapp.settlement.SettlementSyncResult {
                settlementSyncCount++
                return com.yuncun.noteapp.settlement.SettlementSyncResult()
            }
        }
        val viewModel = ScheduleViewModel(
            repository = repository,
            reminderCoordinator = FakeReminderCoordinator(),
            settlementCoordinator = fakeSettlement,
            clock = { now },
            zoneId = ZoneId.of("Asia/Shanghai"),
            today = { LocalDate.parse("2026-08-25") }
        )
        advanceUntilIdle()

        viewModel.saveTask(null, input("临时研讨", LocalTime.of(14, 0)))
        advanceUntilIdle()

        assertEquals(1, settlementSyncCount)
        assertEquals("普通事件已保存", viewModel.uiState.value.feedback)
    }

    private fun viewModel(
        repository: ScheduleRepository,
        reminders: ReminderCoordinator = FakeReminderCoordinator()
    ) = ScheduleViewModel(
        repository = repository,
        reminderCoordinator = reminders,
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

    private fun term() = AcademicTermEntity(
        id = "fall",
        academicYearStart = 2026,
        season = TermSeason.FALL,
        startDate = LocalDate.parse("2026-08-24"),
        endDate = LocalDate.parse("2027-01-15"),
        createdAt = now,
        updatedAt = now
    )

    private class FakeScheduleRepository(
        val tasks: MutableList<ScheduleTaskEntity> = mutableListOf(),
        val courses: MutableList<CourseScheduleEntity> = mutableListOf(),
        private val terms: List<AcademicTermEntity> = emptyList()
    ) : ScheduleRepository {
        override suspend fun load() = ScheduleSnapshot(terms, tasks.toList(), courses.toList())
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
        override suspend fun deleteCourse(id: String) {
            courses.removeAll { it.id == id }
        }
    }

    private class FakeReminderCoordinator(
        private val result: ReminderSyncResult = ReminderSyncResult(ReminderPermissionState(true, true))
    ) : ReminderCoordinator {
        private val permissions = MutableStateFlow(result.permissions)
        override val permissionState: StateFlow<ReminderPermissionState> = permissions
        var synchronizeCount = 0

        override fun refreshPermissionState() = Unit
        override suspend fun cancelScheduled() = Unit

        override suspend fun synchronize(): ReminderSyncResult {
            synchronizeCount += 1
            return result
        }

        override suspend fun handleTriggered(candidate: ReminderCandidate) = Unit
    }
}
