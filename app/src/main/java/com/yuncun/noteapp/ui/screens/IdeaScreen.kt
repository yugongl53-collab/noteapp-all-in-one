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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import com.yuncun.noteapp.ui.idea.IdeaUiState

/** 灵感主列表呈现顶部轻量回收站入口、加载状态、空数据和按最后更新时间倒序的灵感卡片列表。 */
@Composable
fun IdeaScreen(
    state: IdeaUiState,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新增灵感")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 顶部轻量操作栏（右上角放置回收站入口，不展示多余页面标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = if (onBack != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "回收站")
                }
            }

            IdeaListContent(
                state = state,
                onEdit = onEdit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun IdeaListContent(
    state: IdeaUiState,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        Text("暂无灵感记录，点击右下角「+」开始记录吧")
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
