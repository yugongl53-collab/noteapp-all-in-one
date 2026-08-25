package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.rules.TimeRange
import com.yuncun.noteapp.domain.rules.TimeRecordRules
import java.time.Instant
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
}
