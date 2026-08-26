package com.yuncun.noteapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yuncun.noteapp.domain.model.AppThemeMode
import com.yuncun.noteapp.domain.model.AppSettings
import com.yuncun.noteapp.ui.navigation.NoteNavHost
import com.yuncun.noteapp.ui.theme.NoteAppTheme

/**
 * 应用主 Activity，作为 Compose 界面渲染容器
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用沉浸式边缘到边缘显示
        enableEdgeToEdge()
        val app = application as NoteApp
        setContent {
            val settings by app.preferencesRepository.settings.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            NoteAppTheme(darkTheme = darkTheme) {
                NoteNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回或权限被撤销后，立即刷新事实并重建有效提醒。
        (application as NoteApp).run {
            reminderCoordinator.refreshPermissionState()
            synchronizeReminders()
        }
    }
}
