package com.yuncun.noteapp.domain.rules

import java.time.Instant

/** 时间记录重叠检查使用半开区间，因此首尾相邻合法。 */
data class TimeRange(val id: String, val startAt: Instant, val endAt: Instant)

object TimeRecordRules {
    fun validateRange(startAt: Instant, endAt: Instant) {
        require(endAt > startAt) { "结束时间必须晚于开始时间" }
    }

    fun hasOverlap(
        startAt: Instant,
        endAt: Instant,
        existing: Iterable<TimeRange>,
        excludedId: String? = null
    ): Boolean {
        validateRange(startAt, endAt)
        return existing.any { range ->
            range.id != excludedId && startAt < range.endAt && endAt > range.startAt
        }
    }
}
