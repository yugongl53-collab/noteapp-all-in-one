package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.ScheduleInstance
import java.time.Instant

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
}
