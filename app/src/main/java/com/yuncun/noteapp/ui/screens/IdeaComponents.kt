package com.yuncun.noteapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuncun.noteapp.data.local.entity.IdeaEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ideaTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 灵感卡片完整展示正文摘要、标签和最后更新时间，点击进入编辑页。 */
@Composable
fun IdeaCard(idea: IdeaEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = idea.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (idea.tags.isNotEmpty()) {
                // 使用可换行文本展示全部标签，避免长标签或小屏幕发生横向溢出。
                Text(
                    text = idea.tags.joinToString(separator = " · ", prefix = "标签："),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "更新于 ${ideaTimeFormatter.format(idea.updatedAt.atZone(ZoneId.systemDefault()))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
