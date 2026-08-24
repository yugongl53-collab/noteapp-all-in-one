package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 时间记录与日周统计排行榜占位组件（M6 里程碑完整实现）
 */
@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "时间统计页面（待接入 M6 领域逻辑）")
    }
}
