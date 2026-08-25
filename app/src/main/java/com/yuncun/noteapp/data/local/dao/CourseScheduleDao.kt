package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.AcademicCalendarRules

/** 课程 DAO 同时校验学期引用、实际周数和固定学习性质。 */
@Dao
abstract class CourseScheduleDao {
    @Transaction
    open suspend fun save(entity: CourseScheduleEntity): String {
        EntityValidation.requireId(entity.id)
        EntityValidation.requireTimestamps(entity.createdAt, entity.updatedAt)
        require(entity.category == EventCategory.STUDY) { "课程事件性质必须是学习" }
        require(entity.weekdays.isNotEmpty()) { "课程至少选择一个星期" }
        EntityValidation.requireReminder(entity.reminderEnabled, entity.reminderAdvanceMinutes)
        val startTime = EntityValidation.normalizeMinute(entity.startTime)
        val endTime = EntityValidation.normalizeMinute(entity.endTime)
        EntityValidation.requireLocalRange(startTime, endTime)
        val term = requireNotNull(findTerm(entity.termId)) { "课程所属学期不存在" }
        val actualWeeks = AcademicCalendarRules.actualWeekCount(term.toPeriod())
        require(entity.startWeek > 0 && entity.startWeek <= entity.endWeek && entity.endWeek <= actualWeeks) {
            "课程周次必须位于所属学期实际周数内"
        }
        val normalized = entity.copy(
            courseName = EntityValidation.requiredText(entity.courseName, "课程名称"),
            location = EntityValidation.requiredText(entity.location, "上课地点"),
            startTime = startTime,
            endTime = endTime
        )
        if (findById(entity.id) == null) insertInternal(normalized) else updateInternal(normalized)
        return entity.id
    }

    @Query("SELECT * FROM course_schedules ORDER BY updatedAt DESC, id ASC")
    abstract suspend fun getAll(): List<CourseScheduleEntity>

    @Query("SELECT * FROM course_schedules WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: String): CourseScheduleEntity?

    @Query("SELECT * FROM academic_terms WHERE id = :id LIMIT 1")
    abstract suspend fun findTerm(id: String): AcademicTermEntity?

    @Query("DELETE FROM course_schedules WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInternal(entity: CourseScheduleEntity)

    @Update
    abstract suspend fun updateInternal(entity: CourseScheduleEntity)
}
