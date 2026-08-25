package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.domain.rules.IdeaRules
import com.yuncun.noteapp.ui.idea.IdeaUiState
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** 回收站显示剩余保留时间，并将不可恢复的永久删除放在明确确认之后。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaTrashScreen(
    state: IdeaUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentlyDelete: (String) -> Unit,
    now: Instant = Instant.now(),
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<IdeaEntity?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("灵感回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading && state.recycledIdeas.isEmpty() -> Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text("正在清理并加载回收站…", modifier = Modifier.padding(top = 12.dp))
            }

            state.recycledIdeas.isEmpty() -> Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("回收站是空的")
                Text("移入的灵感会在这里保留 30×24 小时")
            }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.recycledIdeas, key = IdeaEntity::id) { idea ->
                    RecycledIdeaCard(
                        idea = idea,
                        now = now,
                        operationEnabled = !state.operationInProgress,
                        onRestore = { onRestore(idea.id) },
                        onDelete = { pendingDelete = idea }
                    )
                }
            }
        }
    }

    pendingDelete?.let { idea ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("永久删除灵感？") },
            text = { Text("永久删除后无法恢复，请确认这是你想要的操作。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onPermanentlyDelete(idea.id)
                }) { Text("确认永久删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun RecycledIdeaCard(
    idea: IdeaEntity,
    now: Instant,
    operationEnabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(idea.content, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
            Text(
                text = remainingRetentionText(idea, now),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRestore, enabled = operationEnabled) { Text("恢复") }
                TextButton(onClick = onDelete, enabled = operationEnabled) { Text("永久删除") }
            }
        }
    }
}

/** 页面只显示未到期记录；小时向上取整，避免剩余数分钟时误显示 0 小时。 */
private fun remainingRetentionText(idea: IdeaEntity, now: Instant): String {
    val deletedAt = idea.deletedAt ?: return "删除时间未知"
    val remainingSeconds = max(0, Duration.between(now, deletedAt.plus(IdeaRules.retention)).seconds)
    if (remainingSeconds == 0L) return "即将自动永久删除"
    val totalHours = (remainingSeconds + 3599) / 3600
    val days = totalHours / 24
    val hours = totalHours % 24
    return if (days > 0) "剩余 $days 天 $hours 小时" else "剩余 $hours 小时"
}
