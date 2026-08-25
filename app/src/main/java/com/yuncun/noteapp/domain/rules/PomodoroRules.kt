package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import java.time.Duration
import java.time.Instant

/** 番茄钟状态机只使用绝对时间，确保前后台和进程恢复得到同一结果。 */
object PomodoroRules {
    fun startFocus(
        id: String,
        title: String?,
        focusMinutes: Int,
        restMinutes: Int,
        now: Instant
    ): PomodoroSession {
        validateDurations(focusMinutes, restMinutes)
        return runningSession(
            id = id,
            title = TextRules.normalizeOptionalText(title),
            phase = PomodoroPhase.FOCUS,
            focusMinutes = focusMinutes,
            restMinutes = restMinutes,
            now = now
        )
    }

    /** 恢复运行会话时，截止时间已到即固定为完成，不自动切换阶段。 */
    fun recover(session: PomodoroSession, now: Instant): PomodoroSession {
        if (session.state != PomodoroState.RUNNING) return session
        val target = requireNotNull(session.targetEndAt) { "运行状态必须包含截止时间" }
        return if (target <= now) complete(session, now) else session
    }

    fun remainingSeconds(session: PomodoroSession, now: Instant): Long = when (session.state) {
        PomodoroState.COMPLETED -> 0
        PomodoroState.PAUSED -> requireNotNull(session.remainingSeconds)
        PomodoroState.RUNNING -> {
            val millis = Duration.between(now, requireNotNull(session.targetEndAt)).toMillis()
            if (millis <= 0) 0 else (millis + 999) / 1_000
        }
    }

    fun pause(session: PomodoroSession, now: Instant): PomodoroSession {
        val current = recover(session, now)
        require(current.state == PomodoroState.RUNNING) { "只有运行中的番茄钟可以暂停" }
        return current.copy(
            targetEndAt = null,
            remainingSeconds = remainingSeconds(current, now),
            state = PomodoroState.PAUSED,
            updatedAt = now
        )
    }

    fun resume(session: PomodoroSession, now: Instant): PomodoroSession {
        require(session.state == PomodoroState.PAUSED) { "只有暂停的番茄钟可以继续" }
        val remaining = requireNotNull(session.remainingSeconds)
        return session.copy(
            targetEndAt = now.plusSeconds(remaining),
            remainingSeconds = null,
            state = PomodoroState.RUNNING,
            updatedAt = now
        )
    }

    /** 重置保留当前阶段和本轮设置，从完整阶段时长重新运行。 */
    fun reset(session: PomodoroSession, now: Instant): PomodoroSession = runningSession(
        id = session.id,
        title = session.title,
        phase = session.phase,
        focusMinutes = session.plannedFocusMinutes,
        restMinutes = session.plannedRestMinutes,
        now = now
    )

    fun complete(session: PomodoroSession, now: Instant): PomodoroSession = session.copy(
        targetEndAt = null,
        remainingSeconds = null,
        state = PomodoroState.COMPLETED,
        updatedAt = now
    )

    fun startRest(session: PomodoroSession, now: Instant): PomodoroSession {
        require(session.phase == PomodoroPhase.FOCUS && session.state == PomodoroState.COMPLETED) {
            "只有已完成的专注阶段可以开始休息"
        }
        return runningSession(
            id = session.id,
            title = session.title,
            phase = PomodoroPhase.REST,
            focusMinutes = session.plannedFocusMinutes,
            restMinutes = session.plannedRestMinutes,
            now = now
        )
    }

    private fun runningSession(
        id: String,
        title: String?,
        phase: PomodoroPhase,
        focusMinutes: Int,
        restMinutes: Int,
        now: Instant
    ): PomodoroSession {
        validateDurations(focusMinutes, restMinutes)
        val durationMinutes = if (phase == PomodoroPhase.FOCUS) focusMinutes else restMinutes
        return PomodoroSession(
            id = id,
            title = title,
            phase = phase,
            plannedFocusMinutes = focusMinutes,
            plannedRestMinutes = restMinutes,
            startedAt = now,
            targetEndAt = now.plusSeconds(durationMinutes * 60L),
            remainingSeconds = null,
            state = PomodoroState.RUNNING,
            updatedAt = now
        )
    }

    private fun validateDurations(focusMinutes: Int, restMinutes: Int) {
        require(focusMinutes in 1..180) { "专注时长必须在 1 到 180 分钟之间" }
        require(restMinutes in 1..60) { "休息时长必须在 1 到 60 分钟之间" }
    }
}
