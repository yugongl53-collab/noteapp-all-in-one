package com.yuncun.noteapp.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.yuncun.noteapp.ui.pomodoro.PomodoroViewModel
import com.yuncun.noteapp.ui.schedule.ScheduleViewModel
import com.yuncun.noteapp.ui.statistics.StatisticsViewModel
import com.yuncun.noteapp.ui.screens.IdeaEditScreen
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.IdeaTrashScreen
import com.yuncun.noteapp.ui.screens.PomodoroScreen
import com.yuncun.noteapp.ui.screens.PomodoroCompactBar
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
    val pomodoroFactory = remember(application) {
        PomodoroViewModel.Factory(application.eventPoolRepository, application.pomodoroCoordinator)
    }
    val pomodoroViewModel: PomodoroViewModel = viewModel(factory = pomodoroFactory)
    val pomodoroState by pomodoroViewModel.uiState.collectAsState()
    val statisticsFactory = remember(application) {
        StatisticsViewModel.Factory(application.timeRecordRepository)
    }
    val statisticsViewModel: StatisticsViewModel = viewModel(factory = statisticsFactory)
    val statisticsState by statisticsViewModel.uiState.collectAsState()
    val timeRecordDraft by statisticsViewModel.draft.collectAsState()
    val reminderPermissions by application.reminderCoordinator.permissionState.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        application.synchronizeReminders()
    }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        application.synchronizeReminders()
    }
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
    LaunchedEffect(pomodoroState.feedback) {
        pomodoroState.feedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            pomodoroViewModel.consumeFeedback()
        }
    }
    LaunchedEffect(statisticsState.feedback) {
        statisticsState.feedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            statisticsViewModel.consumeFeedback()
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
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    draft = quickDraft,
                    onContentChange = ideaViewModel::updateQuickContent,
                    onTagsChange = ideaViewModel::updateQuickTags,
                    onSave = ideaViewModel::saveQuickIdea,
                    onOpenIdeas = { navController.navigate(Screen.Idea.route) },
                    onOpenEventPool = { navController.navigate(PomodoroRoutes.pool()) },
                    onOpenPomodoro = { navController.navigate(PomodoroRoutes.timer()) }
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
                    onCancelOverlap = scheduleViewModel::cancelOverlapSave,
                    reminderPermissions = reminderPermissions,
                    onOpenReminderSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(
                route = PomodoroRoutes.PATTERN,
                arguments = listOf(navArgument("section") { type = NavType.StringType })
            ) { backStackEntry ->
                PomodoroScreen(
                    state = pomodoroState,
                    initialSection = backStackEntry.arguments?.getString("section") ?: "timer",
                    notificationGranted = reminderPermissions.notificationGranted,
                    onBack = navController::popBackStack,
                    onSavePoolItem = pomodoroViewModel::savePoolItem,
                    onSetPoolItemEnabled = pomodoroViewModel::setPoolItemEnabled,
                    onDeletePoolItem = pomodoroViewModel::deletePoolItem,
                    onDraw = pomodoroViewModel::draw,
                    onStart = pomodoroViewModel::startPomodoro,
                    onPause = pomodoroViewModel::pause,
                    onResume = pomodoroViewModel::resume,
                    onReset = pomodoroViewModel::reset,
                    onFinishEarly = pomodoroViewModel::finishEarly,
                    onStartRest = pomodoroViewModel::startRest,
                    onClearSession = pomodoroViewModel::clearSession,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    state = statisticsState,
                    draft = timeRecordDraft,
                    onSelectPeriod = statisticsViewModel::selectPeriod,
                    onSelectRanking = statisticsViewModel::selectRanking,
                    onPreviousPeriod = statisticsViewModel::previousPeriod,
                    onNextPeriod = statisticsViewModel::nextPeriod,
                    onCurrentPeriod = statisticsViewModel::currentPeriod,
                    onRetry = statisticsViewModel::refresh,
                    onAddRecord = statisticsViewModel::prepareNewRecord,
                    onEditRecord = statisticsViewModel::prepareEditRecord,
                    onDeleteRecord = statisticsViewModel::deleteRecord,
                    onUpdateTitle = statisticsViewModel::updateTitle,
                    onUpdateCategory = statisticsViewModel::updateCategory,
                    onUpdateStartDate = statisticsViewModel::updateStartDate,
                    onUpdateStartTime = statisticsViewModel::updateStartTime,
                    onUpdateEndDate = statisticsViewModel::updateEndDate,
                    onUpdateEndTime = statisticsViewModel::updateEndTime,
                    onSaveDraft = statisticsViewModel::saveDraft,
                    onDismissEditor = statisticsViewModel::dismissEditor
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    reminderPermissions = reminderPermissions,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        exactAlarmSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${application.packageName}")
                            )
                        )
                    }
                )
            }
        }
        pomodoroState.session?.let { session ->
            if (currentRoute != PomodoroRoutes.PATTERN) {
                PomodoroCompactBar(
                    session = session,
                    remainingSeconds = pomodoroState.remainingSeconds,
                    onClick = { navController.navigate(PomodoroRoutes.timer()) }
                )
            }
        }
        }
    }
}
