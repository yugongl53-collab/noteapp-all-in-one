package com.yuncun.noteapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** 灵感支持回收站软删除，普通列表由 DAO 排除 deletedAt 非空记录。 */
@Entity(tableName = "ideas", indices = [Index("updatedAt"), Index("deletedAt")])
data class IdeaEntity(
    @PrimaryKey val id: String,
    val content: String,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
)
