package com.yuncun.noteapp.reminder

import com.yuncun.noteapp.data.local.entity.ScheduleTaskEntity
import com.yuncun.noteapp.data.repository.AcademicTermInput
import com.yuncun.noteapp.data.repository.CourseScheduleInput
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.ScheduleSnapshot
import com.yuncun.noteapp.data.repository.ScheduleTaskInput
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证权限、即时通知、去重和旧闹钟替换都由协调器统一保证。 */
class DefaultReminderCoordinatorTest {
    private val now = Instant.parse("2026-08-25T01:57:00Z")
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun synchronize_withoutPermission_cancelsOldAlarmsAndReportsInactive() = runTest {
        val alarmGateway = FakeAlarmGateway().apply { scheduledIds += "old" }
        val registry = FakeReminderRegistry(ReminderRegistrySnapshot(setOf("old"), emptySet()))
        val coordinator = coordinator(
            tasks = listOf(task("future", LocalTime.of(11, 0))),
            permissions = ReminderPermissionState(notificationGranted = false, exactAlarmGranted = true),
            alarmGateway = alarmGateway,
            registry = registry
        )

        val result = coordinator.synchronize()

        assertFalse(result.permissions.isEffective)
        assertEquals(setOf("old"), alarmGateway.cancelledIds)
        assertTrue(alarmGateway.scheduledIds.isEmpty())
        assertTrue(registry.snapshot.scheduledIds.isEmpty())
    }

    @Test
    fun synchronize_schedulesFutureAndImmediatelyDeliversMissedWindow() = runTest {
        val alarmGateway = FakeAlarmGateway()
        val notifications = FakeNotificationGateway()
        val coordinator = coordinator(
            tasks = listOf(task("immediate", LocalTime.of(10, 0)), task("future", LocalTime.of(11, 0))),
            alarmGateway = alarmGateway,
            notifications = notifications
        )

        val result = coordinator.synchronize()

        assertEquals(listOf("immediate"), notifications.delivered.map { it.sourceId })
        assertEquals(setOf("future"), alarmGateway.scheduledCandidates.map { it.sourceId }.toSet())
        assertEquals(1, result.deliveredImmediately)
        assertEquals(1, result.scheduled)
    }

    @Test
    fun synchronize_doesNotDeliverSameImmediateInstanceTwice() = runTest {
        val notifications = FakeNotificationGateway()
        val coordinator = coordinator(
            tasks = listOf(task("immediate", LocalTime.of(10, 0))),
            notifications = notifications
        )

        coordinator.synchronize()
        coordinator.synchronize()

        assertEquals(1, notifications.delivered.size)
    }

    @Test
    fun synchronize_afterDataChange_replacesPreviouslyScheduledAlarm() = runTest {
        val repository = FakeScheduleRepository(mutableListOf(task("event", LocalTime.of(11, 0))))
        val alarmGateway = FakeAlarmGateway()
        val coordinator = DefaultReminderCoordinator(
            repository = repository,
            permissionReader = FakePermissionReader(ReminderPermissionState(true, true)),
            alarmGateway = alarmGateway,
            notificationGateway = FakeNotificationGateway(),
            registry = FakeReminderRegistry(),
            clock = { now },
            zoneId = zoneId
        )
        coordinator.synchronize()
        val oldId = alarmGateway.scheduledCandidates.single().id
        repository.tasks[0] = task("event", LocalTime.of(12, 0))

        coordinator.synchronize()

        assertTrue(oldId in alarmGateway.cancelledIds)
        assertEquals(LocalTime.of(12, 0), alarmGateway.scheduledCandidates.last().startAt.atZone(zoneId).toLocalTime())
    }

    @Test
    fun triggeredWeeklyReminder_schedulesFollowingInstance() = runTest {
        var currentNow = Instant.parse("2026-08-25T00:00:00Z")
        val repository = FakeScheduleRepository(mutableListOf(weeklyTask("weekly")))
        val alarmGateway = FakeAlarmGateway()
        val coordinator = DefaultReminderCoordinator(
            repository = repository,
            permissionReader = FakePermissionReader(ReminderPermissionState(true, true)),
            alarmGateway = alarmGateway,
            notificationGateway = FakeNotificationGateway(),
            registry = FakeReminderRegistry(),
            clock = { currentNow },
            zoneId = zoneId
        )
        coordinator.synchronize()
        val triggered = alarmGateway.scheduledCandidates.single()
        currentNow = triggered.remindAt

        coordinator.handleTriggered(triggered)

        assertEquals(
            Instant.parse("2026-08-27T02:00:00Z"),
            alarmGateway.scheduledCandidates.last().startAt
        )
    }

    @Test
    fun synchronize_afterTimeZoneChange_usesCurrentSystemZone() = runTest {
        var currentZone = ZoneId.of("Asia/Shanghai")
        val alarmGateway = FakeAlarmGateway()
        val coordinator = DefaultReminderCoordinator(
            repository = FakeScheduleRepository(mutableListOf(task("event", LocalTime.of(11, 0)))),
            permissionReader = FakePermissionReader(ReminderPermissionState(true, true)),
            alarmGateway = alarmGateway,
            notificationGateway = FakeNotificationGateway(),
            registry = FakeReminderRegistry(),
            clock = { now },
            zoneIdProvider = { currentZone }
        )
        coordinator.synchronize()
        val shanghaiStart = alarmGateway.scheduledCandidates.last().startAt
        currentZone = ZoneId.of("Asia/Tokyo")

        coordinator.synchronize()

        assertEquals(shanghaiStart.minusSeconds(3600), alarmGateway.scheduledCandidates.last().startAt)
    }

    private fun coordinator(
        tasks: List<ScheduleTaskEntity>,
        permissions: ReminderPermissionState = ReminderPermissionState(true, true),
        alarmGateway: FakeAlarmGateway = FakeAlarmGateway(),
        notifications: FakeNotificationGateway = FakeNotificationGateway(),
        registry: FakeReminderRegistry = FakeReminderRegistry()
    ) = DefaultReminderCoordinator(
        repository = FakeScheduleRepository(tasks.toMutableList()),
        permissionReader = FakePermissionReader(permissions),
        alarmGateway = alarmGateway,
        notificationGateway = notifications,
        registry = registry,
        clock = { now },
        zoneId = zoneId
    )

    private fun task(id: String, startTime: LocalTime) = ScheduleTaskEntity(
        id = id,
        title = "事件-$id",
        category = EventCategory.WORK,
        type = ScheduleType.ONE_OFF,
        weekdays = emptySet(),
        effectiveFrom = null,
        date = LocalDate.parse("2026-08-25"),
        startTime = startTime,
        endTime = startTime.plusHours(1),
        isEnabled = true,
        reminderEnabled = true,
        reminderAdvanceMinutes = 5,
        createdAt = now,
        updatedAt = now
    )

    private fun weeklyTask(id: String) = task(id, LocalTime.of(10, 0)).copy(
        type = ScheduleType.WEEKLY,
        weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        effectiveFrom = LocalDate.parse("2026-08-01"),
        date = null
    )

    private class FakePermissionReader(var state: ReminderPermissionState) : ReminderPermissionReader {
        override fun read(): ReminderPermissionState = state
    }

    private class FakeAlarmGateway : ReminderAlarmGateway {
        val scheduledIds = mutableSetOf<String>()
        val scheduledCandidates = mutableListOf<ReminderCandidate>()
        val cancelledIds = mutableSetOf<String>()

        override fun schedule(candidate: ReminderCandidate) {
            scheduledIds += candidate.id
            scheduledCandidates += candidate
        }

        override fun cancel(reminderId: String) {
            scheduledIds -= reminderId
            cancelledIds += reminderId
        }
    }

    private class FakeNotificationGateway : ReminderNotificationGateway {
        val delivered = mutableListOf<ReminderCandidate>()
        override fun show(candidate: ReminderCandidate) {
            delivered += candidate
        }
    }

    private class FakeReminderRegistry(
        var snapshot: ReminderRegistrySnapshot = ReminderRegistrySnapshot()
    ) : ReminderRegistry {
        override fun read(): ReminderRegistrySnapshot = snapshot
        override fun replace(snapshot: ReminderRegistrySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeScheduleRepository(val tasks: MutableList<ScheduleTaskEntity>) : ScheduleRepository {
        override suspend fun load() = ScheduleSnapshot(emptyList(), tasks.toList(), emptyList())
        override suspend fun saveTerm(id: String?, input: AcademicTermInput, now: Instant) = error("测试未使用")
        override suspend fun deleteTerm(id: String) = error("测试未使用")
        override suspend fun saveTask(id: String?, input: ScheduleTaskInput, now: Instant) = error("测试未使用")
        override suspend fun deleteTask(id: String) = error("测试未使用")
        override suspend fun saveCourse(id: String?, input: CourseScheduleInput, now: Instant) = error("测试未使用")
        override suspend fun deleteCourse(id: String) = error("测试未使用")
    }
}
