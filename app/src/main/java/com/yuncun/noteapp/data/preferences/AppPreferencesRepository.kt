package com.yuncun.noteapp.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yuncun.noteapp.domain.model.AppThemeMode
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.rules.TextRules
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore 只保存应用设置与活动番茄钟；系统权限不写入本地偏好。 */
class AppPreferencesRepository(private val dataStore: DataStore<Preferences>) : PomodoroPreferencesStore {
    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            lastFocusMinutes = preferences[Keys.lastFocusMinutes] ?: DEFAULT_FOCUS_MINUTES,
            lastRestMinutes = preferences[Keys.lastRestMinutes] ?: DEFAULT_REST_MINUTES,
            themeMode = AppThemeMode.fromStableId(preferences[Keys.themeMode] ?: AppThemeMode.SYSTEM.stableId)
        )
    }

    override val pomodoroSession: Flow<PomodoroSession?> = dataStore.data.map(::readSession)

    override suspend fun updateSettings(settings: AppSettings) {
        validateDurations(settings.lastFocusMinutes, settings.lastRestMinutes)
        dataStore.edit { preferences ->
            preferences[Keys.lastFocusMinutes] = settings.lastFocusMinutes
            preferences[Keys.lastRestMinutes] = settings.lastRestMinutes
            preferences[Keys.themeMode] = settings.themeMode.stableId
        }
    }

    /** 更新外观主题偏好（跟随系统、浅色或深色）。 */
    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode.stableId
        }
    }

    override suspend fun savePomodoroSession(session: PomodoroSession) {
        validateSession(session)
        val normalizedTitle = TextRules.normalizeOptionalText(session.title)
        // 单次 edit 原子替换完整会话，避免读取到一半新、一半旧的状态。
        dataStore.edit { preferences ->
            clearSessionKeys(preferences)
            preferences[Keys.sessionId] = session.id
            normalizedTitle?.let { preferences[Keys.sessionTitle] = it }
            preferences[Keys.sessionPhase] = session.phase.stableId
            preferences[Keys.sessionFocusMinutes] = session.plannedFocusMinutes
            preferences[Keys.sessionRestMinutes] = session.plannedRestMinutes
            preferences[Keys.sessionStartedAt] = session.startedAt.toEpochMilli()
            session.targetEndAt?.let { preferences[Keys.sessionTargetEndAt] = it.toEpochMilli() }
            session.remainingSeconds?.let { preferences[Keys.sessionRemainingSeconds] = it }
            preferences[Keys.sessionState] = session.state.stableId
            preferences[Keys.sessionUpdatedAt] = session.updatedAt.toEpochMilli()
        }
    }

    override suspend fun clearPomodoroSession() {
        dataStore.edit(::clearSessionKeys)
    }

    private fun readSession(preferences: Preferences): PomodoroSession? {
        val id = preferences[Keys.sessionId] ?: return null
        return PomodoroSession(
            id = id,
            title = preferences[Keys.sessionTitle],
            phase = PomodoroPhase.fromStableId(requireNotNull(preferences[Keys.sessionPhase])),
            plannedFocusMinutes = requireNotNull(preferences[Keys.sessionFocusMinutes]),
            plannedRestMinutes = requireNotNull(preferences[Keys.sessionRestMinutes]),
            startedAt = Instant.ofEpochMilli(requireNotNull(preferences[Keys.sessionStartedAt])),
            targetEndAt = preferences[Keys.sessionTargetEndAt]?.let(Instant::ofEpochMilli),
            remainingSeconds = preferences[Keys.sessionRemainingSeconds],
            state = PomodoroState.fromStableId(requireNotNull(preferences[Keys.sessionState])),
            updatedAt = Instant.ofEpochMilli(requireNotNull(preferences[Keys.sessionUpdatedAt]))
        )
    }

    private fun validateSession(session: PomodoroSession) {
        require(session.id.isNotBlank()) { "番茄钟会话标识不能为空" }
        validateDurations(session.plannedFocusMinutes, session.plannedRestMinutes)
        require(session.updatedAt >= session.startedAt) { "番茄钟更新时间不能早于开始时间" }
        when (session.state) {
            PomodoroState.RUNNING -> {
                requireNotNull(session.targetEndAt) { "运行状态必须包含截止时间" }
                require(session.remainingSeconds == null) { "运行状态不能包含暂停剩余秒数" }
            }
            PomodoroState.PAUSED -> {
                require(session.targetEndAt == null) { "暂停状态不能保留运行截止时间" }
                requireNotNull(session.remainingSeconds) { "暂停状态必须包含剩余秒数" }
                require(session.remainingSeconds >= 0) { "暂停剩余秒数不能为负数" }
            }
            PomodoroState.COMPLETED -> Unit
        }
    }

    private fun validateDurations(focusMinutes: Int, restMinutes: Int) {
        require(focusMinutes in 1..180) { "专注时长必须在 1 到 180 分钟之间" }
        require(restMinutes in 1..60) { "休息时长必须在 1 到 60 分钟之间" }
    }

    private fun clearSessionKeys(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        // Preferences.Key 保留具体泛型类型，逐项删除可避免用不安全强转抹平类型。
        preferences.remove(Keys.sessionId)
        preferences.remove(Keys.sessionTitle)
        preferences.remove(Keys.sessionPhase)
        preferences.remove(Keys.sessionFocusMinutes)
        preferences.remove(Keys.sessionRestMinutes)
        preferences.remove(Keys.sessionStartedAt)
        preferences.remove(Keys.sessionTargetEndAt)
        preferences.remove(Keys.sessionRemainingSeconds)
        preferences.remove(Keys.sessionState)
        preferences.remove(Keys.sessionUpdatedAt)
    }

    private object Keys {
        val lastFocusMinutes = intPreferencesKey("last_focus_minutes")
        val lastRestMinutes = intPreferencesKey("last_rest_minutes")
        val themeMode = stringPreferencesKey("theme_mode")
        val sessionId = stringPreferencesKey("pomodoro_id")
        val sessionTitle = stringPreferencesKey("pomodoro_title")
        val sessionPhase = stringPreferencesKey("pomodoro_phase")
        val sessionFocusMinutes = intPreferencesKey("pomodoro_focus_minutes")
        val sessionRestMinutes = intPreferencesKey("pomodoro_rest_minutes")
        val sessionStartedAt = longPreferencesKey("pomodoro_started_at")
        val sessionTargetEndAt = longPreferencesKey("pomodoro_target_end_at")
        val sessionRemainingSeconds = longPreferencesKey("pomodoro_remaining_seconds")
        val sessionState = stringPreferencesKey("pomodoro_state")
        val sessionUpdatedAt = longPreferencesKey("pomodoro_updated_at")
    }

    private companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_REST_MINUTES = 5
    }
}
