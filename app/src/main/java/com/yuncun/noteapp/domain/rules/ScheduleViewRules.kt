package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.ScheduleInstance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class EventStreamItem(
    val instance: ScheduleInstance,
    val isOngoing: Boolean,
    val isNext: Boolean
)

/** 日程页面的展示规则保持为纯函数，避免课表与事件流产生不同业务口径。 */
object ScheduleViewRules {
    fun eventStream(instances: List<ScheduleInstance>, now: Instant): List<EventStreamItem> {
        val visible = instances
            .filter { it.endAt > now }
            .sortedWith(compareBy({ it.startAt }, { it.endAt }, { it.sourceId }))
        val nextStart = visible.asSequence().filter { it.startAt > now }.map { it.startAt }.minOrNull()
        return visible.map { instance ->
            EventStreamItem(
                instance = instance,
                isOngoing = instance.startAt <= now && now < instance.endAt,
                isNext = nextStart != null && instance.startAt == nextStart
            )
        }
    }

    /** 按日期与上下午（以 12:00 为界）对事件流进行分块，供纵向布局留白展示。 */
    fun chunkEventStream(
        items: List<EventStreamItem>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<List<EventStreamItem>> {
        if (items.isEmpty()) return emptyList()
        val chunks = mutableListOf<MutableList<EventStreamItem>>()
        var currentKey: Pair<LocalDate, Boolean>? = null
        for (item in items) {
            val zonedDateTime = item.instance.startAt.atZone(zoneId)
            val key = zonedDateTime.toLocalDate() to (zonedDateTime.toLocalTime() < LocalTime.NOON)
            if (key != currentKey) {
                currentKey = key
                chunks.add(mutableListOf(item))
            } else {
                chunks.last().add(item)
            }
        }
        return chunks
    }

    /** 相邻时间不算重叠；命中的双方都返回，确保界面不会静默隐藏任一实例。 */
    fun overlappingIds(instances: List<ScheduleInstance>): Set<String> {
        val result = mutableSetOf<String>()
        instances.forEachIndexed { index, first ->
            instances.drop(index + 1).forEach { second ->
                if (first.startAt < second.endAt && second.startAt < first.endAt) {
                    result += first.sourceId
                    result += second.sourceId
                }
            }
        }
        return result
    }

    /** 候选先按更新时间排序，再对规范化后的完整名称去重。 */
    fun distinctRecentNames(values: List<Pair<String, Instant>>): List<String> =
        values.sortedByDescending { it.second }
            .map { it.first.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private val taskDateFormatter: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("MM-dd")

    /** 将当地日期格式化为月日格式（MM-DD）。 */
    fun formatTaskDate(date: LocalDate): String = date.format(taskDateFormatter)

    /**
     * 解析月日格式字符串并与指定年份拼接为完整 LocalDate。
     * 严格校验输入格式（MM-DD 或 M-D）、月份范围（01-12）以及大小月与闰年合法天数。
     */
    fun parseTaskDate(input: String, year: Int): LocalDate {
        val trimmed = input.trim()
        val parts = trimmed.split("-")
        require(
            parts.size == 2 &&
                parts[0].length in 1..2 && parts[0].all { it.isDigit() } &&
                parts[1].length in 1..2 && parts[1].all { it.isDigit() }
        ) {
            "请填写 MM-DD 格式日期（如 08-26）"
        }
        val month = parts[0].toInt()
        val day = parts[1].toInt()
        require(month in 1..12) {
            "月份超出有效范围（01-12）"
        }
        return runCatching {
            LocalDate.of(year, month, day)
        }.getOrElse {
            throw IllegalArgumentException("日期无效，请检查大小月或闰年天数")
        }
    }
}
