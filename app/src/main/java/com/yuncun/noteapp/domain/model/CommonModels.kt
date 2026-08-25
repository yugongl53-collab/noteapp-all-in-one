package com.yuncun.noteapp.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 固定事件性质；OTHER 仅用于统计派生，不能写入业务实体。 */
enum class EventCategory(val stableId: String, val displayName: String) {
    WORK("work", "工作"),
    STUDY("study", "学习"),
    HIGH_QUALITY_ENTERTAINMENT("high_quality_entertainment", "高质量娱乐"),
    LOW_QUALITY_ENTERTAINMENT("low_quality_entertainment", "低质量娱乐"),
    SOCIAL("social", "社交"),
    OTHER("other", "其他");

    companion object {
        val selectable = entries.filterNot { it == OTHER }

        /** 将持久化稳定标识还原为枚举，未知值必须显式失败。 */
        fun fromStableId(value: String): EventCategory =
            entries.firstOrNull { it.stableId == value }
                ?: throw IllegalArgumentException("未知事件性质：$value")
    }
}

/** 学期季节决定标准名称和假期边界含义。 */
enum class TermSeason(val stableId: String, private val chineseName: String) {
    FALL("fall", "秋季学期"),
    SPRING("spring", "春季学期");

    fun displayName(academicYearStart: Int): String =
        "$academicYearStart-${academicYearStart + 1}$chineseName"

    companion object {
        fun fromStableId(value: String): TermSeason =
            entries.firstOrNull { it.stableId == value }
                ?: throw IllegalArgumentException("未知学期季节：$value")
    }
}

enum class ScheduleType(val stableId: String) {
    WEEKLY("weekly"),
    ONE_OFF("one_off");

    companion object {
        fun fromStableId(value: String): ScheduleType =
            entries.firstOrNull { it.stableId == value }
                ?: throw IllegalArgumentException("未知日程类型：$value")
    }
}

/** 不含 Room 细节的学期日期范围，供纯 Kotlin 日历规则使用。 */
data class TermPeriod(
    val id: String,
    val academicYearStart: Int,
    val season: TermSeason,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    val displayName: String get() = season.displayName(academicYearStart)
}

/** 普通日程展开所需的最小领域快照。 */
data class ScheduleRule(
    val id: String,
    val title: String,
    val category: EventCategory,
    val type: ScheduleType,
    val weekdays: Set<DayOfWeek>,
    val effectiveFrom: LocalDate?,
    val date: LocalDate?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isEnabled: Boolean
)

/** 课程展开所需的最小领域快照；课程性质固定为学习。 */
data class CourseRule(
    val id: String,
    val termId: String,
    val courseName: String,
    val location: String,
    val weekdays: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startWeek: Int,
    val endWeek: Int
)

enum class ScheduleSource {
    TASK,
    COURSE
}

/** 日程提醒配置独立于展开实例，关闭提醒时提前分钟数允许为空。 */
data class ReminderConfiguration(
    val source: ScheduleSource,
    val sourceId: String,
    val enabled: Boolean,
    val advanceMinutes: Int?
)

/** 可交给 Android 调度层的下一次提醒，标识同时绑定来源与实例开始时刻。 */
data class ReminderCandidate(
    val source: ScheduleSource,
    val sourceId: String,
    val title: String,
    val location: String?,
    val startAt: Instant,
    val remindAt: Instant
) {
    val id: String = "${source.name}:${startAt.toEpochMilli()}:$sourceId"

    fun shouldNotifyImmediately(now: Instant): Boolean = remindAt <= now && now < startAt
}

/** 展开后的具体绝对时间实例，排序键由开始、结束和稳定标识组成。 */
data class ScheduleInstance(
    val sourceId: String,
    val source: ScheduleSource,
    val title: String,
    val category: EventCategory,
    val startAt: Instant,
    val endAt: Instant,
    val location: String? = null
)
