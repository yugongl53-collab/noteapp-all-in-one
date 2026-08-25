package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.EventPoolCandidate

/** 事件池规则先过滤停用项，再由注入的等概率索引源选择真实候选。 */
object EventPoolRules {
    fun draw(
        items: List<EventPoolCandidate>,
        nextIndex: (Int) -> Int
    ): EventPoolCandidate? {
        val enabled = items.filter(EventPoolCandidate::isEnabled)
        if (enabled.isEmpty()) return null
        val index = nextIndex(enabled.size)
        require(index in enabled.indices) { "随机索引超出启用候选范围" }
        return enabled[index]
    }
}
