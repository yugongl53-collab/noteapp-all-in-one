package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.domain.rules.TextRules

/** 灵感 DAO 统一规范正文、标签和时间戳后再写入。 */
@Dao
abstract class IdeaDao {
    @Transaction
    open suspend fun save(entity: IdeaEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        val normalized = entity.copy(
            content = EntityValidation.requiredText(entity.content, "灵感正文"),
            tags = TextRules.normalizeTags(entity.tags)
        )
        if (findById(entity.id) == null) insertInternal(normalized) else updateInternal(normalized)
        return entity.id
    }

    @Query("SELECT * FROM ideas WHERE deletedAt IS NULL ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getActive(): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id ASC")
    abstract suspend fun getDeleted(): List<IdeaEntity>

    @Query("SELECT * FROM ideas ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getAll(): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): IdeaEntity?

    @Query("DELETE FROM ideas WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Query("UPDATE ideas SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun moveToTrash(id: String, deletedAt: java.time.Instant): Int

    @Query("UPDATE ideas SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    abstract suspend fun restore(id: String): Int

    @Query("DELETE FROM ideas WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    abstract suspend fun deleteExpired(cutoff: java.time.Instant): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: IdeaEntity)

    @Update
    abstract suspend fun updateInternal(entity: IdeaEntity)
}
