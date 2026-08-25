package com.yuncun.noteapp.pomodoro

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** 验证系统闹钟 Intent 必须同时匹配 data URI、会话标识和有效截止时间。 */
@RunWith(AndroidJUnit4::class)
class AndroidPomodoroAdaptersInstrumentedTest {
    @Test
    fun alarmFrom_acceptsCompleteMatchingIntentAndRejectsBrokenIntent() {
        val target = Instant.parse("2026-08-25T00:25:00Z")
        val valid = Intent().apply {
            data = Uri.parse("noteapp://pomodoro/session")
            putExtra("pomodoro_session_id", "session")
            putExtra("pomodoro_target_end_at", target.toEpochMilli())
        }

        assertEquals("session" to target, AndroidPomodoroAlarmGateway.alarmFrom(valid))
        assertNull(AndroidPomodoroAlarmGateway.alarmFrom(valid.apply { data = Uri.parse("noteapp://pomodoro/other") }))
        assertNull(AndroidPomodoroAlarmGateway.alarmFrom(Intent()))
    }
}
