package com.yuncun.noteapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 普通日程只保存循环规则或单次日期，不预生成每周实例。 */
@Entity(tableName = "schedule_tasks", indices = [Index("updatedAt")])
data class ScheduleTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: EventCategory,
    val type: ScheduleType,
    val weekdays: Set<DayOfWeek>,
    val effectiveFrom: LocalDate?,
    val date: LocalDate?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isEnabled: Boolean,
    val reminderEnabled: Boolean,
    val reminderAdvanceMinutes: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)
