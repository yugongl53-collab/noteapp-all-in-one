package com.yuncun.noteapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuncun.noteapp.domain.model.EventCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** 课程通过受限外键归属学期，删除学期前必须先处理课程。 */
@Entity(
    tableName = "course_schedules",
    foreignKeys = [
        ForeignKey(
            entity = AcademicTermEntity::class,
            parentColumns = ["id"],
            childColumns = ["termId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("termId"), Index("updatedAt")]
)
data class CourseScheduleEntity(
    @PrimaryKey val id: String,
    val termId: String,
    val courseName: String,
    val location: String,
    val category: EventCategory,
    val weekdays: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startWeek: Int,
    val endWeek: Int,
    val reminderEnabled: Boolean,
    val reminderAdvanceMinutes: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)
