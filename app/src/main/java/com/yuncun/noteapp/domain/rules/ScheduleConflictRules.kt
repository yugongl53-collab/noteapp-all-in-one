package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import java.time.LocalDate
import java.time.LocalTime

/** 保存前只报告潜在重叠，不把重叠当作非法数据。 */
object ScheduleConflictRules {
    fun tasksConflict(first: ScheduleRule, second: ScheduleRule): Boolean {
        if (!first.isEnabled || !second.isEnabled || !timesOverlap(first.startTime, first.endTime, second.startTime, second.endTime)) {
            return false
        }
        return when {
            first.type == ScheduleType.ONE_OFF && second.type == ScheduleType.ONE_OFF -> first.date == second.date
            first.type == ScheduleType.WEEKLY && second.type == ScheduleType.WEEKLY ->
                first.weekdays.any { it in second.weekdays }
            first.type == ScheduleType.ONE_OFF -> taskOccursOn(second, requireNotNull(first.date))
            else -> taskOccursOn(first, requireNotNull(second.date))
        }
    }

    fun coursesConflict(first: CourseRule, second: CourseRule): Boolean =
        first.termId == second.termId &&
            first.weekdays.any { it in second.weekdays } &&
            first.startWeek <= second.endWeek && second.startWeek <= first.endWeek &&
            timesOverlap(first.startTime, first.endTime, second.startTime, second.endTime)

    /** 课程日期范围有限，逐日检查能完整覆盖生效日、周次和星期三类边界。 */
    fun taskAndCourseConflict(task: ScheduleRule, course: CourseRule, term: TermPeriod): Boolean {
        if (!task.isEnabled || !timesOverlap(task.startTime, task.endTime, course.startTime, course.endTime)) return false
        var date = term.startDate
        while (date <= term.endDate) {
            val termWeek = AcademicCalendarRules.termWeek(term, date)
            val courseOccurs = termWeek != null && termWeek in course.startWeek..course.endWeek && date.dayOfWeek in course.weekdays
            if (courseOccurs && taskOccursOn(task, date)) return true
            date = date.plusDays(1)
        }
        return false
    }

    private fun taskOccursOn(task: ScheduleRule, date: LocalDate): Boolean = when (task.type) {
        ScheduleType.WEEKLY -> date >= requireNotNull(task.effectiveFrom) && date.dayOfWeek in task.weekdays
        ScheduleType.ONE_OFF -> date == task.date
    }

    private fun timesOverlap(firstStart: LocalTime, firstEnd: LocalTime, secondStart: LocalTime, secondEnd: LocalTime): Boolean =
        firstStart < secondEnd && secondStart < firstEnd
}
