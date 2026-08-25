package com.yuncun.noteapp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuncun.noteapp.data.preferences.AppPreferencesRepository
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 DataStore 只持久化应用设置和活动番茄钟，不伪造系统权限。 */
@RunWith(AndroidJUnit4::class)
class AppPreferencesInstrumentedTest {
    @Test
    fun settingsAndSession_surviveStoreRecreationAndCanClearSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "m1-preferences-test.preferences_pb")
        file.delete()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repository = AppPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        )
        val session = PomodoroSession(
            id = "session",
            title = "阅读",
            phase = PomodoroPhase.FOCUS,
            plannedFocusMinutes = 30,
            plannedRestMinutes = 5,
            startedAt = Instant.parse("2026-08-25T00:00:00Z"),
            targetEndAt = Instant.parse("2026-08-25T00:30:00Z"),
            remainingSeconds = null,
            state = PomodoroState.RUNNING,
            updatedAt = Instant.parse("2026-08-25T00:00:00Z")
        )
        repository.updateSettings(AppSettings(30, 10))
        repository.savePomodoroSession(session)
        scope.cancel()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = AppPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        )
        assertEquals(AppSettings(30, 10), repository.settings.first())
        assertEquals(session, repository.pomodoroSession.first())
        repository.clearPomodoroSession()
        assertNull(repository.pomodoroSession.first())
        scope.cancel()
        file.delete()
    }
}
