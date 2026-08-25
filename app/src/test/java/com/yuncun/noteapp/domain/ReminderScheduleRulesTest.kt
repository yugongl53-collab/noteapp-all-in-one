package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ReminderConfiguration
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.ReminderScheduleRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 M4 只调度每条规则的下一实例，并正确处理错过提醒窗口等边界。 */
class ReminderScheduleRulesTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun oneOffReminder_insideMissedWindow_isMarkedForImmediateDelivery() {
        val now = Instant.parse("2026-08-25T01:57:00Z")
        val candidates = ReminderScheduleRules.nextCandidates(
            schedules = listOf(oneOffTask("task", LocalDate.parse("2026-08-25"), LocalTime.of(10, 0))),
            courses = emptyList(),
            terms = emptyList(),
            configurations = listOf(configuration(ScheduleSource.TASK, "task", 5)),
            now = now,
            zoneId = zoneId
        )

        assertEquals(1, candidates.size)
        assertEquals(Instant.parse("2026-08-25T01:55:00Z"), candidates.single().remindAt)
        assertTrue(candidates.single().shouldNotifyImmediately(now))
    }

    @Test
    fun eventAlreadyStarted_isNotReturnedForCatchUp() {
        val candidates = ReminderScheduleRules.nextCandidates(
            schedules = listOf(oneOffTask("task", LocalDate.parse("2026-08-25"), LocalTime.of(9, 0))),
            courses = emptyList(),
            terms = emptyList(),
            configurations = listOf(configuration(ScheduleSource.TASK, "task", 5)),
            now = Instant.parse("2026-08-25T01:30:00Z"),
            zoneId = zoneId
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun weeklyRule_returnsOnlyNearestFutureInstance() {
        val rule = ScheduleRule(
            id = "weekly",
            title = "锻炼",
            category = EventCategory.WORK,
            type = ScheduleType.WEEKLY,
            weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            effectiveFrom = LocalDate.parse("2026-08-01"),
            date = null,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            isEnabled = true
        )

        val candidates = ReminderScheduleRules.nextCandidates(
            schedules = listOf(rule),
            courses = emptyList(),
            terms = emptyList(),
            configurations = listOf(configuration(ScheduleSource.TASK, "weekly", 5)),
            now = Instant.parse("2026-08-25T00:00:00Z"),
            zoneId = zoneId
        )

        assertEquals(1, candidates.size)
        assertEquals(Instant.parse("2026-08-25T02:00:00Z"), candidates.single().startAt)
        assertFalse(candidates.single().shouldNotifyImmediately(Instant.parse("2026-08-25T00:00:00Z")))
    }

    @Test
    fun courseBeforeItsTeachingWeeks_findsFirstConfiguredWeek() {
        val term = TermPeriod(
            id = "term",
            academicYearStart = 2026,
            season = TermSeason.FALL,
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2027-01-15")
        )
        val course = CourseRule(
            id = "course",
            termId = term.id,
            courseName = "高等数学",
            location = "一教 101",
            weekdays = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 30),
            startWeek = 3,
            endWeek = 10
        )

        val candidate = ReminderScheduleRules.nextCandidates(
            schedules = emptyList(),
            courses = listOf(course),
            terms = listOf(term),
            configurations = listOf(configuration(ScheduleSource.COURSE, "course", 25)),
            now = Instant.parse("2026-08-25T00:00:00Z"),
            zoneId = zoneId
        ).single()

        assertEquals(Instant.parse("2026-09-13T23:35:00Z"), candidate.remindAt)
    }

    @Test
    fun disabledTaskAndDisabledReminder_areExcluded() {
        val disabledTask = oneOffTask("disabled-task", LocalDate.parse("2026-08-26"), LocalTime.of(10, 0))
            .copy(isEnabled = false)
        val reminderOff = oneOffTask("reminder-off", LocalDate.parse("2026-08-26"), LocalTime.of(11, 0))

        val candidates = ReminderScheduleRules.nextCandidates(
            schedules = listOf(disabledTask, reminderOff),
            courses = emptyList(),
            terms = emptyList(),
            configurations = listOf(
                configuration(ScheduleSource.TASK, "disabled-task", 5),
                ReminderConfiguration(ScheduleSource.TASK, "reminder-off", false, null)
            ),
            now = Instant.parse("2026-08-25T00:00:00Z"),
            zoneId = zoneId
        )

        assertTrue(candidates.isEmpty())
    }

    private fun oneOffTask(id: String, date: LocalDate, start: LocalTime) = ScheduleRule(
        id = id,
        title = "事件-$id",
        category = EventCategory.WORK,
        type = ScheduleType.ONE_OFF,
        weekdays = emptySet(),
        effectiveFrom = null,
        date = date,
        startTime = start,
        endTime = start.plusHours(1),
        isEnabled = true
    )

    private fun configuration(source: ScheduleSource, id: String, advance: Int) =
        ReminderConfiguration(source, id, true, advance)
}
