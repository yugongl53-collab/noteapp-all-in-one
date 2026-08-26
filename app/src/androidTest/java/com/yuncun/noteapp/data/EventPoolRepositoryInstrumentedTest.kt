package com.yuncun.noteapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.repository.RoomEventPoolRepository
import com.yuncun.noteapp.domain.model.EventCategory
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证事件池仓库经 Room 完成新增、规范化、启停、编辑与删除。 */
@RunWith(AndroidJUnit4::class)
class EventPoolRepositoryInstrumentedTest {
    private lateinit var database: NoteDatabase
    private lateinit var repository: RoomEventPoolRepository
    private val now = Instant.parse("2026-08-25T00:00:00Z")

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
        repository = RoomEventPoolRepository(database.eventPoolItemDao(), idFactory = { "pool" })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crud_preservesCreationTimeAndPersistsEnabledState() = runBlocking {
        repository.save(null, " 阅读 ", EventCategory.STUDY, true, 3, now)
        repository.setEnabled("pool", false, now.plusSeconds(60))
        repository.save("pool", "写作", EventCategory.WORK, false, 7, now.plusSeconds(120))

        val saved = repository.load().single()
        assertEquals("写作", saved.title)
        assertEquals(now, saved.createdAt)
        assertFalse(saved.isEnabled)
        assertEquals(7, saved.weight)

        repository.delete("pool")
        assertEquals(emptyList<Any>(), repository.load())
    }

    @Test
    fun save_rejectsWeightOutsideSupportedRangeWithoutWriting() = runBlocking {
        val result = runCatching {
            repository.save(null, "阅读", EventCategory.STUDY, true, 0, now)
        }

        assertTrue(result.isFailure)
        assertTrue(repository.load().isEmpty())
    }
}
