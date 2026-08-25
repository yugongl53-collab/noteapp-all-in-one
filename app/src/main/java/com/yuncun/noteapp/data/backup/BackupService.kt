package com.yuncun.noteapp.data.backup

import com.yuncun.noteapp.data.preferences.PomodoroPreferencesStore
import com.yuncun.noteapp.domain.model.PomodoroState
import com.yuncun.noteapp.reminder.ReminderCoordinator
import java.time.Instant
import kotlinx.coroutines.flow.first

data class BackupImportResult(
    val expiredIdeasRemoved: Int,
    val reminderError: String?
)

/** 页面依赖的备份操作边界，文件读取与系统选择器由 Android UI 层负责。 */
interface BackupOperations {
    suspend fun exportJson(): String
    suspend fun prepareImport(content: String): BackupSnapshot
    suspend fun import(snapshot: BackupSnapshot): BackupImportResult
}

/** 统一编排活动计时防护、事务替换、设置补偿以及日程提醒重建。 */
class BackupService(
    private val dataGateway: BackupDataGateway,
    private val preferencesStore: PomodoroPreferencesStore,
    private val reminderCoordinator: ReminderCoordinator,
    private val codec: BackupJsonCodec = BackupJsonCodec(),
    private val clock: () -> Instant = Instant::now
) : BackupOperations {
    override suspend fun exportJson(): String {
        val now = clock()
        val snapshot = dataGateway.load(preferencesStore.settings.first(), now.minusSeconds(RECYCLE_RETENTION_SECONDS))
        return codec.encode(snapshot, now)
    }

    override suspend fun prepareImport(content: String): BackupSnapshot {
        requireNoActivePomodoro()
        return codec.decodeAndValidate(content)
    }

    override suspend fun import(snapshot: BackupSnapshot): BackupImportResult {
        requireNoActivePomodoro()
        codec.validateSnapshot(snapshot)
        val previousSettings = preferencesStore.settings.first()
        reminderCoordinator.cancelScheduled()
        val expiredIdeasRemoved = try {
            dataGateway.replace(snapshot, clock().minusSeconds(RECYCLE_RETENTION_SECONDS)) {
                preferencesStore.updateSettings(snapshot.appSettings)
            }
        } catch (error: Throwable) {
            // Room 已回滚时补偿 DataStore，并按旧数据库重新建立此前取消的提醒。
            runCatching { preferencesStore.updateSettings(previousSettings) }
                .exceptionOrNull()?.let(error::addSuppressed)
            runCatching { reminderCoordinator.synchronize() }
                .exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
        val reminderResult = reminderCoordinator.synchronize()
        return BackupImportResult(expiredIdeasRemoved, reminderResult.errorMessage)
    }

    private suspend fun requireNoActivePomodoro() {
        val state = preferencesStore.pomodoroSession.first()?.state
        require(state != PomodoroState.RUNNING && state != PomodoroState.PAUSED) {
            "番茄钟正在运行或暂停，请先结束当前计时"
        }
    }

    private companion object {
        const val RECYCLE_RETENTION_SECONDS = 30L * 24 * 60 * 60
    }
}
