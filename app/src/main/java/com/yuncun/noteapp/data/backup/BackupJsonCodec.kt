package com.yuncun.noteapp.data.backup

import com.yuncun.noteapp.data.local.EntityValidation
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.AppThemeMode
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.AcademicCalendarRules
import com.yuncun.noteapp.domain.rules.TextRules
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** formatVersion 1 的唯一 JSON 编解码入口，解析后在接触数据库前验证完整快照。 */
class BackupJsonCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: BackupSnapshot, exportedAt: Instant): String {
        validateSnapshot(snapshot)
        return json.encodeToString(
            BackupDocumentDto.serializer(),
            BackupDocumentDto(FORMAT_VERSION, exportedAt.toString(), snapshot.toDto())
        )
    }

    fun decodeAndValidate(content: String): BackupSnapshot {
        val document = try {
            json.decodeFromString(BackupDocumentDto.serializer(), content)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("备份 JSON 结构或字段类型无效", error)
        }
        require(document.formatVersion == FORMAT_VERSION) {
            "不支持的备份格式版本：${document.formatVersion}"
        }
        parseInstant(document.exportedAt, "导出时间")
        return document.data.toSnapshot().also(::validateSnapshot)
    }

    /** 全量校验同时覆盖单实体约束、集合唯一性、跨实体引用与区间冲突。 */
    fun validateSnapshot(snapshot: BackupSnapshot) {
        requireUniqueIds("灵感", snapshot.ideas.map { it.id })
        requireUniqueIds("普通事件", snapshot.scheduleTasks.map { it.id })
        requireUniqueIds("学期", snapshot.academicTerms.map { it.id })
        requireUniqueIds("课程", snapshot.courseSchedules.map { it.id })
        requireUniqueIds("事件池项目", snapshot.eventPoolItems.map { it.id })
        requireUniqueIds("时间记录", snapshot.timeRecords.map { it.id })

        snapshot.ideas.forEach(::validateIdea)
        snapshot.scheduleTasks.forEach(::validateTask)
        validateTerms(snapshot.academicTerms)
        validateCourses(snapshot.courseSchedules, snapshot.academicTerms)
        snapshot.eventPoolItems.forEach(::validatePoolItem)
        validateTimeRecords(snapshot.timeRecords, snapshot.scheduleTasks, snapshot.eventPoolItems)
        require(snapshot.appSettings.lastFocusMinutes in 1..180) { "专注时长必须在 1 到 180 分钟之间" }
        require(snapshot.appSettings.lastRestMinutes in 1..60) { "休息时长必须在 1 到 60 分钟之间" }
    }

    private fun validateIdea(entity: IdeaEntity) {
        validateIdentityAndTimestamps(entity.id, entity.createdAt, entity.updatedAt)
        require(entity.content == EntityValidation.requiredText(entity.content, "灵感正文")) { "灵感正文必须已规范化" }
        require(entity.tags == TextRules.normalizeTags(entity.tags)) { "灵感标签不能包含空白或重复值" }
        require(entity.deletedAt == null || entity.deletedAt >= entity.createdAt) { "灵感删除时间不能早于创建时间" }
    }

    private fun validateTask(entity: ScheduleTaskEntity) {
        validateIdentityAndTimestamps(entity.id, entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        require(entity.title == EntityValidation.requiredText(entity.title, "日程名称")) { "日程名称必须已规范化" }
        validateLocalRange(entity.startTime, entity.endTime)
        EntityValidation.requireReminder(entity.reminderEnabled, entity.reminderAdvanceMinutes)
        when (entity.type) {
            ScheduleType.WEEKLY -> {
                require(entity.weekdays.isNotEmpty()) { "循环日程至少选择一个星期" }
                requireNotNull(entity.effectiveFrom) { "循环日程缺少生效日期" }
                require(entity.date == null) { "循环日程不能保存单次日期" }
            }
            ScheduleType.ONE_OFF -> {
                requireNotNull(entity.date) { "单次日程缺少日期" }
                require(entity.effectiveFrom == null && entity.weekdays.isEmpty()) { "单次日程不能保存循环字段" }
            }
        }
    }

    private fun validateTerms(terms: List<AcademicTermEntity>) {
        terms.forEach { term ->
            validateIdentityAndTimestamps(term.id, term.createdAt, term.updatedAt)
            require(term.academicYearStart in 1000..9999) { "学年起始年份必须是四位整数" }
            require(term.endDate >= term.startDate) { "学期结束日期不能早于开始日期" }
        }
        require(terms.distinctBy { it.academicYearStart to it.season }.size == terms.size) { "学年与季节不能重复" }
        val sorted = terms.sortedBy { it.startDate }
        require(sorted.zipWithNext().none { (first, second) -> first.endDate >= second.startDate }) {
            "学期日期范围不能重叠"
        }
    }

    private fun validateCourses(courses: List<CourseScheduleEntity>, terms: List<AcademicTermEntity>) {
        val termsById = terms.associateBy { it.id }
        courses.forEach { course ->
            validateIdentityAndTimestamps(course.id, course.createdAt, course.updatedAt)
            require(course.category == EventCategory.STUDY) { "课程事件性质必须是学习" }
            require(course.courseName == EntityValidation.requiredText(course.courseName, "课程名称")) { "课程名称必须已规范化" }
            require(course.location == EntityValidation.requiredText(course.location, "上课地点")) { "上课地点必须已规范化" }
            require(course.weekdays.isNotEmpty()) { "课程至少选择一个星期" }
            validateLocalRange(course.startTime, course.endTime)
            EntityValidation.requireReminder(course.reminderEnabled, course.reminderAdvanceMinutes)
            val term = requireNotNull(termsById[course.termId]) { "课程所属学期不存在：${course.termId}" }
            val actualWeeks = AcademicCalendarRules.actualWeekCount(term.toPeriod())
            require(course.startWeek > 0 && course.startWeek <= course.endWeek && course.endWeek <= actualWeeks) {
                "课程周次必须位于所属学期实际周数内"
            }
        }
    }

    private fun validatePoolItem(entity: EventPoolItemEntity) {
        validateIdentityAndTimestamps(entity.id, entity.createdAt, entity.updatedAt)
        EntityValidation.requireSelectableCategory(entity.category)
        require(entity.title == EntityValidation.requiredText(entity.title, "事件名称")) { "事件名称必须已规范化" }
        require(entity.weight in 1..100) { "事件权重必须在 1 到 100 之间" }
    }

    private fun validateTimeRecords(
        records: List<TimeRecordEntity>,
        tasks: List<ScheduleTaskEntity>,
        poolItems: List<EventPoolItemEntity>
    ) {
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
        val poolItemIds = poolItems.mapTo(mutableSetOf()) { it.id }
        val sorted = records.sortedBy { it.startAt }
        sorted.forEach { record ->
            validateIdentityAndTimestamps(record.id, record.createdAt, record.updatedAt)
            EntityValidation.requireSelectableCategory(record.category)
            require(record.title == EntityValidation.requiredText(record.title, "活动名称")) { "活动名称必须已规范化" }
            require(record.source == "manual" || record.source == "schedule") { "时间记录来源只能是 manual 或 schedule" }
            require(record.startAt == record.startAt.truncatedTo(ChronoUnit.MINUTES) &&
                record.endAt == record.endAt.truncatedTo(ChronoUnit.MINUTES)) { "时间记录必须精确到分钟" }
            TimeRecordRules.validateRange(record.startAt, record.endAt)
            require(record.relatedTaskId == null || record.relatedTaskId in taskIds) { "时间记录引用的普通事件不存在" }
            require(record.relatedPoolItemId == null || record.relatedPoolItemId in poolItemIds) { "时间记录引用的事件池项目不存在" }
        }
        require(sorted.zipWithNext().none { (first, second) -> first.endAt > second.startAt }) {
            "时间记录不能互相重叠"
        }
    }

    private fun validateIdentityAndTimestamps(id: String, createdAt: Instant, updatedAt: Instant) {
        EntityValidation.requireId(id)
        EntityValidation.requireTimestamps(createdAt, updatedAt)
    }

    private fun validateLocalRange(start: LocalTime, end: LocalTime) {
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0) { "日程时刻必须精确到分钟" }
        EntityValidation.requireLocalRange(start, end)
    }

    private fun requireUniqueIds(entityName: String, ids: List<String>) {
        require(ids.distinct().size == ids.size) { "$entityName 存在重复标识" }
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}

private fun BackupSnapshot.toDto() = BackupDataDto(
    ideas = ideas.map { IdeaDto(it.id, it.content, it.tags, it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString()) },
    scheduleTasks = scheduleTasks.map {
        ScheduleTaskDto(
            it.id, it.title, it.category.stableId, it.type.stableId,
            it.weekdays.sortedBy(DayOfWeek::getValue).map(::dayOfWeekId),
            it.effectiveFrom?.toString(), it.date?.toString(), it.startTime.toString(), it.endTime.toString(),
            it.isEnabled, it.reminderEnabled, it.reminderAdvanceMinutes, it.createdAt.toString(), it.updatedAt.toString()
        )
    },
    academicTerms = academicTerms.map {
        AcademicTermDto(it.id, it.academicYearStart, it.season.stableId, it.startDate.toString(), it.endDate.toString(), it.createdAt.toString(), it.updatedAt.toString())
    },
    courseSchedules = courseSchedules.map {
        CourseScheduleDto(
            it.id, it.termId, it.courseName, it.location, it.category.stableId,
            it.weekdays.sortedBy(DayOfWeek::getValue).map(::dayOfWeekId),
            it.startTime.toString(), it.endTime.toString(), it.startWeek, it.endWeek,
            it.reminderEnabled, it.reminderAdvanceMinutes, it.createdAt.toString(), it.updatedAt.toString()
        )
    },
    eventPoolItems = eventPoolItems.map {
        EventPoolItemDto(
            it.id, it.title, it.category.stableId, it.isEnabled,
            it.createdAt.toString(), it.updatedAt.toString(), it.weight
        )
    },
    timeRecords = timeRecords.map {
        TimeRecordDto(
            it.id, it.title, it.category.stableId, it.startAt.toString(), it.endAt.toString(), it.source,
            it.relatedTaskId, it.relatedPoolItemId, it.createdAt.toString(), it.updatedAt.toString()
        )
    },
    appSettings = AppSettingsDto(
        lastFocusMinutes = appSettings.lastFocusMinutes,
        lastRestMinutes = appSettings.lastRestMinutes,
        themeMode = appSettings.themeMode.stableId
    )
)

private fun BackupDataDto.toSnapshot() = BackupSnapshot(
    ideas = ideas.map {
        IdeaEntity(it.id, it.content, it.tags, parseInstant(it.createdAt, "灵感创建时间"), parseInstant(it.updatedAt, "灵感更新时间"), it.deletedAt?.let { value -> parseInstant(value, "灵感删除时间") })
    },
    scheduleTasks = scheduleTasks.map {
        ScheduleTaskEntity(
            it.id, it.title, EventCategory.fromStableId(it.category), ScheduleType.fromStableId(it.type),
            it.weekdays.mapTo(linkedSetOf(), ::parseDayOfWeek),
            it.effectiveFrom?.let { value -> parseDate(value, "日程生效日期") },
            it.date?.let { value -> parseDate(value, "日程日期") },
            parseTime(it.startTime, "日程开始时刻"), parseTime(it.endTime, "日程结束时刻"),
            it.isEnabled, it.reminderEnabled, it.reminderAdvanceMinutes,
            parseInstant(it.createdAt, "日程创建时间"), parseInstant(it.updatedAt, "日程更新时间")
        )
    },
    academicTerms = academicTerms.map {
        AcademicTermEntity(
            it.id, it.academicYearStart, TermSeason.fromStableId(it.season),
            parseDate(it.startDate, "学期开始日期"), parseDate(it.endDate, "学期结束日期"),
            parseInstant(it.createdAt, "学期创建时间"), parseInstant(it.updatedAt, "学期更新时间")
        )
    },
    courseSchedules = courseSchedules.map {
        CourseScheduleEntity(
            it.id, it.termId, it.courseName, it.location, EventCategory.fromStableId(it.category),
            it.weekdays.mapTo(linkedSetOf(), ::parseDayOfWeek),
            parseTime(it.startTime, "课程开始时刻"), parseTime(it.endTime, "课程结束时刻"),
            it.startWeek, it.endWeek, it.reminderEnabled, it.reminderAdvanceMinutes,
            parseInstant(it.createdAt, "课程创建时间"), parseInstant(it.updatedAt, "课程更新时间")
        )
    },
    eventPoolItems = eventPoolItems.map {
        EventPoolItemEntity(
            it.id, it.title, EventCategory.fromStableId(it.category), it.isEnabled,
            parseInstant(it.createdAt, "事件池创建时间"), parseInstant(it.updatedAt, "事件池更新时间"), it.weight
        )
    },
    timeRecords = timeRecords.map {
        TimeRecordEntity(
            it.id, it.title, EventCategory.fromStableId(it.category),
            parseInstant(it.startAt, "时间记录开始时间"), parseInstant(it.endAt, "时间记录结束时间"),
            it.source, it.relatedTaskId, it.relatedPoolItemId,
            parseInstant(it.createdAt, "时间记录创建时间"), parseInstant(it.updatedAt, "时间记录更新时间")
        )
    },
    appSettings = AppSettings(
        lastFocusMinutes = appSettings.lastFocusMinutes,
        lastRestMinutes = appSettings.lastRestMinutes,
        themeMode = AppThemeMode.fromStableId(appSettings.themeMode)
    )
)

private fun dayOfWeekId(day: DayOfWeek): String = day.name.lowercase(Locale.ROOT)

private fun parseDayOfWeek(value: String): DayOfWeek = runCatching {
    DayOfWeek.valueOf(value.uppercase(Locale.ROOT))
}.getOrElse { throw IllegalArgumentException("未知星期：$value", it) }

private fun parseInstant(value: String, fieldName: String): Instant = runCatching { Instant.parse(value) }
    .getOrElse { throw IllegalArgumentException("$fieldName 不是有效绝对时间", it) }

private fun parseDate(value: String, fieldName: String): LocalDate = runCatching { LocalDate.parse(value) }
    .getOrElse { throw IllegalArgumentException("$fieldName 不是有效日期", it) }

private fun parseTime(value: String, fieldName: String): LocalTime = runCatching { LocalTime.parse(value) }
    .getOrElse { throw IllegalArgumentException("$fieldName 不是有效时刻", it) }
