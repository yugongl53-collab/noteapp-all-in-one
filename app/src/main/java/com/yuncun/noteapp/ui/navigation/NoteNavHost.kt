package com.yuncun.noteapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuncun.noteapp.NoteApp
import com.yuncun.noteapp.ui.idea.IdeaViewModel
import com.yuncun.noteapp.ui.schedule.ScheduleViewModel
import com.yuncun.noteapp.ui.screens.IdeaEditScreen
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.IdeaTrashScreen
import com.yuncun.noteapp.ui.screens.PomodoroScreen
import com.yuncun.noteapp.ui.screens.ScheduleScreen
import com.yuncun.noteapp.ui.screens.SettingsScreen
import com.yuncun.noteapp.ui.screens.StatisticsScreen
import com.yuncun.noteapp.ui.screens.TodayScreen

/**
 * 主界面脚手架与顶层底栏导航容器
 */
@Composable
fun NoteNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val application = LocalContext.current.applicationContext as NoteApp
    val ideaFactory = remember(application) { IdeaViewModel.Factory(application.ideaRepository) }
    val ideaViewModel: IdeaViewModel = viewModel(factory = ideaFactory)
    val ideaState by ideaViewModel.uiState.collectAsState()
    val quickDraft by ideaViewModel.quickDraft.collectAsState()
    val editorDraft by ideaViewModel.editorDraft.collectAsState()
    val scheduleFactory = remember(application) {
        ScheduleViewModel.Factory(application.scheduleRepository, application.reminderCoordinator)
    }
    val scheduleViewModel: ScheduleViewModel = viewModel(factory = scheduleFactory)
    val scheduleState by scheduleViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ideaState.feedback) {
        ideaState.feedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            ideaViewModel.consumeFeedback()
        }
    }
    LaunchedEffect(scheduleState.feedback) {
        scheduleState.feedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            scheduleViewModel.consumeFeedback()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (Screen.bottomNavItems.any { it.route == currentRoute }) {
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    draft = quickDraft,
                    onContentChange = ideaViewModel::updateQuickContent,
                    onTagsChange = ideaViewModel::updateQuickTags,
                    onSave = ideaViewModel::saveQuickIdea,
                    onOpenIdeas = { navController.navigate(Screen.Idea.route) }
                )
            }
            composable(Screen.Idea.route) {
                IdeaScreen(
                    state = ideaState,
                    onBack = navController::popBackStack,
                    onAdd = { navController.navigate(IdeaRoutes.edit()) },
                    onEdit = { navController.navigate(IdeaRoutes.edit(it)) },
                    onOpenTrash = { navController.navigate(IdeaRoutes.TRASH) }
                )
            }
            composable(
                route = IdeaRoutes.EDIT_PATTERN,
                arguments = listOf(navArgument("ideaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val ideaId = backStackEntry.arguments?.getString("ideaId")
                    ?.takeUnless { it == IdeaRoutes.NEW_ID }
                IdeaEditScreen(
                    draft = editorDraft,
                    state = ideaState,
                    onPrepare = { ideaViewModel.prepareEditor(ideaId) },
                    onContentChange = ideaViewModel::updateEditorContent,
                    onTagsChange = ideaViewModel::updateEditorTags,
                    onSave = ideaViewModel::saveEditor,
                    onMoveToTrash = ideaViewModel::moveToTrash,
                    onBack = navController::popBackStack
                )
            }
            composable(IdeaRoutes.TRASH) {
                IdeaTrashScreen(
                    state = ideaState,
                    onBack = navController::popBackStack,
                    onRestore = ideaViewModel::restore,
                    onPermanentlyDelete = ideaViewModel::permanentlyDelete
                )
            }
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    state = scheduleState,
                    onSelectView = scheduleViewModel::selectView,
                    onPreviousWeek = scheduleViewModel::previousWeek,
                    onNextWeek = scheduleViewModel::nextWeek,
                    onCurrentWeek = scheduleViewModel::currentWeek,
                    onSaveTerm = scheduleViewModel::saveTerm,
                    onDeleteTerm = scheduleViewModel::deleteTerm,
                    onSaveTask = scheduleViewModel::saveTask,
                    onDeleteTask = scheduleViewModel::deleteTask,
                    onSaveCourse = scheduleViewModel::saveCourse,
                    onDeleteCourse = scheduleViewModel::deleteCourse,
                    onConfirmOverlap = scheduleViewModel::confirmOverlapSave,
                    onCancelOverlap = scheduleViewModel::cancelOverlapSave
                )
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
