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

/** 验证 M8 跨页面入口使用真实 Activity 导航，确保备份能力不是孤立页面。 */
class M8NavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todaySettingsEntry_opensBackupSettingsAndCanReturn() {
        composeRule.onNodeWithText("打开设置").performScrollTo().performClick()

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("导出 JSON 备份").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        // 返回后页面保留之前的滚动位置，以底栏选中页标题确认已经回到今日路由。
        composeRule.onNodeWithText("今日").assertIsDisplayed()
    }
}
