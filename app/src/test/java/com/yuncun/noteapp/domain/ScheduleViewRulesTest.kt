package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.ScheduleInstance
import com.yuncun.noteapp.domain.model.ScheduleSource
import com.yuncun.noteapp.domain.rules.ScheduleViewRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证事件流状态、重叠提示与历史名称候选的稳定业务口径。 */
class ScheduleViewRulesTest {
    private val now = Instant.parse("2026-08-25T02:00:00Z")

    @Test
    fun eventStream_filtersEndedAndMarksAllTiedNextInstances() {
        val ended = instance("ended", "已结束", "2026-08-25T00:00:00Z", "2026-08-25T01:00:00Z")
        val active = instance("active", "进行中", "2026-08-25T01:30:00Z", "2026-08-25T02:30:00Z")
        val nextA = instance("a", "下一项甲", "2026-08-25T03:00:00Z", "2026-08-25T04:00:00Z")
        val nextB = instance("b", "下一项乙", "2026-08-25T03:00:00Z", "2026-08-25T03:30:00Z")

        val cards = ScheduleViewRules.eventStream(listOf(nextA, ended, active, nextB), now)

        assertEquals(listOf("active", "b", "a"), cards.map { it.instance.sourceId })
        assertTrue(cards.first().isOngoing)
        assertFalse(cards.first().isNext)
        assertEquals(listOf(true, true), cards.drop(1).map { it.isNext })
    }

    @Test
    fun overlappingIds_returnsEveryVisibleInstanceInTheOverlapGroup() {
        val first = instance("first", "甲", "2026-08-25T01:00:00Z", "2026-08-25T02:00:00Z")
        val second = instance("second", "乙", "2026-08-25T01:30:00Z", "2026-08-25T03:00:00Z")
        val adjacent = instance("adjacent", "丙", "2026-08-25T03:00:00Z", "2026-08-25T04:00:00Z")

        assertEquals(setOf("first", "second"), ScheduleViewRules.overlappingIds(listOf(first, second, adjacent)))
    }

    @Test
    fun distinctRecentNames_trimsDeduplicatesAndKeepsLatestOccurrence() {
        val names = ScheduleViewRules.distinctRecentNames(
            listOf(
                "旧名称" to Instant.parse("2026-08-20T00:00:00Z"),
                "  重复名称  " to Instant.parse("2026-08-22T00:00:00Z"),
                "重复名称" to Instant.parse("2026-08-24T00:00:00Z"),
                "最新名称" to Instant.parse("2026-08-25T00:00:00Z")
            )
        )

        assertEquals(listOf("最新名称", "重复名称", "旧名称"), names)
    }

    private fun instance(id: String, title: String, start: String, end: String) = ScheduleInstance(
        sourceId = id,
        source = ScheduleSource.TASK,
        title = title,
        category = EventCategory.WORK,
        startAt = Instant.parse(start),
        endAt = Instant.parse(end)
    )
}
