package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.ScheduleExpansionRules
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证普通日程与课程按当地日历展开，并保持稳定顺序。 */
class ScheduleExpansionRulesTest {
    private val fall = TermPeriod(
        id = "term",
        academicYearStart = 2026,
        season = TermSeason.FALL,
        startDate = LocalDate.of(2026, 9, 2),
        endDate = LocalDate.of(2027, 1, 15)
    )

    @Test
    fun expandWeek_appliesEffectiveDateTermWeekAndStableIdOrdering() {
        val schedules = listOf(
            ScheduleRule(
                id = "b",
                title = "跑步",
                category = EventCategory.HIGH_QUALITY_ENTERTAINMENT,
                type = ScheduleType.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY),
                effectiveFrom = LocalDate.of(2026, 9, 7),
                date = null,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                isEnabled = true
            ),
            ScheduleRule(
                id = "a",
                title = "复盘",
                category = EventCategory.WORK,
                type = ScheduleType.ONE_OFF,
                weekdays = emptySet(),
                effectiveFrom = null,
                date = LocalDate.of(2026, 9, 7),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                isEnabled = true
            )
        )
        val courses = listOf(
            CourseRule(
                id = "course",
                termId = "term",
                courseName = "高等数学",
                weekdays = setOf(DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(9, 30),
                startWeek = 2,
                endWeek = 2
            )
        )

        val instances = ScheduleExpansionRules.expandWeek(
            weekDate = LocalDate.of(2026, 9, 8),
            schedules = schedules,
            courses = courses,
            terms = listOf(fall),
            zoneId = ZoneId.of("Asia/Shanghai")
        )

        assertEquals(listOf("a", "b", "course"), instances.map { it.sourceId })
        assertEquals(EventCategory.STUDY, instances.last().category)
    }

    @Test
    fun expandWeek_usesRealInstantsAcrossDaylightSavingGap() {
        val schedule = ScheduleRule(
            id = "dst",
            title = "夏令时测试",
            category = EventCategory.WORK,
            type = ScheduleType.ONE_OFF,
            weekdays = emptySet(),
            effectiveFrom = null,
            date = LocalDate.of(2026, 3, 8),
            startTime = LocalTime.of(1, 30),
            endTime = LocalTime.of(3, 30),
            isEnabled = true
        )

        val instance = ScheduleExpansionRules.expandWeek(
            weekDate = LocalDate.of(2026, 3, 8),
            schedules = listOf(schedule),
            courses = emptyList(),
            terms = emptyList(),
            zoneId = ZoneId.of("America/New_York")
        ).single()

        assertEquals(60, Duration.between(instance.startAt, instance.endAt).toMinutes())
        assertTrue(instance.endAt.isAfter(instance.startAt))
    }
}
