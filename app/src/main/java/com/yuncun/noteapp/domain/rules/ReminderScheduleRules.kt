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
        zoneId: ZoneId,
        excludedIds: Set<String> = emptySet()
    ): List<ReminderCandidate> {
        val configurationBySource = configurations.associateBy { it.source to it.sourceId }
        val termById = terms.associateBy { it.id }

        val taskCandidates = schedules.mapNotNull { rule ->
            val configuration = configurationBySource[ScheduleSource.TASK to rule.id]
                ?: return@mapNotNull null
            if (!rule.isEnabled || !configuration.enabled) return@mapNotNull null
            val advanceMinutes = requireAdvanceMinutes(configuration)
            nextTaskDates(rule, now, zoneId)
                .map { date -> taskCandidate(rule, date, advanceMinutes, zoneId) }
                .firstOrNull { it.id !in excludedIds }
        }
        val courseCandidates = courses.mapNotNull { rule ->
            val configuration = configurationBySource[ScheduleSource.COURSE to rule.id]
                ?: return@mapNotNull null
            if (!configuration.enabled) return@mapNotNull null
            val term = termById[rule.termId] ?: return@mapNotNull null
            val advanceMinutes = requireAdvanceMinutes(configuration)
            nextCourseDates(rule, term, now, zoneId)
                .map { date -> courseCandidate(rule, date, advanceMinutes, zoneId) }
                .firstOrNull { it.id !in excludedIds }
        }

        return (taskCandidates + courseCandidates).sortedWith(
            compareBy<ReminderCandidate>({ it.remindAt }, { it.source }, { it.sourceId })
        )
    }

    private fun nextTaskDates(rule: ScheduleRule, now: Instant, zoneId: ZoneId): Sequence<LocalDate> {
        validateLocalRange(rule.startTime, rule.endTime)
        return when (rule.type) {
            ScheduleType.ONE_OFF -> sequenceOf(requireNotNull(rule.date) { "单次日程缺少日期" })
                .filter { date -> date.atTime(rule.startTime).atZone(zoneId).toInstant() > now }

            ScheduleType.WEEKLY -> {
                val effectiveFrom = requireNotNull(rule.effectiveFrom) { "循环日程缺少生效日期" }
                require(rule.weekdays.isNotEmpty()) { "循环日程至少选择一个星期" }
                val today = now.atZone(zoneId).toLocalDate()
                val searchStart = maxOf(today, effectiveFrom)
                // 检查两周可在排除刚送达实例后继续找到同一星期的后续实例。
                (0L..14L).asSequence()
                    .map(searchStart::plusDays)
                    .filter { date ->
                        date.dayOfWeek in rule.weekdays &&
                            date.atTime(rule.startTime).atZone(zoneId).toInstant() > now
                    }
            }
        }
    }

    private fun nextCourseDates(
        rule: CourseRule,
        term: TermPeriod,
        now: Instant,
        zoneId: ZoneId
    ): Sequence<LocalDate> {
        validateLocalRange(rule.startTime, rule.endTime)
        require(rule.weekdays.isNotEmpty()) { "课程至少选择一个星期" }
        require(rule.startWeek > 0 && rule.startWeek <= rule.endWeek) { "课程周次范围无效" }
        val today = now.atZone(zoneId).toLocalDate()
        val firstTeachingWeek = AcademicCalendarRules.weekStart(term.startDate)
            .plusWeeks((rule.startWeek - 1).toLong())
        val searchStart = maxOf(today, term.startDate, firstTeachingWeek)
        if (searchStart > term.endDate) return emptySequence()

        // 检查两周可覆盖触发后续订，周次规则负责裁剪学期边界。
        return (0L..14L).asSequence()
            .map(searchStart::plusDays)
            .takeWhile { it <= term.endDate }
            .filter { date ->
                val week = AcademicCalendarRules.termWeek(term, date)
                week != null && week in rule.startWeek..rule.endWeek &&
                    date.dayOfWeek in rule.weekdays &&
                    date.atTime(rule.startTime).atZone(zoneId).toInstant() > now
            }
    }

    private fun taskCandidate(
        rule: ScheduleRule,
        date: LocalDate,
        advanceMinutes: Int,
        zoneId: ZoneId
    ): ReminderCandidate {
        val startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant()
        return ReminderCandidate(
            source = ScheduleSource.TASK,
            sourceId = rule.id,
            title = TextRules.normalizeRequiredText(rule.title, "日程名称"),
            location = null,
            startAt = startAt,
            remindAt = startAt.minusSeconds(advanceMinutes * 60L)
        )
    }

    private fun courseCandidate(
        rule: CourseRule,
        date: LocalDate,
        advanceMinutes: Int,
        zoneId: ZoneId
    ): ReminderCandidate {
        val startAt = date.atTime(rule.startTime).atZone(zoneId).toInstant()
        return ReminderCandidate(
            source = ScheduleSource.COURSE,
            sourceId = rule.id,
            title = TextRules.normalizeRequiredText(rule.courseName, "课程名称"),
            location = TextRules.normalizeRequiredText(rule.location, "上课地点"),
            startAt = startAt,
            remindAt = startAt.minusSeconds(advanceMinutes * 60L)
        )
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
