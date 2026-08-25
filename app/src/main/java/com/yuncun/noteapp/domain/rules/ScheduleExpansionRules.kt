package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 将当地日历规则按需展开为一周内的绝对时间实例。 */
object ScheduleExpansionRules {
    fun expandWeek(
        weekDate: LocalDate,
        schedules: List<ScheduleRule>,
        courses: List<CourseRule>,
        terms: List<TermPeriod>,
        zoneId: ZoneId
    ): List<ScheduleInstance> {
        val weekStart = AcademicCalendarRules.weekStart(weekDate)
        val dates = (0L..6L).map(weekStart::plusDays)
        val termById = terms.associateBy { it.id }

        val taskInstances = schedules.asSequence()
            .filter { it.isEnabled }
            .flatMap { schedule ->
                dates.asSequence()
                    .filter { date -> scheduleOccursOn(schedule, date) }
                    .map { date -> taskInstance(schedule, date, zoneId) }
            }
        val courseInstances = courses.asSequence().flatMap { course ->
            val term = termById[course.termId] ?: return@flatMap emptySequence()
            dates.asSequence()
                .filter { date -> courseOccursOn(course, term, date) }
                .map { date -> courseInstance(course, date, zoneId) }
        }

        return (taskInstances + courseInstances).sortedWith(
            compareBy<ScheduleInstance>({ it.startAt }, { it.endAt }, { it.sourceId })
        ).toList()
    }

    private fun scheduleOccursOn(schedule: ScheduleRule, date: LocalDate): Boolean = when (schedule.type) {
        ScheduleType.WEEKLY -> {
            val effectiveFrom = requireNotNull(schedule.effectiveFrom) { "循环日程缺少生效日期" }
            require(schedule.weekdays.isNotEmpty()) { "循环日程至少选择一个星期" }
            date >= effectiveFrom && date.dayOfWeek in schedule.weekdays
        }
        ScheduleType.ONE_OFF -> {
            val scheduledDate = requireNotNull(schedule.date) { "单次日程缺少日期" }
            date == scheduledDate
        }
    }

    private fun courseOccursOn(course: CourseRule, term: TermPeriod, date: LocalDate): Boolean {
        require(course.weekdays.isNotEmpty()) { "课程至少选择一个星期" }
        require(course.startWeek > 0 && course.startWeek <= course.endWeek) { "课程周次范围无效" }
        val week = AcademicCalendarRules.termWeek(term, date) ?: return false
        return week in course.startWeek..course.endWeek && date.dayOfWeek in course.weekdays
    }

    private fun taskInstance(rule: ScheduleRule, date: LocalDate, zoneId: ZoneId): ScheduleInstance {
        validateLocalRange(rule.startTime, rule.endTime)
        return ScheduleInstance(
            sourceId = rule.id,
            source = ScheduleSource.TASK,
            title = TextRules.normalizeRequiredText(rule.title, "日程名称"),
            category = requireSelectable(rule.category),
            startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant(),
            endAt = date.atTime(rule.endTime).atZone(zoneId).toInstant()
        )
    }

    private fun courseInstance(rule: CourseRule, date: LocalDate, zoneId: ZoneId): ScheduleInstance {
        validateLocalRange(rule.startTime, rule.endTime)
        return ScheduleInstance(
            sourceId = rule.id,
            source = ScheduleSource.COURSE,
            title = TextRules.normalizeRequiredText(rule.courseName, "课程名称"),
            category = EventCategory.STUDY,
            startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant(),
            endAt = date.atTime(rule.endTime).atZone(zoneId).toInstant()
        )
    }

    private fun validateLocalRange(start: LocalTime, end: LocalTime) {
        require(end > start) { "结束时刻必须晚于开始时刻，MVP 不允许跨午夜" }
    }

    private fun requireSelectable(category: EventCategory): EventCategory {
        require(category in EventCategory.selectable) { "业务实体不能保存其他事件性质" }
        return category
    }
}
