package com.yuncun.noteapp.pomodoro

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yuncun.noteapp.MainActivity
import com.yuncun.noteapp.NoteApp
import com.yuncun.noteapp.domain.model.PomodoroPhase
import com.yuncun.noteapp.domain.model.PomodoroSession
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 番茄钟使用普通可唤醒闹钟，不把“闹钟和提醒”特殊权限变成专注前置条件。 */
class AndroidPomodoroAlarmGateway(private val context: Context) : PomodoroAlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(session: PomodoroSession) {
        val target = requireNotNull(session.targetEndAt) { "运行会话必须包含截止时间" }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            target.toEpochMilli(),
            pendingIntent(session.id, target, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    override fun cancel(sessionId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            baseIntent(sessionId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(sessionId: String, targetEndAt: Instant, flags: Int): PendingIntent {
        val intent = baseIntent(sessionId).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_TARGET_END_AT, targetEndAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun baseIntent(sessionId: String) = Intent(context, PomodoroAlarmReceiver::class.java).apply {
        action = ACTION_COMPLETE_POMODORO
        data = Uri.Builder().scheme("noteapp").authority("pomodoro").appendPath(sessionId).build()
    }

    companion object {
        private const val ACTION_COMPLETE_POMODORO = "com.yuncun.noteapp.action.COMPLETE_POMODORO"
        private const val EXTRA_SESSION_ID = "pomodoro_session_id"
        private const val EXTRA_TARGET_END_AT = "pomodoro_target_end_at"

        /** 系统 Intent 缺字段或时间戳损坏时返回空，接收器不会修改当前会话。 */
        fun alarmFrom(intent: Intent): Pair<String, Instant>? = runCatching {
            val id = requireNotNull(intent.getStringExtra(EXTRA_SESSION_ID))
            require(intent.data?.lastPathSegment == id)
            val epochMillis = intent.getLongExtra(EXTRA_TARGET_END_AT, Long.MIN_VALUE)
            require(epochMillis != Long.MIN_VALUE)
            id to Instant.ofEpochMilli(epochMillis)
        }.getOrNull()
    }
}

/** 通知权限缺失时保留完成状态但不尝试伪造已送达，用户回到应用仍可看到结果。 */
class AndroidPomodoroNotificationGateway(private val context: Context) : PomodoroNotificationGateway {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun showCompleted(session: PomodoroSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel()
        val phaseName = if (session.phase == PomodoroPhase.FOCUS) "专注" else "休息"
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${phaseName}阶段已完成")
            .setContentText(session.title?.let { "$it · 返回应用确认下一步" } ?: "返回应用确认下一步")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(session.id.hashCode(), notification)
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "番茄钟阶段提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "专注或休息阶段结束时的本地提醒"
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "pomodoro_phase_reminders"
    }
}

/** 番茄钟系统闹钟只把稳定标识和原截止时间交给协调器二次校验。 */
class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (sessionId, targetEndAt) = AndroidPomodoroAlarmGateway.alarmFrom(intent) ?: return
        val pendingResult = goAsync()
        val application = context.applicationContext as NoteApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.pomodoroCoordinator.handleAlarm(sessionId, targetEndAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
