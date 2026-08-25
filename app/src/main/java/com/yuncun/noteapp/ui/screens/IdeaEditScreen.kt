package com.yuncun.noteapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.ui.idea.IdeaDraftState
import com.yuncun.noteapp.ui.idea.IdeaUiState

/** 编辑页统一处理新增/修改，并在离开脏草稿或移入回收站前二次确认。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaEditScreen(
    draft: IdeaDraftState,
    state: IdeaUiState,
    onPrepare: () -> Unit,
    onContentChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onMoveToTrash: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val initialSaveVersion = remember { state.saveCompletedVersion }
    val initialDeleteVersion = remember { state.deleteCompletedVersion }

    LaunchedEffect(Unit) { onPrepare() }
    LaunchedEffect(state.saveCompletedVersion, state.deleteCompletedVersion) {
        if (state.saveCompletedVersion > initialSaveVersion || state.deleteCompletedVersion > initialDeleteVersion) {
            onBack()
        }
    }

    fun requestBack() {
        if (draft.isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = draft.isDirty) { showDiscardDialog = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == null) "新增灵感" else "编辑灵感") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = draft.content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("灵感正文") },
                isError = draft.contentError != null,
                supportingText = draft.contentError?.let { message -> { Text(message) } }
            )
            OutlinedTextField(
                value = draft.tagsInput,
                onValueChange = onTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签（可选）") },
                supportingText = { Text("使用逗号或换行分隔") },
                maxLines = 2
            )
            Button(
                onClick = onSave,
                enabled = !draft.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (draft.isSaving) "正在保存…" else "保存")
            }
            if (draft.id != null) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !state.operationInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("移入回收站", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存内容？") },
            text = { Text("当前修改尚未保存，离开后将丢失。") },
            confirmButton = { TextButton(onClick = onBack) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") } }
        )
    }
    if (showDeleteDialog && draft.id != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("移入回收站？") },
            text = { Text("该灵感将在回收站保留 30×24 小时，期间可以恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onMoveToTrash(draft.id)
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}
