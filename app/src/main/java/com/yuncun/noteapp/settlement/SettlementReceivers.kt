package com.yuncun.noteapp.settlement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuncun.noteapp.NoteApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 日程到达结束时刻后接收系统定时闹钟唤醒，执行自动结算并调度下一轮时刻。 */
class ScheduleAutoSettlementAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val application = context.applicationContext as NoteApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.scheduleSettlementCoordinator.synchronize()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
