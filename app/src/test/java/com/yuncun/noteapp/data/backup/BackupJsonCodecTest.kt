package com.yuncun.noteapp.data.backup

import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 固定 formatVersion 1 的完整往返、严格字段与跨实体校验边界。 */
class BackupJsonCodecTest {
    private val codec = BackupJsonCodec()

    @Test
    fun completeSnapshot_roundTripsWithoutPomodoroOrPermissionFields() {
        val snapshot = completeSnapshot()

        val json = codec.encode(snapshot, NOW)
        val decoded = codec.decodeAndValidate(json)

        assertEquals(snapshot, decoded)
        assertTrue("\"formatVersion\": 1" in json)
        assertTrue("\"themeMode\": \"dark\"" in json)
        assertTrue("pomodoroSession" !in json)
        assertTrue("notificationGranted" !in json)
    }

    @Test
    fun legacyJsonWithoutThemeMode_decodesWithDefaultSystemTheme() {
        val valid = codec.encode(completeSnapshot(), NOW)
        val legacyJson = valid.replace(Regex(",?\\s*\"themeMode\":\\s*\"dark\""), "")

        val decoded = codec.decodeAndValidate(legacyJson)
        assertEquals(com.yuncun.noteapp.domain.model.AppThemeMode.SYSTEM, decoded.appSettings.themeMode)
    }

    @Test
    fun unknownVersionAndUnknownField_areRejectedBeforeImport() {
        val valid = codec.encode(completeSnapshot(), NOW)
        val unknownVersion = valid.replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        val unknownField = valid.replace("\"exportedAt\":", "\"devicePath\": \"/private\",\n    \"exportedAt\":")

        assertTrue(runCatching { codec.decodeAndValidate(unknownVersion) }.exceptionOrNull()?.message?.contains("版本") == true)
        assertTrue(runCatching { codec.decodeAndValidate(unknownField) }.isFailure)
    }

    @Test
    fun danglingCourseAndTimeRecordReferences_areRejected() {
        val danglingCourse = completeSnapshot().copy(
            courseSchedules = completeSnapshot().courseSchedules.map { it.copy(termId = "missing") }
        )
        val danglingRecord = completeSnapshot().copy(
            timeRecords = completeSnapshot().timeRecords.map { it.copy(relatedTaskId = "missing") }
        )

        assertTrue(runCatching { codec.validateSnapshot(danglingCourse) }.isFailure)
        assertTrue(runCatching { codec.validateSnapshot(danglingRecord) }.isFailure)
    }

    @Test
    fun overlappingTimeRecords_areRejectedWhileAdjacentRecordsAreAccepted() {
        val original = completeSnapshot()
        val first = original.timeRecords.single()
        val adjacent = first.copy(id = "record-2", startAt = first.endAt, endAt = first.endAt.plusSeconds(3600))
        codec.validateSnapshot(original.copy(timeRecords = listOf(first, adjacent)))

        val overlapping = adjacent.copy(startAt = first.endAt.minusSeconds(60))
        assertTrue(runCatching { codec.validateSnapshot(original.copy(timeRecords = listOf(first, overlapping))) }.isFailure)
    }

    @Test
    fun duplicateIdsInvalidMinutesAndOutOfRangeCourse_areRejected() {
        val original = completeSnapshot()
        assertTrue(runCatching {
            codec.validateSnapshot(original.copy(ideas = original.ideas + original.ideas.single()))
        }.isFailure)
        assertTrue(runCatching {
            codec.validateSnapshot(original.copy(timeRecords = original.timeRecords.map { it.copy(endAt = it.endAt.plusSeconds(1)) }))
        }.isFailure)
        assertTrue(runCatching {
            codec.validateSnapshot(original.copy(courseSchedules = original.courseSchedules.map { it.copy(endWeek = 99) }))
        }.isFailure)
    }

    private fun completeSnapshot(): BackupSnapshot {
        val term = AcademicTermEntity(
            "term", 2026, TermSeason.FALL, LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-15"), NOW, NOW
        )
        val task = ScheduleTaskEntity(
            "task", "周会", EventCategory.WORK, ScheduleType.WEEKLY, setOf(DayOfWeek.MONDAY),
            LocalDate.parse("2026-08-24"), null, LocalTime.of(9, 0), LocalTime.of(10, 0),
            true, true, 5, NOW, NOW
        )
        val pool = EventPoolItemEntity("pool", "阅读", EventCategory.STUDY, true, NOW, NOW)
        return BackupSnapshot(
            ideas = listOf(IdeaEntity("idea", "内容", listOf("标签"), NOW, NOW, NOW)),
            scheduleTasks = listOf(task),
            academicTerms = listOf(term),
            courseSchedules = listOf(
                CourseScheduleEntity(
                    "course", "term", "高等数学", "一教", EventCategory.STUDY,
                    setOf(DayOfWeek.TUESDAY), LocalTime.of(8, 0), LocalTime.of(9, 30),
                    1, 10, true, 25, NOW, NOW
                )
            ),
            eventPoolItems = listOf(pool),
            timeRecords = listOf(
                TimeRecordEntity(
                    "record", "周会", EventCategory.WORK, NOW, NOW.plusSeconds(3600), "manual",
                    "task", "pool", NOW, NOW
                )
            ),
            appSettings = AppSettings(30, 10, com.yuncun.noteapp.domain.model.AppThemeMode.DARK)
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T00:00:00Z")
    }
}
