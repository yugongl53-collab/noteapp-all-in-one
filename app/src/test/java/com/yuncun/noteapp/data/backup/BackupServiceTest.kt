package com.yuncun.noteapp.data.backup

import com.yuncun.noteapp.data.preferences.PomodoroPreferencesStore
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.reminder.ReminderCoordinator
import com.yuncun.noteapp.reminder.ReminderPermissionState
import com.yuncun.noteapp.reminder.ReminderSyncResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证跨 Room、DataStore 与提醒适配器的执行顺序和失败补偿。 */
class BackupServiceTest {
    @Test
    fun activePomodoro_blocksParsingAndImport() = runTest {
        val preferences = FakePreferences(session = session(PomodoroState.PAUSED))
        val service = BackupService(FakeGateway(), preferences, FakeReminders(mutableListOf()), clock = { NOW })

        val failure = runCatching { service.prepareImport("not-json") }

        assertTrue(failure.exceptionOrNull()?.message?.contains("番茄钟") == true)
    }

    @Test
    fun successfulImport_cancelsOldRemindersUpdatesSettingsAndSynchronizes() = runTest {
        val events = mutableListOf<String>()
        val preferences = FakePreferences(events = events)
        val gateway = FakeGateway(events = events)
        val reminders = FakeReminders(events)
        val service = BackupService(gateway, preferences, reminders, clock = { NOW })
        val snapshot = emptySnapshot(AppSettings(30, 10))

        val result = service.import(snapshot)

        assertEquals(AppSettings(30, 10), preferences.settingsState.value)
        assertEquals(listOf("cancel", "replace", "settings:30/10", "commit", "synchronize"), events)
        assertEquals(0, result.expiredIdeasRemoved)
    }

    @Test
    fun failedReplace_restoresSettingsAndOldReminders() = runTest {
        val events = mutableListOf<String>()
        val preferences = FakePreferences(events = events)
        val gateway = FakeGateway(events = events, failAfterSettings = true)
        val reminders = FakeReminders(events)
        val service = BackupService(gateway, preferences, reminders, clock = { NOW })

        val failure = runCatching { service.import(emptySnapshot(AppSettings(30, 10))) }

        assertTrue(failure.isFailure)
        assertEquals(AppSettings(), preferences.settingsState.value)
        assertEquals(
            listOf("cancel", "replace", "settings:30/10", "settings:25/5", "synchronize"),
            events
        )
    }

    @Test
    fun failedReminderCancellation_abortsImportAndResynchronizesOldData() = runTest {
        val events = mutableListOf<String>()
        val preferences = FakePreferences(events = events)
        val gateway = FakeGateway(events = events)
        val reminders = FakeReminders(events, failCancellation = true)
        val service = BackupService(gateway, preferences, reminders, clock = { NOW })

        val failure = runCatching { service.import(emptySnapshot(AppSettings(30, 10))) }

        assertTrue(failure.isFailure)
        assertTrue("replace" !in events)
        assertEquals(listOf("cancel", "settings:25/5", "synchronize"), events)
    }

    @Test
    fun failedReminderRebuild_reportsPartialSystemFailureAfterCommittedImport() = runTest {
        val events = mutableListOf<String>()
        val reminders = FakeReminders(events, failSynchronization = true)
        val service = BackupService(FakeGateway(events), FakePreferences(events = events), reminders, clock = { NOW })

        val result = service.import(emptySnapshot(AppSettings(30, 10)))

        assertEquals("模拟提醒重建失败", result.reminderError)
        assertTrue("commit" in events)
    }

    private class FakeGateway(
        private val events: MutableList<String> = mutableListOf(),
        private val failAfterSettings: Boolean = false
    ) : BackupDataGateway {
        override suspend fun load(appSettings: AppSettings, expiredIdeaCutoff: Instant) = emptySnapshot(appSettings)

        override suspend fun replace(
            snapshot: BackupSnapshot,
            expiredIdeaCutoff: Instant,
            updateSettings: suspend () -> Unit
        ): Int {
            events += "replace"
            updateSettings()
            if (failAfterSettings) error("模拟数据库提交失败")
            events += "commit"
            return 0
        }
    }

    private class FakePreferences(
        settings: AppSettings = AppSettings(),
        session: PomodoroSession? = null,
        private val events: MutableList<String> = mutableListOf()
    ) : PomodoroPreferencesStore {
        val settingsState = MutableStateFlow(settings)
        private val sessionState = MutableStateFlow(session)
        override val settings: Flow<AppSettings> = settingsState
        override val pomodoroSession: Flow<PomodoroSession?> = sessionState

        override suspend fun updateSettings(settings: AppSettings) {
            events += "settings:${settings.lastFocusMinutes}/${settings.lastRestMinutes}"
            settingsState.value = settings
        }

        override suspend fun savePomodoroSession(session: PomodoroSession) {
            sessionState.value = session
        }

        override suspend fun clearPomodoroSession() {
            sessionState.value = null
        }
    }

    private class FakeReminders(
        private val events: MutableList<String>,
        private val failCancellation: Boolean = false,
        private val failSynchronization: Boolean = false
    ) : ReminderCoordinator {
        private val permissions = MutableStateFlow(ReminderPermissionState())
        override val permissionState: StateFlow<ReminderPermissionState> = permissions.asStateFlow()
        override fun refreshPermissionState() = Unit
        override suspend fun cancelScheduled() {
            events += "cancel"
            if (failCancellation) error("模拟提醒取消失败")
        }
        override suspend fun synchronize(): ReminderSyncResult {
            events += "synchronize"
            if (failSynchronization) error("模拟提醒重建失败")
            return ReminderSyncResult(permissions.value)
        }
        override suspend fun handleTriggered(candidate: ReminderCandidate) = Unit
    }

    private fun session(state: PomodoroState) = PomodoroSession(
        id = "session",
        title = null,
        phase = PomodoroPhase.FOCUS,
        plannedFocusMinutes = 25,
        plannedRestMinutes = 5,
        startedAt = NOW,
        targetEndAt = null,
        remainingSeconds = if (state == PomodoroState.PAUSED) 60 else null,
        state = state,
        updatedAt = NOW
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-25T00:00:00Z")

        fun emptySnapshot(settings: AppSettings) = BackupSnapshot(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), settings
        )
    }
}
