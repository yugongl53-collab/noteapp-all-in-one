package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.domain.model.ReminderConfiguration
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 为每条业务规则计算唯一的下一次提醒，避免为无限循环日程预建闹钟。 */
object ReminderScheduleRules {
    fun nextCandidates(
        schedules: List<ScheduleRule>,
        courses: List<CourseRule>,
        terms: List<TermPeriod>,
        configurations: List<ReminderConfiguration>,
        now: Instant,
        zoneId: ZoneId
    ): List<ReminderCandidate> {
        val configurationBySource = configurations.associateBy { it.source to it.sourceId }
        val termById = terms.associateBy { it.id }

        val taskCandidates = schedules.mapNotNull { rule ->
            val configuration = configurationBySource[ScheduleSource.TASK to rule.id]
                ?: return@mapNotNull null
            if (!rule.isEnabled || !configuration.enabled) return@mapNotNull null
            val advanceMinutes = requireAdvanceMinutes(configuration)
            nextTaskDate(rule, now, zoneId)?.let { date ->
                val startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant()
                ReminderCandidate(
                    source = ScheduleSource.TASK,
                    sourceId = rule.id,
                    title = TextRules.normalizeRequiredText(rule.title, "日程名称"),
                    location = null,
                    startAt = startAt,
                    remindAt = startAt.minusSeconds(advanceMinutes * 60L)
                )
            }
        }
        val courseCandidates = courses.mapNotNull { rule ->
            val configuration = configurationBySource[ScheduleSource.COURSE to rule.id]
                ?: return@mapNotNull null
            if (!configuration.enabled) return@mapNotNull null
            val term = termById[rule.termId] ?: return@mapNotNull null
            val advanceMinutes = requireAdvanceMinutes(configuration)
            nextCourseDate(rule, term, now, zoneId)?.let { date ->
                val startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant()
                ReminderCandidate(
                    source = ScheduleSource.COURSE,
                    sourceId = rule.id,
                    title = TextRules.normalizeRequiredText(rule.courseName, "课程名称"),
                    location = TextRules.normalizeRequiredText(rule.location, "上课地点"),
                    startAt = startAt,
                    remindAt = startAt.minusSeconds(advanceMinutes * 60L)
                )
            }
        }

        return (taskCandidates + courseCandidates).sortedWith(
            compareBy<ReminderCandidate>({ it.remindAt }, { it.source }, { it.sourceId })
        )
    }

    private fun nextTaskDate(rule: ScheduleRule, now: Instant, zoneId: ZoneId): LocalDate? {
        validateLocalRange(rule.startTime, rule.endTime)
        return when (rule.type) {
            ScheduleType.ONE_OFF -> requireNotNull(rule.date) { "单次日程缺少日期" }
                .takeIf { date -> date.atTime(rule.startTime).atZone(zoneId).toInstant() > now }

            ScheduleType.WEEKLY -> {
                val effectiveFrom = requireNotNull(rule.effectiveFrom) { "循环日程缺少生效日期" }
                require(rule.weekdays.isNotEmpty()) { "循环日程至少选择一个星期" }
                val today = now.atZone(zoneId).toLocalDate()
                val searchStart = maxOf(today, effectiveFrom)
                // 最多检查八天，覆盖“今天已开始后顺延到下周同一天”的边界。
                (0L..7L).asSequence()
                    .map(searchStart::plusDays)
                    .firstOrNull { date ->
                        date.dayOfWeek in rule.weekdays &&
                            date.atTime(rule.startTime).atZone(zoneId).toInstant() > now
                    }
            }
        }
    }

    private fun nextCourseDate(
        rule: CourseRule,
        term: TermPeriod,
        now: Instant,
        zoneId: ZoneId
    ): LocalDate? {
        validateLocalRange(rule.startTime, rule.endTime)
        require(rule.weekdays.isNotEmpty()) { "课程至少选择一个星期" }
        require(rule.startWeek > 0 && rule.startWeek <= rule.endWeek) { "课程周次范围无效" }
        val today = now.atZone(zoneId).toLocalDate()
        val firstTeachingWeek = AcademicCalendarRules.weekStart(term.startDate)
            .plusWeeks((rule.startWeek - 1).toLong())
        val searchStart = maxOf(today, term.startDate, firstTeachingWeek)
        if (searchStart > term.endDate) return null

        // 从第一个可能日期向后八天即可覆盖下一次命中的星期，周次规则负责裁剪学期边界。
        return (0L..7L).asSequence()
            .map(searchStart::plusDays)
            .takeWhile { it <= term.endDate }
            .firstOrNull { date ->
                val week = AcademicCalendarRules.termWeek(term, date)
                week != null && week in rule.startWeek..rule.endWeek &&
                    date.dayOfWeek in rule.weekdays &&
                    date.atTime(rule.startTime).atZone(zoneId).toInstant() > now
            }
    }

    private fun requireAdvanceMinutes(configuration: ReminderConfiguration): Int {
        val advanceMinutes = requireNotNull(configuration.advanceMinutes) { "启用提醒时必须设置提前分钟数" }
        require(advanceMinutes >= 0) { "提醒提前分钟数不能为负数" }
        return advanceMinutes
    }

    private fun validateLocalRange(start: LocalTime, end: LocalTime) {
        require(end > start) { "结束时刻必须晚于开始时刻，MVP 不允许跨午夜" }
    }
}
