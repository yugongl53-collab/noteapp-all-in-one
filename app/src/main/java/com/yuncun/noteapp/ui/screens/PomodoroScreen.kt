package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 事件池与番茄钟专注占位组件（M5 里程碑完整实现）
 */
@Composable
fun PomodoroScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "番茄钟与事件池页面（待接入 M5 领域逻辑）")
    }
}
