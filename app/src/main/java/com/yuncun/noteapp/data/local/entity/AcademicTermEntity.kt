package com.yuncun.noteapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.Instant
import java.time.LocalDate

/** 学年与季节唯一索引从存储层阻止两个同名学期。 */
@Entity(
    tableName = "academic_terms",
    indices = [
        Index(value = ["academicYearStart", "season"], unique = true),
        Index(value = ["startDate", "endDate"])
    ]
)
data class AcademicTermEntity(
    @PrimaryKey val id: String,
    val academicYearStart: Int,
    val season: TermSeason,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toPeriod() = TermPeriod(id, academicYearStart, season, startDate, endDate)
}
