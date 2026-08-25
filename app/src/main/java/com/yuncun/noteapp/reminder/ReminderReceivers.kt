package com.yuncun.noteapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuncun.noteapp.NoteApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 精确闹钟触发后交给协调器送达并补充同一规则的下一实例。 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val candidate = AndroidReminderAlarmGateway.candidateFrom(intent) ?: return
        val pendingResult = goAsync()
        val application = context.applicationContext as NoteApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.reminderCoordinator.handleTriggered(candidate)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 开机、系统时间或时区变化后按 Room 当前事实重建全部下一实例。 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val application = context.applicationContext as NoteApp
        application.synchronizeReminders { pendingResult.finish() }
    }
}
