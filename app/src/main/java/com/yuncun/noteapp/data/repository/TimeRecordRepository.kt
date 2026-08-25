package com.yuncun.noteapp.data.repository

import com.yuncun.noteapp.data.local.dao.TimeRecordDao
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TextRules
import java.time.Instant
import java.util.UUID

/** 时间记录状态层只通过该接口读写手动记录，统计不接入日程或番茄钟数据。 */
interface TimeRecordRepository {
    suspend fun load(): List<TimeRecordEntity>

    suspend fun save(
        id: String?,
        title: String,
        category: EventCategory,
        startAt: Instant,
        endAt: Instant,
        now: Instant
    ): String

    suspend fun delete(id: String)
}

/** Room 实现编辑时保留创建时间和可选来源关联，仅替换用户可编辑业务字段。 */
class RoomTimeRecordRepository(
    private val dao: TimeRecordDao,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : TimeRecordRepository {
    override suspend fun load(): List<TimeRecordEntity> = dao.getAll()

    override suspend fun save(
        id: String?,
        title: String,
        category: EventCategory,
        startAt: Instant,
        endAt: Instant,
        now: Instant
    ): String {
        require(category in EventCategory.selectable) { "时间记录不能选择“其他”性质" }
        val existing = id?.let { requireNotNull(dao.findById(it)) { "时间记录不存在" } }
        val entity = existing?.copy(
            title = TextRules.normalizeRequiredText(title, "活动名称"),
            category = category,
            startAt = startAt,
            endAt = endAt,
            updatedAt = now
        ) ?: TimeRecordEntity(
            id = idFactory(),
            title = TextRules.normalizeRequiredText(title, "活动名称"),
            category = category,
            startAt = startAt,
            endAt = endAt,
            source = "manual",
            relatedTaskId = null,
            relatedPoolItemId = null,
            createdAt = now,
            updatedAt = now
        )
        return dao.save(entity)
    }

    override suspend fun delete(id: String) {
        require(dao.deleteById(id) == 1) { "时间记录不存在" }
    }
}
