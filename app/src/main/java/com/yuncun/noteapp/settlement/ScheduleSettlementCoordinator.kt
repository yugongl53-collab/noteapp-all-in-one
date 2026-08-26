package com.yuncun.noteapp.settlement

import com.yuncun.noteapp.data.local.entity.CourseScheduleEntity
import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.TimeRecordRepository
import com.yuncun.noteapp.domain.model.CourseRule
import com.yuncun.noteapp.domain.model.ScheduleRule
import com.yuncun.noteapp.domain.rules.ScheduleAutoSettlementRules
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 自动结算同步结果。 */
data class SettlementSyncResult(
    val settledCount: Int = 0,
    val skippedCount: Int = 0,
    val nextSettlementAt: Instant? = null
)

/** 结算记录注册表，保存已处理的日程实例标识以实现幂等防重。 */
interface SettlementRegistry {
    fun read(): Set<String>
    fun addSettled(keys: Set<String>)
    fun replace(keys: Set<String>)
}

/** 自动结算定时闹钟网关，用于在下一次日程结束时唤醒结算。 */
interface SettlementAlarmGateway {
    fun scheduleNextSettlement(triggerAt: Instant)
    fun cancel()
}

/** 日程自动结算协调器接口。 */
interface ScheduleSettlementCoordinator {
    suspend fun synchronize(): SettlementSyncResult
}

/**
 * 自动结算核心协调器：
 * 1. 扫描已结束但未结算的日程实例；
 * 2. 写入 TimeRecord（发生重叠时静默跳过）；
 * 3. 记录已结算防重标识；
 * 4. 调度下一次日程结束时的唤醒闹钟。
 */
class DefaultScheduleSettlementCoordinator(
    private val scheduleRepository: ScheduleRepository,
    private val timeRecordRepository: TimeRecordRepository,
    private val registry: SettlementRegistry,
    private val alarmGateway: SettlementAlarmGateway,
    private val clock: () -> Instant = Instant::now,
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault
) : ScheduleSettlementCoordinator {
    private val mutex = Mutex()

    override suspend fun synchronize(): SettlementSyncResult = mutex.withLock {
        val now = clock()
        val zoneId = zoneIdProvider()
        val snapshot = scheduleRepository.load()
        val settledKeys = registry.read()

        val schedules = snapshot.tasks.map(ScheduleTaskEntity::toRule)
        val courses = snapshot.courses.map(CourseScheduleEntity::toRule)
        val terms = snapshot.terms.map { it.toPeriod() }

        val candidates = ScheduleAutoSettlementRules.unsettledEndedCandidates(
            schedules = schedules,
            courses = courses,
            terms = terms,
            now = now,
            zoneId = zoneId,
            settledKeys = settledKeys
        )

        var settledCount = 0
        var skippedCount = 0
        val newlySettledKeys = mutableSetOf<String>()

        candidates.forEach { candidate ->
            val inserted = timeRecordRepository.saveAutoSettlement(
                id = candidate.deterministicId,
                title = candidate.title,
                category = candidate.category,
                startAt = candidate.startAt,
                endAt = candidate.endAt,
                relatedTaskId = candidate.relatedTaskId,
                now = now
            )
            if (inserted) {
                settledCount++
            } else {
                skippedCount++
            }
            newlySettledKeys += candidate.instanceKey
        }

        if (newlySettledKeys.isNotEmpty()) {
            registry.addSettled(newlySettledKeys)
        }

        val nextEnding = ScheduleAutoSettlementRules.nextEndingInstant(
            schedules = schedules,
            courses = courses,
            terms = terms,
            now = now,
            zoneId = zoneId
        )

        if (nextEnding != null) {
            alarmGateway.scheduleNextSettlement(nextEnding)
        } else {
            alarmGateway.cancel()
        }

        SettlementSyncResult(
            settledCount = settledCount,
            skippedCount = skippedCount,
            nextSettlementAt = nextEnding
        )
    }
}

private fun ScheduleTaskEntity.toRule() = ScheduleRule(
    id, title, category, type, weekdays, effectiveFrom, date, startTime, endTime, isEnabled
)

private fun CourseScheduleEntity.toRule() = CourseRule(
    id, termId, courseName, location, weekdays, startTime, endTime, startWeek, endWeek
)

object NoOpScheduleSettlementCoordinator : ScheduleSettlementCoordinator {
    override suspend fun synchronize() = SettlementSyncResult()
}
