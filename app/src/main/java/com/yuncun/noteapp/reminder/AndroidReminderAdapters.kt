package com.yuncun.noteapp.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yuncun.noteapp.MainActivity
import com.yuncun.noteapp.domain.model.ReminderCandidate
import com.yuncun.noteapp.domain.model.ScheduleSource
import java.time.Instant

/** 每次读取 Android 当前授权事实，绝不把权限状态持久化为业务设置。 */
class AndroidReminderPermissionReader(private val context: Context) : ReminderPermissionReader {
    override fun read(): ReminderPermissionState {
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return ReminderPermissionState(
            notificationGranted = notificationGranted,
            exactAlarmGranted = alarmManager.canScheduleExactAlarms()
        )
    }
}

/** 用稳定 data URI 标识每个实例，使数据修改后可以精确取消旧 PendingIntent。 */
class AndroidReminderAlarmGateway(private val context: Context) : ReminderAlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(candidate: ReminderCandidate) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            candidate.remindAt.toEpochMilli(),
            pendingIntent(candidate, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    override fun cancel(reminderId: String) {
        val intent = baseIntent(reminderId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(candidate: ReminderCandidate, flags: Int): PendingIntent {
        val intent = baseIntent(candidate.id).apply {
            putExtra(EXTRA_REMINDER_ID, candidate.id)
            putExtra(EXTRA_SOURCE, candidate.source.name)
            putExtra(EXTRA_SOURCE_ID, candidate.sourceId)
            putExtra(EXTRA_TITLE, candidate.title)
            putExtra(EXTRA_LOCATION, candidate.location)
            putExtra(EXTRA_START_AT, candidate.startAt.toEpochMilli())
            putExtra(EXTRA_REMIND_AT, candidate.remindAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context,
            candidate.id.hashCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun baseIntent(reminderId: String) = Intent(context, ReminderAlarmReceiver::class.java).apply {
        action = ACTION_DELIVER_REMINDER
        data = Uri.Builder()
            .scheme("noteapp")
            .authority("schedule-reminder")
            .appendPath(reminderId)
            .build()
    }

    companion object {
        private const val ACTION_DELIVER_REMINDER = "com.yuncun.noteapp.action.DELIVER_REMINDER"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_START_AT = "start_at"
        private const val EXTRA_REMIND_AT = "remind_at"

        /** 接收器对缺失或损坏的系统 Intent 安全忽略，不构造半完整提醒。 */
        fun candidateFrom(intent: Intent): ReminderCandidate? = runCatching {
            ReminderCandidate(
                source = ScheduleSource.valueOf(requireNotNull(intent.getStringExtra(EXTRA_SOURCE))),
                sourceId = requireNotNull(intent.getStringExtra(EXTRA_SOURCE_ID)),
                title = requireNotNull(intent.getStringExtra(EXTRA_TITLE)),
                location = intent.getStringExtra(EXTRA_LOCATION),
                startAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START_AT, Long.MIN_VALUE)),
                remindAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_REMIND_AT, Long.MIN_VALUE))
            ).takeIf { it.id == intent.getStringExtra(EXTRA_REMINDER_ID) }
        }.getOrNull()
    }
}

/** 通知渠道只在首次实际发送时创建，内容不暴露除标题和可选地点外的数据。 */
class AndroidReminderNotificationGateway(private val context: Context) : ReminderNotificationGateway {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun show(candidate: ReminderCandidate) {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val locationSuffix = candidate.location?.let { " · $it" }.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${candidate.title}即将开始")
            .setContentText("请按计划开始$locationSuffix")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(candidate.id.hashCode(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "日程提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程和普通事件开始前的本地提醒"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "schedule_reminders"
    }
}

/** 注册表只保存系统闹钟标识与去重标识，不属于可导出的业务数据。 */
class SharedPreferencesReminderRegistry(context: Context) : ReminderRegistry {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read() = ReminderRegistrySnapshot(
        scheduledIds = preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().toSet(),
        deliveredIds = preferences.getStringSet(KEY_DELIVERED, emptySet()).orEmpty().toSet()
    )

    override fun replace(snapshot: ReminderRegistrySnapshot) {
        preferences.edit()
            .putStringSet(KEY_SCHEDULED, snapshot.scheduledIds.toSet())
            .putStringSet(KEY_DELIVERED, snapshot.deliveredIds.toSet())
            .apply()
    }

    private companion object {
        const val FILE_NAME = "reminder_registry"
        const val KEY_SCHEDULED = "scheduled_ids"
        const val KEY_DELIVERED = "delivered_ids"
    }
}
