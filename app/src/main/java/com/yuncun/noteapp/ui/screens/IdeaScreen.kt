package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.ui.idea.IdeaUiState

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.yuncun.noteapp.ui.idea.IdeaDraftState

/** 灵感主列表呈现快速记录输入、加载状态、空数据和按最后更新时间倒序的真实 Room 数据。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaScreen(
    state: IdeaUiState,
    modifier: Modifier = Modifier,
    quickDraft: IdeaDraftState = IdeaDraftState(),
    onQuickContentChange: (String) -> Unit = {},
    onQuickTagsChange: (String) -> Unit = {},
    onQuickSave: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("灵感") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "回收站")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新增灵感")
            }
        }
    ) { innerPadding ->
        IdeaListContent(
            state = state,
            quickDraft = quickDraft,
            onQuickContentChange = onQuickContentChange,
            onQuickTagsChange = onQuickTagsChange,
            onQuickSave = onQuickSave,
            onEdit = onEdit,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun IdeaListContent(
    state: IdeaUiState,
    quickDraft: IdeaDraftState,
    onQuickContentChange: (String) -> Unit,
    onQuickTagsChange: (String) -> Unit,
    onQuickSave: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // 快速记录灵感卡片
            QuickIdeaCard(
                draft = quickDraft,
                onContentChange = onQuickContentChange,
                onTagsChange = onQuickTagsChange,
                onSave = onQuickSave
            )
        }

        when {
            state.isLoading && state.activeIdeas.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text("正在加载灵感…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            state.activeIdeas.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("还没有已保存的灵感，在上方输入或点击右下角添加吧")
                    }
                }
            }

            else -> {
                items(state.activeIdeas, key = IdeaEntity::id) { idea ->
                    IdeaCard(idea = idea, onClick = { onEdit(idea.id) })
                }
            }
        }
    }
}

/** 灵感速记卡片：支持正文与可选标签录入，点击保存后实时入库。 */
@Composable
fun QuickIdeaCard(
    draft: IdeaDraftState,
    onContentChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("快速记录灵感", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = draft.content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("现在想到了什么？") },
                minLines = 2,
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
        }
    }
}
