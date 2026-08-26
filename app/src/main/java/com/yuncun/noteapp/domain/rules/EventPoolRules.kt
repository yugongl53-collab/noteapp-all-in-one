package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.model.WheelSegment

/** 事件池规则集中定义加权选择与转盘几何，避免动画落点和算法使用不同口径。 */
object EventPoolRules {
    fun draw(
        items: List<EventPoolCandidate>,
        nextWeightUnit: (Long) -> Long
    ): EventPoolCandidate? {
        val enabled = enabledCandidates(items)
        if (enabled.isEmpty()) return null
        val totalWeight = enabled.sumOf { it.weight.toLong() }
        val selectedUnit = nextWeightUnit(totalWeight)
        require(selectedUnit in 0 until totalWeight) { "随机权重单位超出启用候选范围" }

        var cumulativeWeight = 0L
        return enabled.first { candidate ->
            cumulativeWeight += candidate.weight
            selectedUnit < cumulativeWeight
        }
    }

    /** 按候选权重生成总计 360 度的稳定扇区，停用项完全排除。 */
    fun wheelSegments(items: List<EventPoolCandidate>): List<WheelSegment> {
        val enabled = enabledCandidates(items)
        if (enabled.isEmpty()) return emptyList()
        val totalWeight = enabled.sumOf { it.weight.toLong() }.toDouble()
        var startAngle = 0.0
        return enabled.mapIndexed { index, candidate ->
            val sweepAngle = if (index == enabled.lastIndex) {
                360.0 - startAngle
            } else {
                360.0 * candidate.weight / totalWeight
            }
            WheelSegment(
                candidate = candidate,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                percentage = 100.0 * candidate.weight / totalWeight
            ).also { startAngle += sweepAngle }
        }
    }

    /** 增加完整圈数后把中选扇区中心精确对齐正上方指针。 */
    fun targetRotation(
        currentRotation: Double,
        selectedId: String,
        segments: List<WheelSegment>,
        fullTurns: Int
    ): Double {
        require(fullTurns >= 0) { "完整旋转圈数不能为负数" }
        val selected = requireNotNull(segments.firstOrNull { it.candidate.id == selectedId }) {
            "中选项目不在当前转盘中"
        }
        val currentNormalized = positiveModulo(currentRotation, 360.0)
        val alignedRotation = positiveModulo(-selected.centerAngle, 360.0)
        val alignmentDelta = positiveModulo(alignedRotation - currentNormalized, 360.0)
        return currentRotation + fullTurns * 360.0 + alignmentDelta
    }

    private fun enabledCandidates(items: List<EventPoolCandidate>): List<EventPoolCandidate> =
        items.filter(EventPoolCandidate::isEnabled).also { enabled ->
            require(enabled.all { it.weight in 1..100 }) { "事件权重必须在 1 到 100 之间" }
        }

    private fun positiveModulo(value: Double, divisor: Double): Double =
        ((value % divisor) + divisor) % divisor
}
