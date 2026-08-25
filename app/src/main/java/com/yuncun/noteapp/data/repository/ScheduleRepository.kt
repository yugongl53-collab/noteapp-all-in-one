package com.yuncun.noteapp.data.repository

import com.yuncun.noteapp.data.local.dao.AcademicTermDao
import com.yuncun.noteapp.data.local.dao.CourseScheduleDao
import com.yuncun.noteapp.data.local.dao.ScheduleTaskDao
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ScheduleSnapshot(
    val terms: List<AcademicTermEntity>,
    val tasks: List<ScheduleTaskEntity>,
    val courses: List<CourseScheduleEntity>
)

data class AcademicTermInput(
    val academicYearStart: Int,
    val season: TermSeason,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class ScheduleTaskInput(
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
    val reminderAdvanceMinutes: Int?
)

data class CourseScheduleInput(
    val termId: String,
    val courseName: String,
    val location: String,
    val weekdays: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startWeek: Int,
    val endWeek: Int,
    val reminderEnabled: Boolean,
    val reminderAdvanceMinutes: Int?
)

/** M3 只依赖三个日程实体，接口隔离 Room 细节并便于状态层测试失败分支。 */
interface ScheduleRepository {
    suspend fun load(): ScheduleSnapshot
    suspend fun saveTerm(id: String?, input: AcademicTermInput, now: Instant): String
    suspend fun deleteTerm(id: String)
    suspend fun saveTask(id: String?, input: ScheduleTaskInput, now: Instant): String
    suspend fun deleteTask(id: String)
    suspend fun saveCourse(id: String?, input: CourseScheduleInput, now: Instant): String
    suspend fun deleteCourse(id: String)
}

/** Room 仓储保留创建时间，并把学期删除约束转换为用户可理解的业务错误。 */
class RoomScheduleRepository(
    private val termDao: AcademicTermDao,
    private val taskDao: ScheduleTaskDao,
    private val courseDao: CourseScheduleDao,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : ScheduleRepository {
    override suspend fun load() = ScheduleSnapshot(
        terms = termDao.getAll(),
        tasks = taskDao.getAll(),
        courses = courseDao.getAll()
    )

    override suspend fun saveTerm(id: String?, input: AcademicTermInput, now: Instant): String {
        val existing = id?.let { requireNotNull(termDao.findById(it)) { "学期不存在" } }
        val entity = AcademicTermEntity(
            id = existing?.id ?: idFactory(),
            academicYearStart = input.academicYearStart,
            season = input.season,
            startDate = input.startDate,
            endDate = input.endDate,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return termDao.save(entity)
    }

    override suspend fun deleteTerm(id: String) {
        require(courseDao.getAll().none { it.termId == id }) { "该学期仍有课程，请先删除或调整课程" }
        check(termDao.deleteById(id) == 1) { "学期不存在" }
    }

    override suspend fun saveTask(id: String?, input: ScheduleTaskInput, now: Instant): String {
        val existing = id?.let { requireNotNull(taskDao.findById(it)) { "普通事件不存在" } }
        val entity = ScheduleTaskEntity(
            id = existing?.id ?: idFactory(),
            title = input.title,
            category = input.category,
            type = input.type,
            weekdays = input.weekdays,
            effectiveFrom = input.effectiveFrom,
            date = input.date,
            startTime = input.startTime,
            endTime = input.endTime,
            isEnabled = input.isEnabled,
            reminderEnabled = input.reminderEnabled,
            reminderAdvanceMinutes = input.reminderAdvanceMinutes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return taskDao.save(entity)
    }

    override suspend fun deleteTask(id: String) {
        check(taskDao.deleteById(id) == 1) { "普通事件不存在" }
    }

    override suspend fun saveCourse(id: String?, input: CourseScheduleInput, now: Instant): String {
        val existing = id?.let { requireNotNull(courseDao.findById(it)) { "课程不存在" } }
        val entity = CourseScheduleEntity(
            id = existing?.id ?: idFactory(),
            termId = input.termId,
            courseName = input.courseName,
            location = input.location,
            category = EventCategory.STUDY,
            weekdays = input.weekdays,
            startTime = input.startTime,
            endTime = input.endTime,
            startWeek = input.startWeek,
            endWeek = input.endWeek,
            reminderEnabled = input.reminderEnabled,
            reminderAdvanceMinutes = input.reminderAdvanceMinutes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return courseDao.save(entity)
    }

    override suspend fun deleteCourse(id: String) {
        check(courseDao.deleteById(id) == 1) { "课程不存在" }
    }
}
