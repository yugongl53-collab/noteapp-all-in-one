package com.yuncun.noteapp

import android.app.Application
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.preferences.AppPreferencesRepository
import com.yuncun.noteapp.data.preferences.appPreferencesDataStore

/**
 * 全局 Application 类，管理应用全局生命周期与依赖初始化
 */
class NoteApp : Application() {
    lateinit var database: NoteDatabase
        private set

    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // 初始化进程级本地唯一事实来源，页面后续通过 Application 取得同一实例。
        database = NoteDatabase.getInstance(this)
        preferencesRepository = AppPreferencesRepository(appPreferencesDataStore)
    }
}
