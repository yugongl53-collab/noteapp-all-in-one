package com.yuncun.noteapp.data.local

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TextRules
import java.time.Instant
import java.time.LocalTime

/** DAO 共用的存储边界校验，所有保存入口必须先通过这些不变量。 */
internal object EntityValidation {
    fun requireId(id: String) {
        require(id.isNotBlank()) { "稳定标识不能为空" }
    }

    fun requireTimestamps(createdAt: Instant, updatedAt: Instant) {
        require(updatedAt >= createdAt) { "最后更新时间不能早于创建时间" }
    }

    fun requireSelectableCategory(category: EventCategory) {
        require(category in EventCategory.selectable) { "业务实体不能保存其他事件性质" }
    }

    fun normalizeMinute(time: LocalTime): LocalTime = time.withSecond(0).withNano(0)

    fun requireLocalRange(startTime: LocalTime, endTime: LocalTime) {
        require(endTime > startTime) { "结束时刻必须晚于开始时刻，MVP 不允许跨午夜" }
    }

    fun requireReminder(enabled: Boolean, advanceMinutes: Int?) {
        if (enabled) requireNotNull(advanceMinutes) { "启用提醒时必须设置提前分钟数" }
        require(advanceMinutes == null || advanceMinutes >= 0) { "提醒提前分钟数不能为负数" }
    }

    fun requiredText(value: String, fieldName: String): String =
        TextRules.normalizeRequiredText(value, fieldName)
}
