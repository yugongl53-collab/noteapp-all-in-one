package com.yuncun.noteapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 基础 Android 仪器测试，验证设备/模拟器环境下的 Context 与应用包名
 */
@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.yuncun.noteapp.debug", appContext.packageName)
    }
}
