package com.yuncun.noteapp.data.backup

import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.AppSettings
import kotlinx.serialization.Serializable

/** 已通过格式与业务校验的完整业务快照，活动番茄钟和系统权限不属于该范围。 */
data class BackupSnapshot(
    val ideas: List<IdeaEntity>,
    val scheduleTasks: List<ScheduleTaskEntity>,
    val academicTerms: List<AcademicTermEntity>,
    val courseSchedules: List<CourseScheduleEntity>,
    val eventPoolItems: List<EventPoolItemEntity>,
    val timeRecords: List<TimeRecordEntity>,
    val appSettings: AppSettings
)

/** 二次确认只展示数据量，不向 Compose 状态暴露可被意外修改的完整导入对象。 */
data class BackupSummary(
    val fileName: String,
    val ideaCount: Int,
    val scheduleCount: Int,
    val termCount: Int,
    val courseCount: Int,
    val poolItemCount: Int,
    val timeRecordCount: Int
)

internal fun BackupSnapshot.toSummary(fileName: String) = BackupSummary(
    fileName = fileName,
    ideaCount = ideas.size,
    scheduleCount = scheduleTasks.size,
    termCount = academicTerms.size,
    courseCount = courseSchedules.size,
    poolItemCount = eventPoolItems.size,
    timeRecordCount = timeRecords.size
)

/** formatVersion 1 的顶层结构；所有字段必填，未知字段由严格 Json 配置拒绝。 */
@Serializable
internal data class BackupDocumentDto(
    val formatVersion: Int,
    val exportedAt: String,
    val data: BackupDataDto
)

@Serializable
internal data class BackupDataDto(
    val ideas: List<IdeaDto>,
    val scheduleTasks: List<ScheduleTaskDto>,
    val academicTerms: List<AcademicTermDto>,
    val courseSchedules: List<CourseScheduleDto>,
    val eventPoolItems: List<EventPoolItemDto>,
    val timeRecords: List<TimeRecordDto>,
    val appSettings: AppSettingsDto
)

@Serializable
internal data class IdeaDto(
    val id: String,
    val content: String,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?
)

@Serializable
internal data class ScheduleTaskDto(
    val id: String,
    val title: String,
    val category: String,
    val type: String,
    val weekdays: List<String>,
    val effectiveFrom: String?,
    val date: String?,
    val startTime: String,
    val endTime: String,
    val isEnabled: Boolean,
    val reminderEnabled: Boolean,
    val reminderAdvanceMinutes: Int?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class AcademicTermDto(
    val id: String,
    val academicYearStart: Int,
    val season: String,
    val startDate: String,
    val endDate: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class CourseScheduleDto(
    val id: String,
    val termId: String,
    val courseName: String,
    val location: String,
    val category: String,
    val weekdays: List<String>,
    val startTime: String,
    val endTime: String,
    val startWeek: Int,
    val endWeek: Int,
    val reminderEnabled: Boolean,
    val reminderAdvanceMinutes: Int?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class EventPoolItemDto(
    val id: String,
    val title: String,
    val category: String,
    val isEnabled: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class TimeRecordDto(
    val id: String,
    val title: String,
    val category: String,
    val startAt: String,
    val endAt: String,
    val source: String,
    val relatedTaskId: String?,
    val relatedPoolItemId: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class AppSettingsDto(
    val lastFocusMinutes: Int,
    val lastRestMinutes: Int
)
