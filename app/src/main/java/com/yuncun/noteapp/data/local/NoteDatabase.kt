package com.yuncun.noteapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yuncun.noteapp.data.local.dao.AcademicTermDao
import com.yuncun.noteapp.data.local.dao.CourseScheduleDao
import com.yuncun.noteapp.data.local.dao.EventPoolItemDao
import com.yuncun.noteapp.data.local.dao.IdeaDao
import com.yuncun.noteapp.data.local.dao.ScheduleTaskDao
import com.yuncun.noteapp.data.local.dao.TimeRecordDao
import com.yuncun.noteapp.data.local.entity.AcademicTermEntity
import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.EventPoolItemEntity
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.local.entity.TimeRecordEntity

/** M1 本地业务数据库；版本升级必须提供显式迁移，禁止破坏性回退。 */
@Database(
    entities = [
        IdeaEntity::class,
        ScheduleTaskEntity::class,
        AcademicTermEntity::class,
        CourseScheduleEntity::class,
        EventPoolItemEntity::class,
        TimeRecordEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun ideaDao(): IdeaDao
    abstract fun scheduleTaskDao(): ScheduleTaskDao
    abstract fun academicTermDao(): AcademicTermDao
    abstract fun courseScheduleDao(): CourseScheduleDao
    abstract fun eventPoolItemDao(): EventPoolItemDao
    abstract fun timeRecordDao(): TimeRecordDao

    companion object {
        private const val DATABASE_NAME = "noteapp.db"

        @Volatile
        private var instance: NoteDatabase? = null

        /** 进程内复用同一数据库实例，Application Context 避免持有页面。 */
        fun getInstance(context: Context): NoteDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                DATABASE_NAME
            ).build().also { instance = it }
        }
    }
}
