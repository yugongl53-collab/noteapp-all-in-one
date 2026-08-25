package com.yuncun.noteapp.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleType
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 Room 约束、DAO 事务校验和磁盘重启后的数据一致性。 */
@RunWith(AndroidJUnit4::class)
class NoteDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: NoteDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allEntityDaos_persistAndReadTheSixM1Entities() = runBlocking {
        val now = Instant.parse("2026-08-25T00:00:00Z")
        val term = term(id = "term", start = "2026-09-01", end = "2027-01-15", now = now)
        database.academicTermDao().save(term)
        database.ideaDao().save(IdeaEntity("idea", "内容", listOf("标签"), now, now, null))
        database.scheduleTaskDao().save(schedule(now))
        database.courseScheduleDao().save(course(now))
        database.eventPoolItemDao().save(EventPoolItemEntity("pool", "阅读", EventCategory.STUDY, true, now, now))
        database.timeRecordDao().save(record("record", now, now.plusSeconds(3600)))

        assertEquals(1, database.ideaDao().getAll().size)
        assertEquals(1, database.scheduleTaskDao().getAll().size)
        assertEquals(1, database.academicTermDao().getAll().size)
        assertEquals(1, database.courseScheduleDao().getAll().size)
        assertEquals(1, database.eventPoolItemDao().getAll().size)
        assertEquals(1, database.timeRecordDao().getAll().size)
    }

    @Test
    fun academicTermDao_rejectsDuplicateNameAndOverlappingRangeWithoutPartialWrite() = runBlocking {
        val now = Instant.parse("2026-08-25T00:00:00Z")
        database.academicTermDao().save(term("first", "2026-09-01", "2027-01-15", now))

        val duplicate = runCatching {
            database.academicTermDao().save(term("duplicate", "2027-09-01", "2028-01-15", now))
        }
        val overlap = runCatching {
            database.academicTermDao().save(
                term("overlap", "2027-01-01", "2027-06-30", now, TermSeason.SPRING)
            )
        }

        assertTrue(duplicate.isFailure)
        assertTrue(overlap.isFailure)
        assertEquals(listOf("first"), database.academicTermDao().getAll().map { it.id })
    }

    @Test
    fun courseScheduleDao_rejectsDanglingTermReference() = runBlocking {
        val failure = runCatching {
            database.courseScheduleDao().save(course(Instant.parse("2026-08-25T00:00:00Z")))
        }
        assertTrue(failure.isFailure)
        assertTrue(database.courseScheduleDao().getAll().isEmpty())
    }

    @Test
    fun timeRecordDao_allowsAdjacencyAndRejectsOverlapWhenEditing() = runBlocking {
        val start = Instant.parse("2026-08-25T00:00:00Z")
        database.timeRecordDao().save(record("first", start, start.plusSeconds(3600)))
        database.timeRecordDao().save(record("adjacent", start.plusSeconds(3600), start.plusSeconds(7200)))

        val overlap = runCatching {
            database.timeRecordDao().save(record("overlap", start.plusSeconds(1800), start.plusSeconds(5400)))
        }
        val selfEdit = database.timeRecordDao().save(record("first", start.plusSeconds(60), start.plusSeconds(3540)))

        assertTrue(overlap.isFailure)
        assertEquals("first", selfEdit)
        assertEquals(2, database.timeRecordDao().getAll().size)
    }

    @Test
    fun database_reopensWithTheSamePersistedData() = runBlocking {
        database.close()
        val databaseName = "m1-restart-test.db"
        context.deleteDatabase(databaseName)
        var diskDatabase = Room.databaseBuilder(context, NoteDatabase::class.java, databaseName).build()
        val now = Instant.parse("2026-08-25T00:00:00Z")
        diskDatabase.ideaDao().save(IdeaEntity("idea", "重启后仍存在", emptyList(), now, now, null))
        diskDatabase.close()

        diskDatabase = Room.databaseBuilder(context, NoteDatabase::class.java, databaseName).build()
        assertEquals("重启后仍存在", diskDatabase.ideaDao().getAll().single().content)
        diskDatabase.close()
        context.deleteDatabase(databaseName)
        // 避免 @After 重复关闭已关闭实例时掩盖测试结果。
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
    }

    @Test
    fun schema_exposesForeignKeysForIntegrityChecks() {
        val cursor = database.query(SimpleSQLiteQuery("PRAGMA foreign_key_check"))
        cursor.use { assertEquals(0, it.count) }
    }

    private fun term(
        id: String,
        start: String,
        end: String,
        now: Instant,
        season: TermSeason = TermSeason.FALL
    ) = AcademicTermEntity(id, 2026, season, LocalDate.parse(start), LocalDate.parse(end), now, now)

    private fun schedule(now: Instant) = ScheduleTaskEntity(
        id = "task",
        title = "周会",
        category = EventCategory.WORK,
        type = ScheduleType.WEEKLY,
        weekdays = setOf(DayOfWeek.MONDAY),
        effectiveFrom = LocalDate.parse("2026-08-24"),
        date = null,
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = now,
        updatedAt = now
    )

    private fun course(now: Instant) = CourseScheduleEntity(
        id = "course",
        termId = "term",
        courseName = "高等数学",
        location = "一教",
        category = EventCategory.STUDY,
        weekdays = setOf(DayOfWeek.TUESDAY),
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 30),
        startWeek = 1,
        endWeek = 10,
        reminderEnabled = true,
        reminderAdvanceMinutes = 25,
        createdAt = now,
        updatedAt = now
    )

    private fun record(id: String, start: Instant, end: Instant) = TimeRecordEntity(
        id = id,
        title = "工作",
        category = EventCategory.WORK,
        startAt = start,
        endAt = end,
        source = "manual",
        relatedTaskId = null,
        relatedPoolItemId = null,
        createdAt = start,
        updatedAt = start
    )
}
