package com.yuncun.noteapp.pomodoro

import com.yuncun.noteapp.data.preferences.PomodoroPreferencesStore
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证协调器的持久化顺序、重启恢复、旧闹钟防护与通知边界。 */
class DefaultPomodoroCoordinatorTest {
    private val now = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun start_savesRecentSettingsAndSchedulesPersistedDeadline() = runTest {
        val fixture = fixture()

        fixture.coordinator.start("阅读", 30, 10)

        assertEquals(AppSettings(30, 10), fixture.store.settingsState.value)
        assertEquals(now.plusSeconds(1_800), fixture.store.sessionState.value?.targetEndAt)
        assertEquals(listOf("session"), fixture.alarms.scheduled.map { it.id })
    }

    @Test
    fun synchronize_afterExpiredDeadlineCompletesWithoutLateNotification() = runTest {
        val expired = session(target = now.minusSeconds(1))
        val fixture = fixture(expired, clock = { now })

        fixture.coordinator.synchronize()

        assertEquals(PomodoroState.COMPLETED, fixture.store.sessionState.value?.state)
        assertTrue(fixture.notifications.shown.isEmpty())
        assertEquals(listOf("session"), fixture.alarms.cancelled)
    }

    @Test
    fun staleAlarm_afterResetCannotCompleteCurrentSession() = runTest {
        val current = session(target = now.plusSeconds(60))
        val fixture = fixture(current, clock = { now.plusSeconds(61) })

        fixture.coordinator.handleAlarm("session", now.plusSeconds(30))

        assertEquals(PomodoroState.RUNNING, fixture.store.sessionState.value?.state)
        assertTrue(fixture.notifications.shown.isEmpty())
    }

    @Test
    fun matchingAlarm_completesAndNotifiesButNeverCreatesTimeRecord() = runTest {
        val target = now.plusSeconds(60)
        val fixture = fixture(session(target), clock = { target })

        fixture.coordinator.handleAlarm("session", target)

        assertEquals(PomodoroState.COMPLETED, fixture.store.sessionState.value?.state)
        assertEquals(listOf(PomodoroPhase.FOCUS), fixture.notifications.shown.map { it.phase })
    }

    @Test
    fun clear_cancelsAlarmAndRemovesOnlyActiveSession() = runTest {
        val fixture = fixture(session(now.plusSeconds(60)))

        fixture.coordinator.clear()

        assertNull(fixture.store.sessionState.value)
        assertEquals(AppSettings(), fixture.store.settingsState.value)
    }

    private fun fixture(
        session: PomodoroSession? = null,
        clock: () -> Instant = { now }
    ): Fixture {
        val store = FakeStore(session)
        val alarms = FakeAlarms()
        val notifications = FakeNotifications()
        return Fixture(
            store,
            alarms,
            notifications,
            DefaultPomodoroCoordinator(store, alarms, notifications, clock, idFactory = { "session" })
        )
    }

    private fun session(target: Instant) = PomodoroSession(
        id = "session",
        title = "阅读",
        phase = PomodoroPhase.FOCUS,
        plannedFocusMinutes = 25,
        plannedRestMinutes = 5,
        startedAt = now.minusSeconds(60),
        targetEndAt = target,
        remainingSeconds = null,
        state = PomodoroState.RUNNING,
        updatedAt = now.minusSeconds(60)
    )

    private data class Fixture(
        val store: FakeStore,
        val alarms: FakeAlarms,
        val notifications: FakeNotifications,
        val coordinator: DefaultPomodoroCoordinator
    )

    private class FakeStore(session: PomodoroSession?) : PomodoroPreferencesStore {
        val settingsState = MutableStateFlow(AppSettings())
        val sessionState = MutableStateFlow(session)
        override val settings: Flow<AppSettings> = settingsState
        override val pomodoroSession: Flow<PomodoroSession?> = sessionState
        override suspend fun updateSettings(settings: AppSettings) { settingsState.value = settings }
        override suspend fun savePomodoroSession(session: PomodoroSession) { sessionState.value = session }
        override suspend fun clearPomodoroSession() { sessionState.value = null }
    }

    private class FakeAlarms : PomodoroAlarmGateway {
        val scheduled = mutableListOf<PomodoroSession>()
        val cancelled = mutableListOf<String>()
        override fun schedule(session: PomodoroSession) { scheduled += session }
        override fun cancel(sessionId: String) { cancelled += sessionId }
    }

    private class FakeNotifications : PomodoroNotificationGateway {
        val shown = mutableListOf<PomodoroSession>()
        override fun showCompleted(session: PomodoroSession) { shown += session }
    }
}
