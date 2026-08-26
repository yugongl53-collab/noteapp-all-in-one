package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 时间记录 DAO 用半开区间查询在事务内拒绝重叠，编辑时排除自身。 */
@Dao
abstract class TimeRecordDao {
    @Transaction
    open suspend fun save(entity: TimeRecordEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        require(entity.source == "manual" || entity.source == "schedule") {
            "时间记录来源只能是 manual 或 schedule"
        }
        val startAt = entity.startAt.truncatedTo(ChronoUnit.MINUTES)
        val endAt = entity.endAt.truncatedTo(ChronoUnit.MINUTES)
        TimeRecordRules.validateRange(startAt, endAt)
        require(findOverlap(startAt, endAt, entity.id) == null) { "时间记录不能与已有记录重叠" }
        val normalized = entity.copy(
            title = EntityValidation.requiredText(entity.title, "活动名称"),
            startAt = startAt,
            endAt = endAt
        )
        if (findById(entity.id) == null) insertInternal(normalized) else updateInternal(normalized)
        return entity.id
    }

    /** 自动结算插入：若记录已存在或与既有记录重叠则静默跳过，避免中断整体结算。 */
    @Transaction
    open suspend fun insertAutoSettlement(entity: TimeRecordEntity): Boolean {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        require(entity.source == "schedule") { "自动结算来源必须是 schedule" }
        val startAt = entity.startAt.truncatedTo(ChronoUnit.MINUTES)
        val endAt = entity.endAt.truncatedTo(ChronoUnit.MINUTES)
        TimeRecordRules.validateRange(startAt, endAt)
        if (findById(entity.id) != null) return false
        if (findOverlap(startAt, endAt, entity.id) != null) return false
        val normalized = entity.copy(
            title = EntityValidation.requiredText(entity.title, "活动名称"),
            startAt = startAt,
            endAt = endAt
        )
        insertInternal(normalized)
        return true
    }

    @Query("SELECT * FROM time_records ORDER BY startAt DESC, id ASC")
    abstract suspend fun getAll(): List<TimeRecordEntity>

    @Query("SELECT * FROM time_records WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): TimeRecordEntity?

    @Query(
        """SELECT * FROM time_records
            WHERE id != :excludedId AND startAt < :endAt AND endAt > :startAt
            LIMIT 1"""
    )
    abstract suspend fun findOverlap(
        startAt: Instant,
        endAt: Instant,
        excludedId: String
    ): TimeRecordEntity?

    @Query("DELETE FROM time_records WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: TimeRecordEntity)

    @Update
    abstract suspend fun updateInternal(entity: TimeRecordEntity)
}
