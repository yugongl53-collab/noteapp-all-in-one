package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.domain.model.ScheduleType

/** 普通日程 DAO 强制 weekly 与 one_off 的条件字段互斥。 */
@Dao
abstract class ScheduleTaskDao {
    @Transaction
    open suspend fun save(entity: ScheduleTaskEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        EntityValidation.requireReminder(entity.reminderEnabled, entity.reminderAdvanceMinutes)
        val startTime = EntityValidation.normalizeMinute(entity.startTime)
        val endTime = EntityValidation.normalizeMinute(entity.endTime)
        EntityValidation.requireLocalRange(startTime, endTime)
        when (entity.type) {
            ScheduleType.WEEKLY -> {
                require(entity.weekdays.isNotEmpty()) { "循环日程至少选择一个星期" }
                requireNotNull(entity.effectiveFrom) { "循环日程缺少生效日期" }
                require(entity.date == null) { "循环日程不能保存单次日期" }
            }
            ScheduleType.ONE_OFF -> {
                requireNotNull(entity.date) { "单次日程缺少日期" }
                require(entity.effectiveFrom == null && entity.weekdays.isEmpty()) {
                    "单次日程不能保存循环字段"
                }
            }
        }
        val normalized = entity.copy(
            title = EntityValidation.requiredText(entity.title, "日程名称"),
            startTime = startTime,
            endTime = endTime
        )
        if (findById(entity.id) == null) insertInternal(normalized) else updateInternal(normalized)
        return entity.id
    }

    @Query("SELECT * FROM schedule_tasks ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getAll(): List<ScheduleTaskEntity>

    @Query("SELECT * FROM schedule_tasks WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): ScheduleTaskEntity?

    @Query("DELETE FROM schedule_tasks WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: ScheduleTaskEntity)

    @Update
    abstract suspend fun updateInternal(entity: ScheduleTaskEntity)
}
