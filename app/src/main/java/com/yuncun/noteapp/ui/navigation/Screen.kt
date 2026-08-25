package com.yuncun.noteapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 顶层导航路由定义与底栏图标配置
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "今日", Icons.Default.Today)
    object Schedule : Screen("schedule", "日程", Icons.Default.DateRange)
    object Idea : Screen("ideas", "灵感", Icons.Default.Lightbulb)
    object Pomodoro : Screen("pomodoro", "番茄钟", Icons.Default.Timer)
    object Statistics : Screen("statistics", "统计", Icons.Default.QueryStats)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Today, Schedule, Idea, Statistics)
    }
}

/** M2 二级页面不占用底栏位置，均从今日页进入。 */
object IdeaRoutes {
    const val TRASH = "ideas/trash"
    const val EDIT_PATTERN = "ideas/edit/{ideaId}"
    const val NEW_ID = "new"

    fun edit(ideaId: String = NEW_ID): String = "ideas/edit/$ideaId"
}

/** M5 二级页按入口选择初始分区，页内切换不再增加导航栈。 */
object PomodoroRoutes {
    const val PATTERN = "pomodoro/{section}"
    fun timer(): String = "pomodoro/timer"
    fun pool(): String = "pomodoro/pool"
}
