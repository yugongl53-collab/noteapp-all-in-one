package com.yuncun.noteapp.settlement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant

/** 自动结算防重注册表，基于 SharedPreferences 保存已结算的日程实例标识。 */
class SharedPreferencesSettlementRegistry(context: Context) : SettlementRegistry {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): Set<String> =
        preferences.getStringSet(KEY_SETTLED, emptySet()).orEmpty().toSet()

    override fun addSettled(keys: Set<String>) {
        val current = read()
        preferences.edit()
            .putStringSet(KEY_SETTLED, current + keys)
            .apply()
    }

    override fun replace(keys: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_SETTLED, keys.toSet())
            .apply()
    }

    private companion object {
        const val FILE_NAME = "schedule_settlement_registry"
        const val KEY_SETTLED = "settled_instance_keys"
    }
}

/** 自动结算 AlarmManager 网关，用于在下一次日程结束时唤醒执行结算。 */
class AndroidSettlementAlarmGateway(private val context: Context) : SettlementAlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleNextSettlement(triggerAt: Instant) {
        val intent = baseIntent()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                pendingIntent
            )
        }
    }

    override fun cancel() {
        val intent = baseIntent()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun baseIntent() = Intent(context, ScheduleAutoSettlementAlarmReceiver::class.java).apply {
        action = ACTION_SETTLE_SCHEDULE
        data = Uri.Builder()
            .scheme("noteapp")
            .authority("schedule-settlement")
            .build()
    }

    companion object {
        const val ACTION_SETTLE_SCHEDULE = "com.yuncun.noteapp.action.SETTLE_SCHEDULE"
        private const val REQUEST_CODE = 9001
    }
}
