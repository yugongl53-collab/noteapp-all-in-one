package com.yuncun.noteapp.settlement

import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleSnapshot
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.data.repository.TimeRecordRepository
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScheduleSettlementCoordinatorTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.parse("2026-09-10").atTime(15, 0).atZone(zoneId).toInstant()

    private val term = AcademicTermEntity(
        id = "term-1",
        academicYearStart = 2026,
        season = TermSeason.FALL,
        startDate = LocalDate.parse("2026-09-07"),
        endDate = LocalDate.parse("2027-01-17"),
        createdAt = now,
        updatedAt = now
    )

    private val task = ScheduleTaskEntity(
        id = "task-1",
        title = "项目周会",
        category = EventCategory.WORK,
        type = ScheduleType.ONE_OFF,
        weekdays = emptySet(),
        effectiveFrom = null,
        date = LocalDate.parse("2026-09-08"),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = now,
        updatedAt = now
    )

    private val futureTask = ScheduleTaskEntity(
        id = "task-future",
        title = "晚间自习",
        category = EventCategory.STUDY,
        type = ScheduleType.ONE_OFF,
        weekdays = emptySet(),
        effectiveFrom = null,
        date = LocalDate.parse("2026-09-10"),
        startTime = LocalTime.of(19, 0),
        endTime = LocalTime.of(21, 0),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = now,
        updatedAt = now
    )

    private val course = CourseScheduleEntity(
        id = "course-1",
        termId = "term-1",
        courseName = "操作系统",
        location = "二教 201",
        category = EventCategory.STUDY,
        weekdays = linkedSetOf(DayOfWeek.MONDAY),
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 40),
        startWeek = 1,
        endWeek = 16,
        reminderEnabled = true,
        reminderAdvanceMinutes = 25,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun synchronize_settlesEndedTaskAndCourse_andSchedulesNextAlarm() = runBlocking {
        val scheduleRepo = FakeScheduleRepository(
            tasks = mutableListOf(task, futureTask),
            courses = mutableListOf(course),
            terms = mutableListOf(term)
        )
        val timeRecordRepo = FakeTimeRecordRepository()
        val registry = InMemorySettlementRegistry()
        val alarmGateway = FakeSettlementAlarmGateway()

        val coordinator = DefaultScheduleSettlementCoordinator(
            scheduleRepository = scheduleRepo,
            timeRecordRepository = timeRecordRepo,
            registry = registry,
            alarmGateway = alarmGateway,
            clock = { now },
            zoneIdProvider = { zoneId }
        )

        val result = coordinator.synchronize()

        // task-1 (周二) 和 course-1 (周一) 均已结束，应被自动结算
        assertEquals(2, result.settledCount)
        assertEquals(0, result.skippedCount)
        assertEquals(2, timeRecordRepo.records.size)

        val taskRecord = timeRecordRepo.records.first { it.relatedTaskId == "task-1" }
        assertEquals("项目周会", taskRecord.title)
        assertEquals(EventCategory.WORK, taskRecord.category)
        assertEquals("schedule", taskRecord.source)

        val courseRecord = timeRecordRepo.records.first { it.title == "操作系统" }
        assertEquals(EventCategory.STUDY, courseRecord.category)
        assertEquals("schedule", courseRecord.source)

        // 防重注册表记录了 2 个 key
        assertEquals(2, registry.read().size)

        // 下一次结算时刻指向 futureTask 的结束时间 (2026-09-10 21:00)
        val expectedFutureEnd = LocalDate.parse("2026-09-10").atTime(21, 0).atZone(zoneId).toInstant()
        assertEquals(expectedFutureEnd, result.nextSettlementAt)
        assertEquals(expectedFutureEnd, alarmGateway.lastScheduled)
    }

    @Test
    fun synchronize_isIdempotent_doesNotDuplicateRecords() = runBlocking {
        val scheduleRepo = FakeScheduleRepository(
            tasks = mutableListOf(task),
            courses = emptyList(),
            terms = emptyList()
        )
        val timeRecordRepo = FakeTimeRecordRepository()
        val registry = InMemorySettlementRegistry()
        val alarmGateway = FakeSettlementAlarmGateway()

        val coordinator = DefaultScheduleSettlementCoordinator(
            scheduleRepository = scheduleRepo,
            timeRecordRepository = timeRecordRepo,
            registry = registry,
            alarmGateway = alarmGateway,
            clock = { now },
            zoneIdProvider = { zoneId }
        )

        val result1 = coordinator.synchronize()
        assertEquals(1, result1.settledCount)
        assertEquals(1, timeRecordRepo.records.size)

        val result2 = coordinator.synchronize()
        assertEquals(0, result2.settledCount)
        assertEquals(0, result2.skippedCount)
        assertEquals(1, timeRecordRepo.records.size)
    }

    @Test
    fun synchronize_whenOverlappingWithExistingRecord_skipsGracefully() = runBlocking {
        val scheduleRepo = FakeScheduleRepository(
            tasks = mutableListOf(task),
            courses = emptyList(),
            terms = emptyList()
        )
        val timeRecordRepo = FakeTimeRecordRepository()
        // 预先录入一条重叠的手动时间记录
        val overlapStart = LocalDate.parse("2026-09-08").atTime(9, 30).atZone(zoneId).toInstant()
        val overlapEnd = LocalDate.parse("2026-09-08").atTime(10, 30).atZone(zoneId).toInstant()
        timeRecordRepo.save(null, "既有手动记录", EventCategory.WORK, overlapStart, overlapEnd, now)

        val registry = InMemorySettlementRegistry()
        val alarmGateway = FakeSettlementAlarmGateway()

        val coordinator = DefaultScheduleSettlementCoordinator(
            scheduleRepository = scheduleRepo,
            timeRecordRepository = timeRecordRepo,
            registry = registry,
            alarmGateway = alarmGateway,
            clock = { now },
            zoneIdProvider = { zoneId }
        )

        val result = coordinator.synchronize()
        assertEquals(0, result.settledCount)
        assertEquals(1, result.skippedCount)
        // 仅保留原有的手动记录
        assertEquals(1, timeRecordRepo.records.size)
        // 该实例仍被加入注册表以避免下次重复尝试
        assertEquals(1, registry.read().size)
    }

    private class FakeScheduleRepository(
        val tasks: List<ScheduleTaskEntity> = emptyList(),
        val courses: List<CourseScheduleEntity> = emptyList(),
        val terms: List<AcademicTermEntity> = emptyList()
    ) : ScheduleRepository {
        override suspend fun load() = ScheduleSnapshot(terms, tasks, courses)
        override suspend fun saveTerm(id: String?, input: AcademicTermInput, now: Instant): String = ""
        override suspend fun deleteTerm(id: String) = Unit
        override suspend fun saveTask(id: String?, input: ScheduleTaskInput, now: Instant): String = ""
        override suspend fun deleteTask(id: String) = Unit
        override suspend fun saveCourse(id: String?, input: CourseScheduleInput, now: Instant): String = ""
        override suspend fun deleteCourse(id: String) = Unit
    }

    private class FakeTimeRecordRepository : TimeRecordRepository {
        val records = mutableListOf<TimeRecordEntity>()

        override suspend fun load(): List<TimeRecordEntity> = records

        override suspend fun save(
            id: String?,
            title: String,
            category: EventCategory,
            startAt: Instant,
            endAt: Instant,
            now: Instant
        ): String {
            val targetId = id ?: "record-${records.size}"
            records.removeAll { it.id == targetId }
            records += TimeRecordEntity(targetId, title, category, startAt, endAt, "manual", null, null, now, now)
            return targetId
        }

        override suspend fun saveAutoSettlement(
            id: String,
            title: String,
            category: EventCategory,
            startAt: Instant,
            endAt: Instant,
            relatedTaskId: String?,
            now: Instant
        ): Boolean {
            if (records.any { it.id == id || (startAt < it.endAt && endAt > it.startAt) }) return false
            records += TimeRecordEntity(id, title, category, startAt, endAt, "schedule", relatedTaskId, null, now, now)
            return true
        }

        override suspend fun delete(id: String) {
            records.removeAll { it.id == id }
        }
    }

    private class InMemorySettlementRegistry : SettlementRegistry {
        private val keys = mutableSetOf<String>()
        override fun read(): Set<String> = keys.toSet()
        override fun addSettled(keys: Set<String>) { this.keys += keys }
        override fun replace(keys: Set<String>) { this.keys.clear(); this.keys += keys }
    }

    private class FakeSettlementAlarmGateway : SettlementAlarmGateway {
        var lastScheduled: Instant? = null
        override fun scheduleNextSettlement(triggerAt: Instant) { lastScheduled = triggerAt }
        override fun cancel() { lastScheduled = null }
    }
}
