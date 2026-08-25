package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import java.time.LocalDate

/** 学期 DAO 在同一事务中检查闭区间重叠，再执行插入或更新。 */
@Dao
abstract class AcademicTermDao {
    @Transaction
    open suspend fun save(entity: AcademicTermEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        require(entity.academicYearStart in 1000..9999) { "学年起始年份必须是四位整数" }
        require(entity.endDate >= entity.startDate) { "学期结束日期不能早于开始日期" }
        require(findOverlap(entity.startDate, entity.endDate, entity.id) == null) { "学期日期范围不能重叠" }
        if (findById(entity.id) == null) insertInternal(entity) else updateInternal(entity)
        return entity.id
    }

    @Query("SELECT * FROM academic_terms ORDER BY startDate ASC, id ASC")
    abstract suspend fun getAll(): List<AcademicTermEntity>

    @Query("SELECT * FROM academic_terms WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): AcademicTermEntity?

    @Query(
        """SELECT * FROM academic_terms
            WHERE id != :excludedId AND startDate <= :endDate AND endDate >= :startDate
            LIMIT 1"""
    )
    abstract suspend fun findOverlap(
        startDate: LocalDate,
        endDate: LocalDate,
        excludedId: String
    ): AcademicTermEntity?

    @Query("DELETE FROM academic_terms WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: AcademicTermEntity)

    @Update
    abstract suspend fun updateInternal(entity: AcademicTermEntity)
}
