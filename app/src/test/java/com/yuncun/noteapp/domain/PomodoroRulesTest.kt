package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.rules.PomodoroRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证绝对截止时间、暂停快照、恢复过期与阶段确认的纯状态机。 */
class PomodoroRulesTest {
    private val start = Instant.parse("2026-08-25T00:00:00Z")

    @Test
    fun startAndBackgroundTime_useAbsoluteDeadline() {
        val session = PomodoroRules.startFocus("id", " 阅读 ", 25, 5, start)

        assertEquals("阅读", session.title)
        assertEquals(start.plusSeconds(1_500), session.targetEndAt)
        assertEquals(900, PomodoroRules.remainingSeconds(session, start.plusSeconds(600)))
    }

    @Test
    fun pauseAndResume_persistRemainingSecondsAndCreateNewDeadline() {
        val running = PomodoroRules.startFocus("id", null, 25, 5, start)
        val paused = PomodoroRules.pause(running, start.plusSeconds(120))
        val resumed = PomodoroRules.resume(paused, start.plusSeconds(300))

        assertEquals(PomodoroState.PAUSED, paused.state)
        assertEquals(1_380L, paused.remainingSeconds)
        assertNull(paused.targetEndAt)
        assertEquals(start.plusSeconds(1_680), resumed.targetEndAt)
    }

    @Test
    fun processRecovery_afterDeadlineCompletesWithoutStartingRest() {
        val running = PomodoroRules.startFocus("id", null, 1, 5, start)

        val recovered = PomodoroRules.recover(running, start.plusSeconds(61))

        assertEquals(PomodoroState.COMPLETED, recovered.state)
        assertEquals(PomodoroPhase.FOCUS, recovered.phase)
        assertNull(recovered.targetEndAt)
    }

    @Test
    fun completedFocus_requiresConfirmationBeforeRestStarts() {
        val completed = PomodoroRules.complete(
            PomodoroRules.startFocus("id", "任务", 25, 5, start),
            start.plusSeconds(300)
        )

        val rest = PomodoroRules.startRest(completed, start.plusSeconds(301))

        assertEquals(PomodoroPhase.REST, rest.phase)
        assertEquals(PomodoroState.RUNNING, rest.state)
        assertEquals(start.plusSeconds(601), rest.targetEndAt)
    }

    @Test
    fun durationBoundaries_rejectOutOfRangeValues() {
        assertTrue(runCatching { PomodoroRules.startFocus("id", null, 0, 5, start) }.isFailure)
        assertTrue(runCatching { PomodoroRules.startFocus("id", null, 25, 61, start) }.isFailure)
        assertEquals(180, PomodoroRules.startFocus("id", null, 180, 60, start).plannedFocusMinutes)
    }
}
