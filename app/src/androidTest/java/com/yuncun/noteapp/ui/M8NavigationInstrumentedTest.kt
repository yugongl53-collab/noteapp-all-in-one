package com.yuncun.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yuncun.noteapp.MainActivity
import org.junit.Rule
import org.junit.Test

/** 验证四大主Tab（日程、灵感、工具箱、设置）底栏导航与状态切换。 */
class M8NavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigation_switchesAcrossFourMainTabs() {
        // 默认启动进入「日程」
        composeRule.onNodeWithText("日程").assertIsDisplayed()
        composeRule.onNodeWithText("课表").assertIsDisplayed()

        // 切换到「灵感」
        composeRule.onNodeWithText("灵感").performClick()
        composeRule.onNodeWithText("快速记录灵感").assertIsDisplayed()

        // 切换到「工具箱」
        composeRule.onNodeWithText("工具箱").performClick()
        composeRule.onNodeWithText("幸运大转盘").assertIsDisplayed()
        composeRule.onNodeWithText("番茄钟").assertIsDisplayed()

        // 切换到「设置」
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("导出 JSON 备份").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("外观与主题").performScrollTo().assertIsDisplayed()

        // 再次切回「日程」
        composeRule.onNodeWithText("日程").performClick()
        composeRule.onNodeWithText("课表").assertIsDisplayed()
    }
}
