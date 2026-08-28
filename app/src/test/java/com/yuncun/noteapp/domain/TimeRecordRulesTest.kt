package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.rules.TimeRange
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import com.yuncun.noteapp.domain.rules.TimeRecordSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证时间记录范围与重叠判定边界。 */
class TimeRecordRulesTest {
    private fun instant(minute: Long): Instant = Instant.EPOCH.plusSeconds(minute * 60)

    @Test
    fun validateRange_rejectsZeroOrNegativeDuration() {
        assertTrue(runCatching { TimeRecordRules.validateRange(instant(10), instant(10)) }.isFailure)
        assertTrue(runCatching { TimeRecordRules.validateRange(instant(11), instant(10)) }.isFailure)
    }

    @Test
    fun overlaps_allowsAdjacentRangesAndRejectsSharedTime() {
        val existing = TimeRange("old", instant(10), instant(20))
        assertFalse(TimeRecordRules.hasOverlap(instant(20), instant(30), listOf(existing)))
        assertTrue(TimeRecordRules.hasOverlap(instant(19), instant(30), listOf(existing)))
    }

    @Test
    fun hasOverlap_excludesTheRecordBeingEdited() {
        val existing = listOf(TimeRange("self", instant(10), instant(20)))
        assertFalse(TimeRecordRules.hasOverlap(instant(11), instant(19), existing, excludedId = "self"))
    }

    @Test
    fun statistics_emptyDayReportsZeroRecordedMinutes() {
        val statistics = TimeRecordRules.calculateStatistics(
            emptyList(),
            LocalDate.parse("2026-08-25"),
            LocalDate.parse("2026-08-25"),
            ZoneId.of("Asia/Shanghai")
        )

        assertEquals(1440, statistics.totalMinutes)
        assertEquals(0, statistics.recordedMinutes)
        assertTrue(statistics.categoryRanking.isEmpty())
        assertTrue(statistics.titleRanking.isEmpty())
    }

    @Test
    fun statistics_clipsCrossMidnightRecordAndAggregatesWeekRankings() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val records = listOf(
            record("cross", " 阅读 ", EventCategory.STUDY, "2026-08-24T23:30:00+08:00", "2026-08-25T00:30:00+08:00"),
            record("work", "写作", EventCategory.WORK, "2026-08-25T09:00:00+08:00", "2026-08-25T11:00:00+08:00"),
            record("study", "阅读", EventCategory.STUDY, "2026-08-26T09:00:00+08:00", "2026-08-26T10:30:00+08:00")
        )

        val statistics = TimeRecordRules.calculateStatistics(
            records,
            LocalDate.parse("2026-08-24"),
            LocalDate.parse("2026-08-30"),
            zoneId,
            Locale.CHINA
        )

        assertEquals(150, statistics.categoryRanking[0].minutes)
        assertEquals(EventCategory.STUDY, statistics.categoryRanking[0].category)
        assertEquals(120, statistics.categoryRanking[1].minutes)
        assertEquals("阅读", statistics.titleRanking[0].title)
        assertEquals(150, statistics.titleRanking[0].minutes)
        assertEquals(30, statistics.dailySummaries[0].recordedMinutes)
        assertEquals(150, statistics.dailySummaries[1].recordedMinutes)
    }

    @Test
    fun statistics_categoryTiesUseFixedOrderAndTitleTiesUseLocaleOrder() {
        val statistics = TimeRecordRules.calculateStatistics(
            listOf(
                record("study", "乙", EventCategory.STUDY, "2026-08-25T09:00:00+08:00", "2026-08-25T10:00:00+08:00"),
                record("work", "甲", EventCategory.WORK, "2026-08-25T10:00:00+08:00", "2026-08-25T11:00:00+08:00")
            ),
            LocalDate.parse("2026-08-25"),
            LocalDate.parse("2026-08-25"),
            ZoneId.of("Asia/Shanghai"),
            Locale.CHINA
        )

        assertEquals(listOf(EventCategory.WORK, EventCategory.STUDY), statistics.categoryRanking.map { it.category })
        assertEquals(listOf("甲", "乙"), statistics.titleRanking.map { it.title })
        assertEquals(listOf(1, 2), statistics.titleRanking.map { it.rank })
    }

    @Test
    fun statistics_usesActualTwentyThreeAndTwentyFiveHourDayLengths() {
        val zoneId = ZoneId.of("America/New_York")

        val spring = TimeRecordRules.calculateStatistics(
            emptyList(),
            LocalDate.parse("2026-03-08"),
            LocalDate.parse("2026-03-08"),
            zoneId
        )
        val autumn = TimeRecordRules.calculateStatistics(
            emptyList(),
            LocalDate.parse("2026-11-01"),
            LocalDate.parse("2026-11-01"),
            zoneId
        )

        assertEquals(23 * 60L, spring.totalMinutes)
        assertEquals(25 * 60L, autumn.totalMinutes)
    }

    private fun record(
        id: String,
        title: String,
        category: EventCategory,
        start: String,
        end: String
    ) = TimeRecordSnapshot(id, title, category, Instant.parse(start), Instant.parse(end))
}
