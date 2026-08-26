package com.yuncun.noteapp.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yuncun.noteapp.ui.backup.BackupViewModel
import com.yuncun.noteapp.ui.pomodoro.PomodoroViewModel
import com.yuncun.noteapp.ui.schedule.ScheduleViewModel
import com.yuncun.noteapp.ui.statistics.StatisticsViewModel
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yuncun.noteapp.ui.screens.IdeaEditScreen
import com.yuncun.noteapp.ui.screens.IdeaScreen
import com.yuncun.noteapp.ui.screens.IdeaTrashScreen
import com.yuncun.noteapp.ui.screens.PomodoroScreen
import com.yuncun.noteapp.ui.screens.PomodoroCompactBar
import com.yuncun.noteapp.ui.screens.ScheduleScreen
import com.yuncun.noteapp.ui.screens.SettingsScreen

/**
 * 主界面脚手架与顶层底栏导航容器（日程、灵感、工具箱、设置四大主Tab）
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
        ScheduleViewModel.Factory(
            application.scheduleRepository,
            application.reminderCoordinator,
            application.scheduleSettlementCoordinator
        )
    }
    val scheduleViewModel: ScheduleViewModel = viewModel(factory = scheduleFactory)
    val scheduleState by scheduleViewModel.uiState.collectAsState()
    val pomodoroFactory = remember(application) {
        PomodoroViewModel.Factory(application.eventPoolRepository, application.pomodoroCoordinator)
    }
    val pomodoroViewModel: PomodoroViewModel = viewModel(factory = pomodoroFactory)
    val pomodoroState by pomodoroViewModel.uiState.collectAsState()
    val statisticsFactory = remember(application) {
        StatisticsViewModel.Factory(
            application.timeRecordRepository,
            application.scheduleSettlementCoordinator
        )
    }
    val statisticsViewModel: StatisticsViewModel = viewModel(factory = statisticsFactory)
    val statisticsState by statisticsViewModel.uiState.collectAsState()
    val timeRecordDraft by statisticsViewModel.draft.collectAsState()
    val backupFactory = remember(application) { BackupViewModel.Factory(application.backupOperations) }
    val backupViewModel: BackupViewModel = viewModel(factory = backupFactory)
    val backupState by backupViewModel.uiState.collectAsState()
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
    val exportFileName = remember { "noteapp-backup-${LocalDateTime.now().format(BACKUP_FILE_TIME_FORMAT)}.json" }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            backupViewModel.export(application.displayName(it, exportFileName)) { content ->
                application.writeUtf8(it, content)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            backupViewModel.prepareImport(application.displayName(it, "所选备份.json")) {
                application.readUtf8(it)
            }
        }
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
    LaunchedEffect(backupState.feedback) {
        backupState.feedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            backupViewModel.consumeFeedback()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (Screen.bottomNavItems.any { it.route == currentRoute || (it == Screen.Toolbox && currentRoute?.startsWith("toolbox") == true) }) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route ||
                            (screen == Screen.Toolbox && currentRoute?.startsWith("toolbox") == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
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
                startDestination = Screen.Schedule.route,
                modifier = Modifier.weight(1f)
            ) {
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
                        onOpenReminderSettings = { navController.navigate(Screen.Settings.route) },
                        statisticsState = statisticsState,
                        statisticsDraft = timeRecordDraft,
                        onSelectPeriod = statisticsViewModel::selectPeriod,
                        onSelectRanking = statisticsViewModel::selectRanking,
                        onPreviousPeriod = statisticsViewModel::previousPeriod,
                        onNextPeriod = statisticsViewModel::nextPeriod,
                        onCurrentPeriod = statisticsViewModel::currentPeriod,
                        onRetryStatistics = statisticsViewModel::refresh,
                        onAddRecord = statisticsViewModel::prepareNewRecord,
                        onEditRecord = statisticsViewModel::prepareEditRecord,
                        onDeleteRecord = statisticsViewModel::deleteRecord,
                        onUpdateRecordTitle = statisticsViewModel::updateTitle,
                        onUpdateRecordCategory = statisticsViewModel::updateCategory,
                        onUpdateRecordStartDate = statisticsViewModel::updateStartDate,
                        onUpdateRecordStartTime = statisticsViewModel::updateStartTime,
                        onUpdateRecordEndDate = statisticsViewModel::updateEndDate,
                        onUpdateRecordEndTime = statisticsViewModel::updateEndTime,
                        onSaveRecordDraft = statisticsViewModel::saveDraft,
                        onDismissRecordEditor = statisticsViewModel::dismissEditor
                    )
                }
                composable(Screen.Idea.route) {
                    IdeaScreen(
                        state = ideaState,
                        quickDraft = quickDraft,
                        onQuickContentChange = ideaViewModel::updateQuickContent,
                        onQuickTagsChange = ideaViewModel::updateQuickTags,
                        onQuickSave = ideaViewModel::saveQuickIdea,
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
                composable(Screen.Toolbox.route) {
                    PomodoroScreen(
                        state = pomodoroState,
                        initialSection = "timer",
                        notificationGranted = reminderPermissions.notificationGranted,
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
                composable(
                    route = PomodoroRoutes.PATTERN,
                    arguments = listOf(navArgument("section") { type = NavType.StringType })
                ) { backStackEntry ->
                    PomodoroScreen(
                        state = pomodoroState,
                        initialSection = backStackEntry.arguments?.getString("section") ?: "timer",
                        notificationGranted = reminderPermissions.notificationGranted,
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
                composable(Screen.Settings.route) {
                    val appSettings by application.preferencesRepository.settings.collectAsState(
                        initial = com.yuncun.noteapp.domain.model.AppSettings()
                    )
                    val coroutineScope = rememberCoroutineScope()
                    SettingsScreen(
                        currentThemeMode = appSettings.themeMode,
                        onThemeModeChange = { mode ->
                            coroutineScope.launch {
                                application.preferencesRepository.setThemeMode(mode)
                            }
                        },
                        reminderPermissions = reminderPermissions,
                        backupState = backupState,
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
                        },
                        onRequestExport = backupViewModel::requestExport,
                        onDismissExportWarning = backupViewModel::dismissExportWarning,
                        onConfirmExportWarning = {
                            backupViewModel.confirmExportWarning { exportLauncher.launch(exportFileName) }
                        },
                        onChooseImportFile = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onDismissImportConfirmation = backupViewModel::dismissImportConfirmation,
                        onConfirmImport = {
                            backupViewModel.confirmImport {
                                // DAO 列表不是 Flow，导入成功后主动刷新全部受影响页面快照。
                                ideaViewModel.refresh()
                                scheduleViewModel.refresh()
                                pomodoroViewModel.refreshPool()
                                statisticsViewModel.refresh()
                            }
                        }
                    )
                }
            }
            pomodoroState.session?.let { session ->
                if (currentRoute != Screen.Toolbox.route && currentRoute != PomodoroRoutes.PATTERN) {
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

private val BACKUP_FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** 系统文档 URI 只通过 ContentResolver 读写，不申请宽泛存储权限。 */
private suspend fun android.content.Context.writeUtf8(uri: Uri, content: String) = withContext(Dispatchers.IO) {
    val stream = requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "无法打开所选保存位置" }
    stream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(content) }
}

private suspend fun android.content.Context.readUtf8(uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = requireNotNull(contentResolver.openInputStream(uri)) { "无法打开所选备份文件" }
    stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

/** OpenableColumns 不可用时回退到建议名称，反馈仍不泄露设备路径。 */
private fun android.content.Context.displayName(uri: Uri, fallback: String): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
