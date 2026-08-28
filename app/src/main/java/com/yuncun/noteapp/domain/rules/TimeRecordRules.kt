package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.EventCategory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 时间记录重叠检查使用半开区间，因此首尾相邻合法。 */
data class TimeRange(val id: String, val startAt: Instant, val endAt: Instant)

/** 统计规则只依赖记录快照，不依赖 Room 或页面状态。 */
data class TimeRecordSnapshot(
    val id: String,
    val title: String,
    val category: EventCategory,
    val startAt: Instant,
    val endAt: Instant
)

data class CategoryRankingItem(
    val rank: Int,
    val category: EventCategory,
    val minutes: Long
)

data class TitleRankingItem(
    val rank: Int,
    val title: String,
    val minutes: Long
)

/** 每日摘要保留真实自然日长度，夏令时切换日不会被固定成 24 小时。 */
data class DailyTimeSummary(
    val date: LocalDate,
    val totalMinutes: Long,
    val recordedMinutes: Long
)

data class TimeStatistics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMinutes: Long,
    val recordedMinutes: Long,
    val categoryRanking: List<CategoryRankingItem>,
    val titleRanking: List<TitleRankingItem>,
    val dailySummaries: List<DailyTimeSummary>
)

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

    /** 按当前设备时区逐日裁剪后聚合，确保跨午夜与夏令时边界只统计实际经过分钟。 */
    fun calculateStatistics(
        records: Iterable<TimeRecordSnapshot>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
        locale: Locale = Locale.getDefault()
    ): TimeStatistics {
        require(!endDate.isBefore(startDate)) { "统计结束日期不能早于开始日期" }
        val normalizedRecords = records.map { record ->
            validateRange(record.startAt, record.endAt)
            require(record.category in EventCategory.selectable) { "时间记录不能使用“其他”性质" }
            record.copy(title = TextRules.normalizeRequiredText(record.title, "活动名称"))
        }
        val categoryMinutes = mutableMapOf<EventCategory, Long>()
        val titleMinutes = mutableMapOf<String, Long>()
        val dailySummaries = generateSequence(startDate) { date ->
            date.plusDays(1).takeIf { !it.isAfter(endDate) }
        }.map { date ->
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val dayMinutes = Duration.between(dayStart, dayEnd).toMinutes()
            var recordedMinutes = 0L
            normalizedRecords.forEach { record ->
                val clippedStart = maxOf(record.startAt, dayStart)
                val clippedEnd = minOf(record.endAt, dayEnd)
                if (clippedStart < clippedEnd) {
                    val minutes = Duration.between(clippedStart, clippedEnd).toMinutes()
                    if (minutes > 0) {
                        recordedMinutes += minutes
                        categoryMinutes[record.category] = categoryMinutes.getOrDefault(record.category, 0) + minutes
                        titleMinutes[record.title] = titleMinutes.getOrDefault(record.title, 0) + minutes
                    }
                }
            }
            DailyTimeSummary(
                date = date,
                totalMinutes = dayMinutes,
                recordedMinutes = recordedMinutes
            )
        }.toList()

        val categoryRanking = categoryMinutes.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<EventCategory, Long>> { it.value }
                .thenBy { EventCategory.selectable.indexOf(it.key) })
            .mapIndexed { index, entry -> CategoryRankingItem(index + 1, entry.key, entry.value) }
        val collator = java.text.Collator.getInstance(locale)
        val titleRanking = titleMinutes.entries
            .filter { it.value > 0 }
            .sortedWith { first, second ->
                val durationOrder = second.value.compareTo(first.value)
                if (durationOrder != 0) durationOrder else collator.compare(first.key, second.key)
            }
            .mapIndexed { index, entry -> TitleRankingItem(index + 1, entry.key, entry.value) }
        val totalMinutes = dailySummaries.sumOf { it.totalMinutes }
        val recordedMinutes = dailySummaries.sumOf { it.recordedMinutes }
        return TimeStatistics(
            startDate = startDate,
            endDate = endDate,
            totalMinutes = totalMinutes,
            recordedMinutes = recordedMinutes,
            categoryRanking = categoryRanking,
            titleRanking = titleRanking,
            dailySummaries = dailySummaries
        )
    }
}
