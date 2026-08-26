package com.yuncun.noteapp.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuncun.noteapp.data.local.NoteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证版本 1 的既有事件池升级后保留数据，并补齐等概率默认权重。 */
@RunWith(AndroidJUnit4::class)
class NoteDatabaseMigrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        NoteDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrateOneToTwo_addsDefaultWeightAndPreservesPoolItem() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO event_pool_items
                    (id, title, category, isEnabled, createdAt, updatedAt)
                VALUES ('pool', '阅读', 'study', 1, 1787616000000, 1787616000000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            NoteDatabase.MIGRATION_1_2
        )

        migrated.query("SELECT title, weight FROM event_pool_items WHERE id = 'pool'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("阅读", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "issue-7-migration-test.db"
    }
}
