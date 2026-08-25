package com.yuncun.noteapp

import android.app.Application
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.preferences.AppPreferencesRepository
import com.yuncun.noteapp.data.preferences.appPreferencesDataStore
import com.yuncun.noteapp.data.repository.IdeaRepository
import com.yuncun.noteapp.data.repository.RoomIdeaRepository
import com.yuncun.noteapp.data.repository.RoomScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleRepository

/**
 * 全局 Application 类，管理应用全局生命周期与依赖初始化
 */
class NoteApp : Application() {
    lateinit var database: NoteDatabase
        private set

    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    lateinit var ideaRepository: IdeaRepository
        private set

    lateinit var scheduleRepository: ScheduleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // 初始化进程级本地唯一事实来源，页面后续通过 Application 取得同一实例。
        database = NoteDatabase.getInstance(this)
        preferencesRepository = AppPreferencesRepository(appPreferencesDataStore)
        ideaRepository = RoomIdeaRepository(database.ideaDao())
        scheduleRepository = RoomScheduleRepository(
            database.academicTermDao(),
            database.scheduleTaskDao(),
            database.courseScheduleDao()
        )
    }
}
