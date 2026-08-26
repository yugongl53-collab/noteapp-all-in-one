package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.ScheduleAutoSettlementRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAutoSettlementRulesTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val term = TermPeriod(
        id = "term-2026-fall",
        academicYearStart = 2026,
        season = TermSeason.FALL,
        startDate = LocalDate.parse("2026-09-07"), // 周一，第1周
        endDate = LocalDate.parse("2027-01-17")
    )

    @Test
    fun unsettledEndedCandidates_filtersEndedOneOffAndWeeklyTasks() {
        // 当前时间为 2026-09-10 (周四) 15:00
        val now = LocalDate.parse("2026-09-10").atTime(15, 0).atZone(zoneId).toInstant()

        val oneOffEnded = ScheduleRule(
            id = "task-1",
            title = "开学班会",
            category = EventCategory.WORK,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-08"),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            isEnabled = true
        )

        val oneOffFuture = ScheduleRule(
            id = "task-2",
            title = "周末大扫除",
            category = EventCategory.WORK,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-12"),
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            isEnabled = true
        )

        val weeklyTask = ScheduleRule(
            id = "task-3",
            title = "每日晨读",
            category = EventCategory.STUDY,
            type = ScheduleType.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            effectiveFrom = LocalDate.parse("2026-09-07"),
            date = null,
            startTime = LocalTime.of(7, 30),
            endTime = LocalTime.of(8, 30),
            isEnabled = true
        )

        val disabledTask = ScheduleRule(
            id = "task-4",
            title = "已停用日程",
            category = EventCategory.SOCIAL,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-09"),
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            isEnabled = false
        )

        val course = CourseRule(
            id = "course-1",
            termId = term.id,
            courseName = "高等数学",
            location = "一教 101",
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 40),
            startWeek = 1,
            endWeek = 16
        )

        val candidates = ScheduleAutoSettlementRules.unsettledEndedCandidates(
            schedules = listOf(oneOffEnded, oneOffFuture, weeklyTask, disabledTask),
            courses = listOf(course),
            terms = listOf(term),
            now = now,
            zoneId = zoneId,
            settledKeys = emptySet()
        )

        // 预期候选：
        // 1. weeklyTask 周一 (2026-09-07 07:30~08:30)
        // 2. course 周一 (2026-09-07 08:00~09:40)
        // 3. weeklyTask 周二 (2026-09-08 07:30~08:30)
        // 4. oneOffEnded 周二 (2026-09-08 09:00~10:30)
        // 5. weeklyTask 周三 (2026-09-09 07:30~08:30)
        // 6. course 周三 (2026-09-09 08:00~09:40)
        // 7. weeklyTask 周四 (2026-09-10 07:30~08:30)
        // weeklyTask 周五、oneOffFuture 在未来，未结算；disabledTask 已停用
        assertEquals(7, candidates.size)
        assertEquals(listOf("每日晨读", "高等数学", "每日晨读", "开学班会", "每日晨读", "高等数学", "每日晨读"), candidates.map { it.title })
        assertTrue(candidates.all { it.endAt <= now })
        assertEquals(EventCategory.STUDY, candidates.first { it.source == ScheduleSource.COURSE }.category)
    }

    @Test
    fun unsettledEndedCandidates_excludesAlreadySettledKeys() {
        val now = LocalDate.parse("2026-09-10").atTime(15, 0).atZone(zoneId).toInstant()
        val oneOffEnded = ScheduleRule(
            id = "task-1",
            title = "开学班会",
            category = EventCategory.WORK,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-08"),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            isEnabled = true
        )
        val startAt = LocalDate.parse("2026-09-08").atTime(9, 0).atZone(zoneId).toInstant()
        val endAt = LocalDate.parse("2026-09-08").atTime(10, 30).atZone(zoneId).toInstant()
        val key = ScheduleAutoSettlementRules.buildInstanceKey(ScheduleSource.TASK, "task-1", startAt, endAt)

        val candidates = ScheduleAutoSettlementRules.unsettledEndedCandidates(
            schedules = listOf(oneOffEnded),
            courses = emptyList(),
            terms = emptyList(),
            now = now,
            zoneId = zoneId,
            settledKeys = setOf(key)
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun nextEndingInstant_findsEarliestFutureEndTime() {
        val now = LocalDate.parse("2026-09-10").atTime(15, 0).atZone(zoneId).toInstant()
        val taskTodayEvening = ScheduleRule(
            id = "task-evening",
            title = "晚自习",
            category = EventCategory.STUDY,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-10"),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(21, 0),
            isEnabled = true
        )
        val taskTomorrow = ScheduleRule(
            id = "task-tomorrow",
            title = "晨练",
            category = EventCategory.HIGH_QUALITY_ENTERTAINMENT,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.parse("2026-09-11"),
            startTime = LocalTime.of(6, 0),
            endTime = LocalTime.of(7, 0),
            isEnabled = true
        )

        val nextInstant = ScheduleAutoSettlementRules.nextEndingInstant(
            schedules = listOf(taskTodayEvening, taskTomorrow),
            courses = emptyList(),
            terms = emptyList(),
            now = now,
            zoneId = zoneId
        )

        val expected = LocalDate.parse("2026-09-10").atTime(21, 0).atZone(zoneId).toInstant()
        assertEquals(expected, nextInstant)
    }
}
