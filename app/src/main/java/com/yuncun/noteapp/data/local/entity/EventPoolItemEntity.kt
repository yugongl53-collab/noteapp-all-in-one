package com.yuncun.noteapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuncun.noteapp.domain.model.EventCategory
import java.time.Instant

/** 事件池项目的启用状态和权重共同决定后续加权抽取候选集合。 */
@Entity(tableName = "event_pool_items", indices = [Index("isEnabled"), Index("updatedAt")])
data class EventPoolItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: EventCategory,
    val isEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    @ColumnInfo(defaultValue = "1")
    val weight: Int = 1
)
