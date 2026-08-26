package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 待自动结算的日程候选实例，包含防重键与确定性记录主键。 */
data class SettlementCandidate(
    val instanceKey: String,
    val deterministicId: String,
    val source: ScheduleSource,
    val sourceId: String,
    val title: String,
    val category: EventCategory,
    val startAt: Instant,
    val endAt: Instant,
    val relatedTaskId: String?
)

/** 纯 Kotlin 自动结算规则，负责筛选已结束但未结算的日程实例，并计算下一次结算时刻。 */
object ScheduleAutoSettlementRules {

    /** 生成日程实例的唯一结算标识。 */
    fun buildInstanceKey(source: ScheduleSource, sourceId: String, startAt: Instant, endAt: Instant): String =
        "${source.name}:$sourceId:${startAt.toEpochMilli()}:${endAt.toEpochMilli()}"

    /** 根据结算标识生成确定性的 UUID，保证重试与防重时的主键唯一且稳定。 */
    fun buildDeterministicId(instanceKey: String): String =
        UUID.nameUUIDFromBytes(instanceKey.toByteArray(Charsets.UTF_8)).toString()

    /** 找出所有已到达结束时间且尚未结算的日程实例。 */
    fun unsettledEndedCandidates(
        schedules: List<ScheduleRule>,
        courses: List<CourseRule>,
        terms: List<TermPeriod>,
        now: Instant,
        zoneId: ZoneId,
        settledKeys: Set<String>
    ): List<SettlementCandidate> {
        val today = now.atZone(zoneId).toLocalDate()
        val termById = terms.associateBy { it.id }

        val taskCandidates = schedules.asSequence()
            .filter { it.isEnabled }
            .flatMap { schedule ->
                when (schedule.type) {
                    ScheduleType.ONE_OFF -> {
                        val date = schedule.date ?: return@flatMap emptySequence()
                        val startAt = date.atTime(schedule.startTime).atZone(zoneId).toInstant()
                        val endAt = date.atTime(schedule.endTime).atZone(zoneId).toInstant()
                        if (endAt <= now) {
                            sequenceOf(toCandidate(schedule, startAt, endAt))
                        } else {
                            emptySequence()
                        }
                    }
                    ScheduleType.WEEKLY -> {
                        val effectiveFrom = schedule.effectiveFrom ?: return@flatMap emptySequence()
                        if (schedule.weekdays.isEmpty() || effectiveFrom.isAfter(today)) return@flatMap emptySequence()

                        generateSequence(effectiveFrom) { it.plusDays(1) }
                            .takeWhile { !it.isAfter(today) }
                            .filter { it.dayOfWeek in schedule.weekdays }
                            .map { date ->
                                val startAt = date.atTime(schedule.startTime).atZone(zoneId).toInstant()
                                val endAt = date.atTime(schedule.endTime).atZone(zoneId).toInstant()
                                toCandidate(schedule, startAt, endAt)
                            }
                            .filter { it.endAt <= now }
                    }
                }
            }

        val courseCandidates = courses.asSequence()
            .flatMap { course ->
                val term = termById[course.termId] ?: return@flatMap emptySequence()
                if (course.weekdays.isEmpty() || course.startWeek > course.endWeek) return@flatMap emptySequence()
                val limitDate = if (today.isBefore(term.endDate)) today else term.endDate
                if (term.startDate.isAfter(limitDate)) return@flatMap emptySequence()

                generateSequence(term.startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(limitDate) }
                    .filter { date ->
                        val week = AcademicCalendarRules.termWeek(term, date) ?: return@filter false
                        week in course.startWeek..course.endWeek && date.dayOfWeek in course.weekdays
                    }
                    .map { date ->
                        val startAt = date.atTime(course.startTime).atZone(zoneId).toInstant()
                        val endAt = date.atTime(course.endTime).atZone(zoneId).toInstant()
                        val instanceKey = buildInstanceKey(ScheduleSource.COURSE, course.id, startAt, endAt)
                        SettlementCandidate(
                            instanceKey = instanceKey,
                            deterministicId = buildDeterministicId(instanceKey),
                            source = ScheduleSource.COURSE,
                            sourceId = course.id,
                            title = TextRules.normalizeRequiredText(course.courseName, "课程名称"),
                            category = EventCategory.STUDY,
                            startAt = startAt,
                            endAt = endAt,
                            relatedTaskId = null
                        )
                    }
                    .filter { it.endAt <= now }
            }

        return (taskCandidates + courseCandidates)
            .filter { it.instanceKey !in settledKeys }
            .sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.instanceKey }))
            .toList()
    }

    /** 计算未来最早结束的日程实例结束时刻，用于精确闹钟调度。 */
    fun nextEndingInstant(
        schedules: List<ScheduleRule>,
        courses: List<CourseRule>,
        terms: List<TermPeriod>,
        now: Instant,
        zoneId: ZoneId,
        lookAheadDays: Long = 30
    ): Instant? {
        val today = now.atZone(zoneId).toLocalDate()
        val maxDate = today.plusDays(lookAheadDays)
        val termById = terms.associateBy { it.id }

        val taskEndTimes = schedules.asSequence()
            .filter { it.isEnabled }
            .flatMap { schedule ->
                when (schedule.type) {
                    ScheduleType.ONE_OFF -> {
                        val date = schedule.date ?: return@flatMap emptySequence()
                        if (date.isBefore(today) || date.isAfter(maxDate)) return@flatMap emptySequence()
                        val endAt = date.atTime(schedule.endTime).atZone(zoneId).toInstant()
                        if (endAt > now) sequenceOf(endAt) else emptySequence()
                    }
                    ScheduleType.WEEKLY -> {
                        val effectiveFrom = schedule.effectiveFrom ?: return@flatMap emptySequence()
                        val startDate = if (effectiveFrom.isAfter(today)) effectiveFrom else today
                        if (startDate.isAfter(maxDate) || schedule.weekdays.isEmpty()) return@flatMap emptySequence()

                        generateSequence(startDate) { it.plusDays(1) }
                            .takeWhile { !it.isAfter(maxDate) }
                            .filter { it.dayOfWeek in schedule.weekdays }
                            .map { date -> date.atTime(schedule.endTime).atZone(zoneId).toInstant() }
                            .filter { it > now }
                    }
                }
            }

        val courseEndTimes = courses.asSequence()
            .flatMap { course ->
                val term = termById[course.termId] ?: return@flatMap emptySequence()
                if (course.weekdays.isEmpty() || course.startWeek > course.endWeek) return@flatMap emptySequence()
                val startDate = if (term.startDate.isAfter(today)) term.startDate else today
                val endDate = if (term.endDate.isBefore(maxDate)) term.endDate else maxDate
                if (startDate.isAfter(endDate)) return@flatMap emptySequence()

                generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(endDate) }
                    .filter { date ->
                        val week = AcademicCalendarRules.termWeek(term, date) ?: return@filter false
                        week in course.startWeek..course.endWeek && date.dayOfWeek in course.weekdays
                    }
                    .map { date -> date.atTime(course.endTime).atZone(zoneId).toInstant() }
                    .filter { it > now }
            }

        return (taskEndTimes + courseEndTimes).minOrNull()
    }

    private fun toCandidate(schedule: ScheduleRule, startAt: Instant, endAt: Instant): SettlementCandidate {
        val instanceKey = buildInstanceKey(ScheduleSource.TASK, schedule.id, startAt, endAt)
        return SettlementCandidate(
            instanceKey = instanceKey,
            deterministicId = buildDeterministicId(instanceKey),
            source = ScheduleSource.TASK,
            sourceId = schedule.id,
            title = TextRules.normalizeRequiredText(schedule.title, "日程名称"),
            category = schedule.category,
            startAt = startAt,
            endAt = endAt,
            relatedTaskId = schedule.id
        )
    }
}
