package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity

/** 事件池 DAO 只允许五类可选性质，并统一名称规范化。 */
@Dao
abstract class EventPoolItemDao {
    @Transaction
    open suspend fun save(entity: EventPoolItemEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        val normalized = entity.copy(title = EntityValidation.requiredText(entity.title, "事件名称"))
        if (findById(entity.id) == null) insertInternal(normalized) else updateInternal(normalized)
        return entity.id
    }

    @Query("SELECT * FROM event_pool_items ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getAll(): List<EventPoolItemEntity>

    @Query("SELECT * FROM event_pool_items WHERE isEnabled = 1 ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getEnabled(): List<EventPoolItemEntity>

    @Query("SELECT * FROM event_pool_items WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): EventPoolItemEntity?

    @Query("DELETE FROM event_pool_items WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: EventPoolItemEntity)

    @Update
    abstract suspend fun updateInternal(entity: EventPoolItemEntity)
}
