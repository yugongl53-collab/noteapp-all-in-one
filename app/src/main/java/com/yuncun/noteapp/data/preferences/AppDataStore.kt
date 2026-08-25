package com.yuncun.noteapp.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** 应用级 DataStore 委托确保同一文件在进程内只有一个活动实例。 */
val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")
