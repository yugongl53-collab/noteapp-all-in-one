package com.yuncun.noteapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.PomodoroScreen
import com.yuncun.noteapp.ui.screens.ScheduleScreen
import com.yuncun.noteapp.ui.screens.SettingsScreen
import com.yuncun.noteapp.ui.screens.StatisticsScreen

/**
 * 主界面脚手架与顶层底栏导航容器
 */
@Composable
fun NoteNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    // 弹出到导航栈起始位置，避免底栏堆积过多回退状态
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Idea.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Idea.route) {
                IdeaScreen()
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen()
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen()
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
