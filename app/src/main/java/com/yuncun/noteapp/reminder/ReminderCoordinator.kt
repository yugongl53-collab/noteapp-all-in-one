package com.yuncun.noteapp.reminder

import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.domain.model.ReminderConfiguration
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.rules.ReminderScheduleRules
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 两项系统权限都具备时，日程提醒才可被描述为已生效。 */
data class ReminderPermissionState(
    val notificationGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false
) {
    val isEffective: Boolean get() = notificationGranted && exactAlarmGranted

    fun missingReason(): String = buildList {
        if (!notificationGranted) add("通知权限")
        if (!exactAlarmGranted) add("“闹钟和提醒”权限")
    }.joinToString("、")
}

data class ReminderSyncResult(
    val permissions: ReminderPermissionState,
    val scheduled: Int = 0,
    val deliveredImmediately: Int = 0,
    val errorMessage: String? = null
)

data class ReminderRegistrySnapshot(
    val scheduledIds: Set<String> = emptySet(),
    val deliveredIds: Set<String> = emptySet()
)

/** 平台权限、AlarmManager、通知和轻量注册表均以窄接口隔离，便于本地单测。 */
interface ReminderPermissionReader {
    fun read(): ReminderPermissionState
}

interface ReminderAlarmGateway {
    fun schedule(candidate: ReminderCandidate)
    fun cancel(reminderId: String)
}

interface ReminderNotificationGateway {
    fun show(candidate: ReminderCandidate)
}

interface ReminderRegistry {
    fun read(): ReminderRegistrySnapshot
    fun replace(snapshot: ReminderRegistrySnapshot)
}

interface ReminderCoordinator {
    val permissionState: StateFlow<ReminderPermissionState>
    fun refreshPermissionState()
    suspend fun synchronize(): ReminderSyncResult
    suspend fun handleTriggered(candidate: ReminderCandidate)
}

/** 统一执行“先取消旧提醒，再按当前数据重建”，并用已送达集合阻止即时提醒重复。 */
class DefaultReminderCoordinator(
    private val repository: ScheduleRepository,
    private val permissionReader: ReminderPermissionReader,
    private val alarmGateway: ReminderAlarmGateway,
    private val notificationGateway: ReminderNotificationGateway,
    private val registry: ReminderRegistry,
    private val clock: () -> Instant = Instant::now,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ReminderCoordinator {
    private val mutex = Mutex()
    private val _permissionState = MutableStateFlow(permissionReader.read())
    override val permissionState: StateFlow<ReminderPermissionState> = _permissionState.asStateFlow()

    override fun refreshPermissionState() {
        _permissionState.value = permissionReader.read()
    }

    override suspend fun synchronize(): ReminderSyncResult = mutex.withLock {
        refreshPermissionState()
        val permissions = _permissionState.value
        val previousRegistry = registry.read()
        if (!permissions.isEffective) {
            // 权限撤销后必须清掉仍登记的系统闹钟，但保留业务配置与已送达去重状态。
            previousRegistry.scheduledIds.forEach(alarmGateway::cancel)
            registry.replace(previousRegistry.copy(scheduledIds = emptySet()))
            return@withLock ReminderSyncResult(permissions)
        }

        val candidates = runCatching { loadCandidates() }.getOrElse { error ->
            return@withLock ReminderSyncResult(
                permissions = permissions,
                errorMessage = error.message ?: "读取日程失败"
            )
        }
        val currentIds = candidates.mapTo(mutableSetOf()) { it.id }
        val deliveredIds = previousRegistry.deliveredIds.intersect(currentIds).toMutableSet()
        val scheduledIds = mutableSetOf<String>()
        var deliveredImmediately = 0

        // 数据修改、删除、时区变化和重启都走同一路径，旧 PendingIntent 不会残留。
        previousRegistry.scheduledIds.forEach(alarmGateway::cancel)
        val failure = runCatching {
            candidates.filterNot { it.id in deliveredIds }.forEach { candidate ->
                if (candidate.shouldNotifyImmediately(clock())) {
                    notificationGateway.show(candidate)
                    deliveredIds += candidate.id
                    deliveredImmediately += 1
                } else {
                    alarmGateway.schedule(candidate)
                    scheduledIds += candidate.id
                }
            }
        }.exceptionOrNull()
        if (failure != null) {
            // 部分调度失败时撤回本轮已创建闹钟，不能向界面报告半成功状态。
            scheduledIds.forEach(alarmGateway::cancel)
            registry.replace(ReminderRegistrySnapshot(deliveredIds = deliveredIds))
            return@withLock ReminderSyncResult(
                permissions = permissions,
                deliveredImmediately = deliveredImmediately,
                errorMessage = failure.message ?: "系统提醒调度失败"
            )
        }

        registry.replace(ReminderRegistrySnapshot(scheduledIds, deliveredIds))
        ReminderSyncResult(permissions, scheduledIds.size, deliveredImmediately)
    }

    override suspend fun handleTriggered(candidate: ReminderCandidate) {
        mutex.withLock {
            refreshPermissionState()
            val current = registry.read()
            val delivered = current.deliveredIds.toMutableSet()
            // 闹钟触发瞬间若通知权限已被撤销，不标记送达；恢复权限后仍可在开始前即时补发。
            if (_permissionState.value.notificationGranted) {
                notificationGateway.show(candidate)
                delivered += candidate.id
            }
            registry.replace(
                current.copy(
                    scheduledIds = current.scheduledIds - candidate.id,
                    deliveredIds = delivered
                )
            )
        }
        synchronize()
    }

    private suspend fun loadCandidates(): List<ReminderCandidate> {
        val snapshot = repository.load()
        val configurations = snapshot.tasks.map(ScheduleTaskEntity::toReminderConfiguration) +
            snapshot.courses.map(CourseScheduleEntity::toReminderConfiguration)
        return ReminderScheduleRules.nextCandidates(
            schedules = snapshot.tasks.map(ScheduleTaskEntity::toRule),
            courses = snapshot.courses.map(CourseScheduleEntity::toRule),
            terms = snapshot.terms.map { it.toPeriod() },
            configurations = configurations,
            now = clock(),
            zoneId = zoneId
        )
    }
}

private fun ScheduleTaskEntity.toRule() = ScheduleRule(
    id, title, category, type, weekdays, effectiveFrom, date, startTime, endTime, isEnabled
)

private fun CourseScheduleEntity.toRule() = CourseRule(
    id, termId, courseName, location, weekdays, startTime, endTime, startWeek, endWeek
)

private fun ScheduleTaskEntity.toReminderConfiguration() = ReminderConfiguration(
    ScheduleSource.TASK, id, reminderEnabled, reminderAdvanceMinutes
)

private fun CourseScheduleEntity.toReminderConfiguration() = ReminderConfiguration(
    ScheduleSource.COURSE, id, reminderEnabled, reminderAdvanceMinutes
)

/** 仅供不需要提醒副作用的预览或局部测试使用。 */
object NoOpReminderCoordinator : ReminderCoordinator {
    private val state = MutableStateFlow(ReminderPermissionState())
    override val permissionState: StateFlow<ReminderPermissionState> = state.asStateFlow()
    override fun refreshPermissionState() = Unit
    override suspend fun synchronize() = ReminderSyncResult(state.value)
    override suspend fun handleTriggered(candidate: ReminderCandidate) = Unit
}
