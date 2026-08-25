package com.yuncun.noteapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TimeRange
import java.time.Instant

/** 实际时间记录保存名称与性质快照，关联来源删除时仅清空可选外键。 */
@Entity(
    tableName = "time_records",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedTaskId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventPoolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedPoolItemId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("startAt", "endAt"), Index("relatedTaskId"), Index("relatedPoolItemId")]
)
data class TimeRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: EventCategory,
    val startAt: Instant,
    val endAt: Instant,
    val source: String,
    val relatedTaskId: String?,
    val relatedPoolItemId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toRange() = TimeRange(id, startAt, endAt)
}
