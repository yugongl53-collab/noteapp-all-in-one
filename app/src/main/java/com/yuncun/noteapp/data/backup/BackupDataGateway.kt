package com.yuncun.noteapp.data.backup

import androidx.room.withTransaction
import com.yuncun.noteapp.data.local.NoteDatabase
import com.yuncun.noteapp.domain.model.AppSettings
import java.time.Instant

/** 协调层通过该接口获得一致快照，并把数据库写入与设置回调纳入同一失败边界。 */
interface BackupDataGateway {
    suspend fun load(appSettings: AppSettings, expiredIdeaCutoff: Instant): BackupSnapshot

    suspend fun replace(
        snapshot: BackupSnapshot,
        expiredIdeaCutoff: Instant,
        updateSettings: suspend () -> Unit
    ): Int
}

/** Room 实现按外键顺序写入；任一 DAO 校验、约束或设置写入失败都会回滚数据库事务。 */
class RoomBackupDataGateway(private val database: NoteDatabase) : BackupDataGateway {
    override suspend fun load(appSettings: AppSettings, expiredIdeaCutoff: Instant): BackupSnapshot =
        database.withTransaction {
            database.ideaDao().deleteExpired(expiredIdeaCutoff)
            BackupSnapshot(
                ideas = database.ideaDao().getAll(),
                scheduleTasks = database.scheduleTaskDao().getAll(),
                academicTerms = database.academicTermDao().getAll(),
                courseSchedules = database.courseScheduleDao().getAll(),
                eventPoolItems = database.eventPoolItemDao().getAll(),
                timeRecords = database.timeRecordDao().getAll(),
                appSettings = appSettings
            )
        }

    override suspend fun replace(
        snapshot: BackupSnapshot,
        expiredIdeaCutoff: Instant,
        updateSettings: suspend () -> Unit
    ): Int = database.withTransaction {
        database.backupDao().clearAllBusinessData()
        snapshot.ideas.forEach { database.ideaDao().save(it) }
        snapshot.academicTerms.forEach { database.academicTermDao().save(it) }
        snapshot.scheduleTasks.forEach { database.scheduleTaskDao().save(it) }
        snapshot.eventPoolItems.forEach { database.eventPoolItemDao().save(it) }
        snapshot.courseSchedules.forEach { database.courseScheduleDao().save(it) }
        snapshot.timeRecords.forEach { database.timeRecordDao().save(it) }
        val expiredCount = database.ideaDao().deleteExpired(expiredIdeaCutoff)
        // 设置写入放在数据库提交之前，DataStore 失败时 Room 仍能完整回滚。
        updateSettings()
        expiredCount
    }
}
