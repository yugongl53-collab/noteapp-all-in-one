package com.yuncun.noteapp.data.repository

import com.yuncun.noteapp.data.local.dao.EventPoolItemDao
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TextRules
import java.time.Instant
import java.util.UUID

/** 事件池页面使用的最小持久化接口，状态层无需感知 Room 写入细节。 */
interface EventPoolRepository {
    suspend fun load(): List<EventPoolItemEntity>
    suspend fun save(
        id: String?,
        title: String,
        category: EventCategory,
        isEnabled: Boolean,
        weight: Int,
        now: Instant
    ): String
    suspend fun setEnabled(id: String, enabled: Boolean, now: Instant)
    suspend fun delete(id: String)
}

/** Room 实现保留创建时间，并让编辑、启停与删除都验证目标真实存在。 */
class RoomEventPoolRepository(
    private val dao: EventPoolItemDao,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : EventPoolRepository {
    override suspend fun load(): List<EventPoolItemEntity> = dao.getAll()

    override suspend fun save(
        id: String?,
        title: String,
        category: EventCategory,
        isEnabled: Boolean,
        weight: Int,
        now: Instant
    ): String {
        require(category in EventCategory.selectable) { "事件池不能选择“其他”性质" }
        require(weight in 1..100) { "事件权重必须在 1 到 100 之间" }
        val existing = id?.let { requireNotNull(dao.findById(it)) { "事件池项目不存在" } }
        return dao.save(
            EventPoolItemEntity(
                id = existing?.id ?: idFactory(),
                title = TextRules.normalizeRequiredText(title, "事件名称"),
                category = category,
                isEnabled = isEnabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                weight = weight
            )
        )
    }

    override suspend fun setEnabled(id: String, enabled: Boolean, now: Instant) {
        val existing = requireNotNull(dao.findById(id)) { "事件池项目不存在" }
        dao.save(existing.copy(isEnabled = enabled, updatedAt = now))
    }

    override suspend fun delete(id: String) {
        require(dao.deleteById(id) == 1) { "事件池项目不存在" }
    }
}
