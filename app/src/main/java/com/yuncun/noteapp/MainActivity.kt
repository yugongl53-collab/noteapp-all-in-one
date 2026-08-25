package com.yuncun.noteapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            NoteAppTheme {
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
