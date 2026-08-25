package com.yuncun.noteapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuncun.noteapp.data.backup.BackupSnapshot
import com.yuncun.noteapp.data.backup.RoomBackupDataGateway
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证整体替换的真实 Room 事务回滚与导入后回收站清理。 */
@RunWith(AndroidJUnit4::class)
class BackupDataGatewayInstrumentedTest {
    private lateinit var database: NoteDatabase
    private lateinit var gateway: RoomBackupDataGateway

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gateway = RoomBackupDataGateway(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replace_removesExpiredTrashAndUsesImportedSettingsCallback() = runBlocking {
        database.ideaDao().save(IdeaEntity("old", "旧数据", emptyList(), NOW, NOW, null))
        val expiredAt = NOW.minusSeconds(30L * 24 * 60 * 60)
        var importedSettings: AppSettings? = null
        val snapshot = emptySnapshot().copy(
            ideas = listOf(
                IdeaEntity("active", "新数据", emptyList(), NOW, NOW, null),
                IdeaEntity("expired", "到期", emptyList(), expiredAt, expiredAt, expiredAt)
            ),
            appSettings = AppSettings(30, 10)
        )

        val removed = gateway.replace(snapshot, expiredAt) { importedSettings = snapshot.appSettings }

        assertEquals(1, removed)
        assertEquals(listOf("active"), database.ideaDao().getAll().map { it.id })
        assertEquals(AppSettings(30, 10), importedSettings)
    }

    @Test
    fun replace_constraintFailureRollsBackClearsAndEarlierInserts() = runBlocking {
        database.ideaDao().save(IdeaEntity("old", "旧数据", emptyList(), NOW, NOW, null))
        val first = term("first", "2026-09-01", "2027-01-15")
        val duplicateName = term("second", "2027-09-01", "2028-01-15")
        val invalidSnapshot = emptySnapshot().copy(
            ideas = listOf(IdeaEntity("new", "新数据", emptyList(), NOW, NOW, null)),
            academicTerms = listOf(first, duplicateName)
        )

        val failure = runCatching { gateway.replace(invalidSnapshot, Instant.MIN) {} }

        assertTrue(failure.isFailure)
        assertEquals(listOf("old"), database.ideaDao().getAll().map { it.id })
        assertTrue(database.academicTermDao().getAll().isEmpty())
    }

    private fun term(id: String, start: String, end: String) = AcademicTermEntity(
        id, 2026, TermSeason.FALL, LocalDate.parse(start), LocalDate.parse(end), NOW, NOW
    )

    private fun emptySnapshot() = BackupSnapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), AppSettings()
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T00:00:00Z")
    }
}
