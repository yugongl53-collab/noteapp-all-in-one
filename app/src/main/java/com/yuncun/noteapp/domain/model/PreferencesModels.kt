package com.yuncun.noteapp.domain.model

import java.time.Instant

/** 番茄钟最近一次设置；默认值与产品规则保持一致。 */
data class AppSettings(
    val lastFocusMinutes: Int = 25,
    val lastRestMinutes: Int = 5
)

enum class PomodoroPhase(val stableId: String) {
    FOCUS("focus"),
    REST("rest");

    companion object {
        fun fromStableId(value: String): PomodoroPhase =
            entries.firstOrNull { it.stableId == value }
                ?: throw IllegalArgumentException("未知番茄钟阶段：$value")
    }
}

enum class PomodoroState(val stableId: String) {
    RUNNING("running"),
    PAUSED("paused"),
    COMPLETED("completed");

    companion object {
        fun fromStableId(value: String): PomodoroState =
            entries.firstOrNull { it.stableId == value }
                ?: throw IllegalArgumentException("未知番茄钟状态：$value")
    }
}

/** DataStore 中的活动番茄钟快照；运行与暂停字段由状态决定。 */
data class PomodoroSession(
    val id: String,
    val title: String?,
    val phase: PomodoroPhase,
    val plannedFocusMinutes: Int,
    val plannedRestMinutes: Int,
    val startedAt: Instant,
    val targetEndAt: Instant?,
    val remainingSeconds: Long?,
    val state: PomodoroState,
    val updatedAt: Instant
)
