package com.yuncun.noteapp.pomodoro

import com.yuncun.noteapp.data.preferences.PomodoroPreferencesStore
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.domain.model.PomodoroSession
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.domain.rules.PomodoroRules
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 平台闹钟只接收运行会话；暂停、完成和清除时必须撤销旧闹钟。 */
interface PomodoroAlarmGateway {
    fun schedule(session: PomodoroSession)
    fun cancel(sessionId: String)
}

interface PomodoroNotificationGateway {
    fun showCompleted(session: PomodoroSession)
}

interface PomodoroCoordinator {
    val settings: Flow<AppSettings>
    val session: Flow<PomodoroSession?>
    suspend fun synchronize()
    suspend fun start(title: String?, focusMinutes: Int, restMinutes: Int)
    suspend fun pause()
    suspend fun resume()
    suspend fun reset()
    suspend fun finishEarly()
    suspend fun startRest()
    suspend fun clear()
    suspend fun handleAlarm(sessionId: String, targetEndAt: Instant)
}

/** 默认协调器串行持久化状态和系统闹钟，过期恢复只显示完成状态而不补发通知。 */
class DefaultPomodoroCoordinator(
    private val store: PomodoroPreferencesStore,
    private val alarmGateway: PomodoroAlarmGateway,
    private val notificationGateway: PomodoroNotificationGateway,
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : PomodoroCoordinator {
    private val mutex = Mutex()
    override val settings: Flow<AppSettings> = store.settings
    override val session: Flow<PomodoroSession?> = store.pomodoroSession

    override suspend fun synchronize() = mutex.withLock {
        val current = store.pomodoroSession.first() ?: return@withLock
        val recovered = PomodoroRules.recover(current, clock())
        if (recovered != current) store.savePomodoroSession(recovered)
        if (recovered.state == PomodoroState.RUNNING) {
            alarmGateway.schedule(recovered)
        } else {
            alarmGateway.cancel(recovered.id)
        }
    }

    override suspend fun start(title: String?, focusMinutes: Int, restMinutes: Int) = mutex.withLock {
        store.pomodoroSession.first()?.let { alarmGateway.cancel(it.id) }
        val now = clock()
        val next = PomodoroRules.startFocus(idFactory(), title, focusMinutes, restMinutes, now)
        // 设置与会话先后写入同一 DataStore 文件；会话只在两项都校验通过后构造。
        store.updateSettings(AppSettings(focusMinutes, restMinutes))
        persistRunning(next)
    }

    override suspend fun pause() = mutate { current, now -> PomodoroRules.pause(current, now) }
    override suspend fun resume() = mutate { current, now -> PomodoroRules.resume(current, now) }
    override suspend fun reset() = mutate { current, now -> PomodoroRules.reset(current, now) }
    override suspend fun finishEarly() = mutate { current, now -> PomodoroRules.complete(current, now) }
    override suspend fun startRest() = mutate { current, now -> PomodoroRules.startRest(current, now) }

    override suspend fun clear() = mutex.withLock {
        store.pomodoroSession.first()?.let { alarmGateway.cancel(it.id) }
        store.clearPomodoroSession()
    }

    override suspend fun handleAlarm(sessionId: String, targetEndAt: Instant) = mutex.withLock {
        val current = store.pomodoroSession.first() ?: return@withLock
        // 旧闹钟的会话或截止时间不匹配时安全忽略，避免重置后被过期 Intent 提前完成。
        if (current.id != sessionId || current.state != PomodoroState.RUNNING || current.targetEndAt != targetEndAt) {
            return@withLock
        }
        val now = clock()
        if (targetEndAt > now) return@withLock
        val completed = PomodoroRules.complete(current, now)
        store.savePomodoroSession(completed)
        alarmGateway.cancel(current.id)
        notificationGateway.showCompleted(completed)
    }

    private suspend fun mutate(transform: (PomodoroSession, Instant) -> PomodoroSession) = mutex.withLock {
        val current = requireNotNull(store.pomodoroSession.first()) { "当前没有番茄钟会话" }
        val next = transform(current, clock())
        if (next.state == PomodoroState.RUNNING) persistRunning(next) else {
            store.savePomodoroSession(next)
            alarmGateway.cancel(next.id)
        }
    }

    private suspend fun persistRunning(session: PomodoroSession) {
        store.savePomodoroSession(session)
        alarmGateway.schedule(session)
    }
}
