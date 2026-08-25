package com.yuncun.noteapp.data.preferences

import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroSession
import kotlinx.coroutines.flow.Flow

/** 番茄钟协调器只依赖原子偏好接口，便于验证进程恢复和写入失败。 */
interface PomodoroPreferencesStore {
    val settings: Flow<AppSettings>
    val pomodoroSession: Flow<PomodoroSession?>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun savePomodoroSession(session: PomodoroSession)
    suspend fun clearPomodoroSession()
}
