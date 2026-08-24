package com.yuncun.noteapp

import android.app.Application

/**
 * 全局 Application 类，管理应用全局生命周期与依赖初始化
 */
class NoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局基础组件初始化
    }
}
