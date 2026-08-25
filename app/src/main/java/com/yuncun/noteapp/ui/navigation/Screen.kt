package com.yuncun.noteapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 顶层导航路由定义与底栏四大主Tab配置（日程、灵感、工具箱、设置）
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "日程", Icons.Default.DateRange)
    object Idea : Screen("ideas", "灵感", Icons.Default.Lightbulb)
    object Toolbox : Screen("toolbox", "工具箱", Icons.Default.Build)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Schedule, Idea, Toolbox, Settings)
    }
}

/** 灵感二级页面路由 */
object IdeaRoutes {
    const val TRASH = "ideas/trash"
    const val EDIT_PATTERN = "ideas/edit/{ideaId}"
    const val NEW_ID = "new"

    fun edit(ideaId: String = NEW_ID): String = "ideas/edit/$ideaId"
}

/** 工具箱（番茄钟与事件抽奖）二级页按入口选择初始分区 */
object PomodoroRoutes {
    const val PATTERN = "toolbox/{section}"
    fun timer(): String = "toolbox/timer"
    fun pool(): String = "toolbox/pool"
}
