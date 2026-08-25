package com.yuncun.noteapp

import android.app.Application
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.data.backup.BackupOperations
import com.yuncun.noteapp.data.backup.BackupService
import com.yuncun.noteapp.data.backup.RoomBackupDataGateway
import com.yuncun.noteapp.data.preferences.AppPreferencesRepository
import com.yuncun.noteapp.data.preferences.appPreferencesDataStore
import com.yuncun.noteapp.data.repository.IdeaRepository
import com.yuncun.noteapp.data.repository.EventPoolRepository
import com.yuncun.noteapp.data.repository.RoomIdeaRepository
import com.yuncun.noteapp.data.repository.RoomEventPoolRepository
import com.yuncun.noteapp.data.repository.RoomScheduleRepository
import com.yuncun.noteapp.data.repository.RoomTimeRecordRepository
import com.yuncun.noteapp.data.repository.ScheduleRepository
import com.yuncun.noteapp.data.repository.TimeRecordRepository
import com.yuncun.noteapp.reminder.AndroidReminderAlarmGateway
import com.yuncun.noteapp.reminder.AndroidReminderNotificationGateway
import com.yuncun.noteapp.reminder.AndroidReminderPermissionReader
import com.yuncun.noteapp.reminder.DefaultReminderCoordinator
import com.yuncun.noteapp.reminder.ReminderCoordinator
import com.yuncun.noteapp.reminder.SharedPreferencesReminderRegistry
import com.yuncun.noteapp.pomodoro.AndroidPomodoroAlarmGateway
import com.yuncun.noteapp.pomodoro.AndroidPomodoroNotificationGateway
import com.yuncun.noteapp.pomodoro.DefaultPomodoroCoordinator
import com.yuncun.noteapp.pomodoro.PomodoroCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局 Application 类，管理应用全局生命周期与依赖初始化
 */
class NoteApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: NoteDatabase
        private set

    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    lateinit var ideaRepository: IdeaRepository
        private set

    lateinit var scheduleRepository: ScheduleRepository
        private set

    lateinit var eventPoolRepository: EventPoolRepository
        private set

    lateinit var timeRecordRepository: TimeRecordRepository
        private set

    lateinit var reminderCoordinator: ReminderCoordinator
        private set

    lateinit var pomodoroCoordinator: PomodoroCoordinator
        private set

    lateinit var backupOperations: BackupOperations
        private set

    override fun onCreate() {
        super.onCreate()
        // 初始化进程级本地唯一事实来源，页面后续通过 Application 取得同一实例。
        database = NoteDatabase.getInstance(this)
        preferencesRepository = AppPreferencesRepository(appPreferencesDataStore)
        ideaRepository = RoomIdeaRepository(database.ideaDao())
        eventPoolRepository = RoomEventPoolRepository(database.eventPoolItemDao())
        timeRecordRepository = RoomTimeRecordRepository(database.timeRecordDao())
        scheduleRepository = RoomScheduleRepository(
            database.academicTermDao(),
            database.scheduleTaskDao(),
            database.courseScheduleDao()
        )
        reminderCoordinator = DefaultReminderCoordinator(
            repository = scheduleRepository,
            permissionReader = AndroidReminderPermissionReader(this),
            alarmGateway = AndroidReminderAlarmGateway(this),
            notificationGateway = AndroidReminderNotificationGateway(this),
            registry = SharedPreferencesReminderRegistry(this)
        )
        pomodoroCoordinator = DefaultPomodoroCoordinator(
            store = preferencesRepository,
            alarmGateway = AndroidPomodoroAlarmGateway(this),
            notificationGateway = AndroidPomodoroNotificationGateway(this)
        )
        backupOperations = BackupService(
            dataGateway = RoomBackupDataGateway(database),
            preferencesStore = preferencesRepository,
            reminderCoordinator = reminderCoordinator
        )
        // 每次进程启动都以数据库、活动会话与当前系统权限为事实重建提醒。
        synchronizeReminders()
    }

    fun synchronizeReminders(onComplete: () -> Unit = {}) {
        applicationScope.launch {
            try {
                reminderCoordinator.synchronize()
                pomodoroCoordinator.synchronize()
            } finally {
                onComplete()
            }
        }
    }
}
