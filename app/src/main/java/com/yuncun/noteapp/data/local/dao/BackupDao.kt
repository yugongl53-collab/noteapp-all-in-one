package com.yuncun.noteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** 备份恢复按外键依赖顺序清空六张业务表，只能由整体替换事务调用。 */
@Dao
abstract class BackupDao {
    @Transaction
    open suspend fun clearAllBusinessData() {
        deleteTimeRecords()
        deleteCourses()
        deleteTasks()
        deletePoolItems()
        deleteTerms()
        deleteIdeas()
    }

    @Query("DELETE FROM time_records")
    protected abstract suspend fun deleteTimeRecords()

    @Query("DELETE FROM course_schedules")
    protected abstract suspend fun deleteCourses()

    @Query("DELETE FROM schedule_tasks")
    protected abstract suspend fun deleteTasks()

    @Query("DELETE FROM event_pool_items")
    protected abstract suspend fun deletePoolItems()

    @Query("DELETE FROM academic_terms")
    protected abstract suspend fun deleteTerms()

    @Query("DELETE FROM ideas")
    protected abstract suspend fun deleteIdeas()
}
