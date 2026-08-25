package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.ui.idea.IdeaDraftState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val todayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 EEEE")

/** 今日页让快速输入始终位于首屏，其他里程碑入口继续保持真实占位说明。 */
@Composable
fun TodayScreen(
    draft: IdeaDraftState,
    onContentChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onOpenIdeas: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("今天", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = todayFormatter.format(LocalDate.now()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("快速记录灵感", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("现在想到了什么？") },
                    minLines = 3,
                    isError = draft.contentError != null,
                    supportingText = draft.contentError?.let { message -> { Text(message) } }
                )
                OutlinedTextField(
                    value = draft.tagsInput,
                    onValueChange = onTagsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签（可选）") },
                    supportingText = { Text("使用逗号或换行分隔，重复标签会自动合并") },
                    maxLines = 2
                )
                Button(
                    onClick = onSave,
                    enabled = !draft.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null)
                    Text(if (draft.isSaving) "正在保存…" else "保存灵感", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOpenIdeas, modifier = Modifier.fillMaxWidth()) {
                    Text("查看全部灵感")
                }
            }
        }
        Text(
            text = "今日计划、番茄钟和随机行动将在后续里程碑接入。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
