package com.yuncun.noteapp.data.repository

import com.yuncun.noteapp.data.local.dao.IdeaDao
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.domain.rules.TextRules
import java.time.Instant
import java.util.UUID

data class IdeaSnapshot(
    val activeIdeas: List<IdeaEntity>,
    val recycledIdeas: List<IdeaEntity>
)

/** 灵感页面依赖的最小持久化接口，便于状态层覆盖数据库失败场景。 */
interface IdeaRepository {
    suspend fun refresh(now: Instant): IdeaSnapshot
    suspend fun create(content: String, tags: List<String>, now: Instant): IdeaEntity
    suspend fun update(id: String, content: String, tags: List<String>, now: Instant)
    suspend fun moveToTrash(id: String, now: Instant)
    suspend fun restore(id: String)
    suspend fun permanentlyDelete(id: String)
}

/** Room 实现负责生成本地标识，并在每次读取前清理恰好到期的回收站数据。 */
class RoomIdeaRepository(
    private val dao: IdeaDao,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : IdeaRepository {
    override suspend fun refresh(now: Instant): IdeaSnapshot {
        dao.deleteExpired(now.minusSeconds(THIRTY_DAYS_SECONDS))
        return IdeaSnapshot(
            activeIdeas = dao.getActive(),
            recycledIdeas = dao.getDeleted()
        )
    }

    override suspend fun create(content: String, tags: List<String>, now: Instant): IdeaEntity {
        val entity = IdeaEntity(
            id = idFactory(),
            content = TextRules.normalizeRequiredText(content, "灵感正文"),
            tags = TextRules.normalizeTags(tags),
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        dao.save(entity)
        return entity
    }

    override suspend fun update(id: String, content: String, tags: List<String>, now: Instant) {
        val existing = requireNotNull(dao.findById(id)) { "灵感不存在" }
        require(existing.deletedAt == null) { "回收站中的灵感不能编辑" }
        dao.save(
            existing.copy(
                content = TextRules.normalizeRequiredText(content, "灵感正文"),
                tags = TextRules.normalizeTags(tags),
                updatedAt = now
            )
        )
    }

    override suspend fun moveToTrash(id: String, now: Instant) {
        check(dao.moveToTrash(id, now) == 1) { "灵感不存在或已在回收站" }
    }

    override suspend fun restore(id: String) {
        check(dao.restore(id) == 1) { "灵感不存在或不在回收站" }
    }

    override suspend fun permanentlyDelete(id: String) {
        val existing = requireNotNull(dao.findById(id)) { "灵感不存在" }
        require(existing.deletedAt != null) { "只能永久删除回收站中的灵感" }
        check(dao.deleteById(id) == 1) { "永久删除失败" }
    }

    private companion object {
        const val THIRTY_DAYS_SECONDS = 30L * 24 * 60 * 60
    }
}
