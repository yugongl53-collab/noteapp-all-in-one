package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.rules.EventPoolRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证加权抽取与转盘几何边界，确保算法结果和最终指针可使用同一候选顺序。 */
class EventPoolRulesTest {
    @Test
    fun draw_usesCumulativeWeightsAndFiltersDisabledItems() {
        val items = listOf(
            candidate("disabled", false, 100),
            candidate("first", true, 1),
            candidate("second", true, 3)
        )
        var bound = 0L

        val result = EventPoolRules.draw(items) { totalWeight -> bound = totalWeight; 3L }

        assertEquals(4L, bound)
        assertEquals("second", result?.id)
    }

    @Test
    fun draw_singleCandidateAlwaysWinsAcrossItsWholeWeightRange() {
        val result = EventPoolRules.draw(listOf(candidate("only", true, 100))) { 99L }

        assertEquals("only", result?.id)
    }

    @Test
    fun draw_equalWeightsMapsEachRandomUnitToOneCandidate() {
        val items = listOf(candidate("first", true, 1), candidate("second", true, 1))

        assertEquals("first", EventPoolRules.draw(items) { 0L }?.id)
        assertEquals("second", EventPoolRules.draw(items) { 1L }?.id)
    }

    @Test
    fun draw_extremeWeightBoundaryDoesNotRoundAwaySmallCandidate() {
        val items = listOf(candidate("small", true, 1), candidate("large", true, 100))

        assertEquals("small", EventPoolRules.draw(items) { 0L }?.id)
        assertEquals("large", EventPoolRules.draw(items) { 1L }?.id)
        assertEquals("large", EventPoolRules.draw(items) { 100L }?.id)
    }

    @Test
    fun draw_withoutEnabledItemsReturnsEmptyGuidanceState() {
        assertNull(EventPoolRules.draw(listOf(candidate("disabled", false, 1))) { 0L })
    }

    @Test
    fun draw_rejectsBrokenRandomSourceInsteadOfSelectingOutsideCandidates() {
        val result = runCatching { EventPoolRules.draw(listOf(candidate("enabled", true, 1))) { 1L } }

        assertTrue(result.isFailure)
    }

    @Test
    fun draw_rejectsWeightOutsideSupportedRange() {
        val result = runCatching {
            EventPoolRules.draw(listOf(candidate("invalid", true, 0))) { 0L }
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun wheelSegmentsUseExactWeightRatiosAndIgnoreDisabledItems() {
        val segments = EventPoolRules.wheelSegments(
            listOf(
                candidate("first", true, 1),
                candidate("disabled", false, 100),
                candidate("second", true, 3)
            )
        )

        assertEquals(listOf("first", "second"), segments.map { it.candidate.id })
        assertEquals(0.0, segments[0].startAngle, 0.0001)
        assertEquals(90.0, segments[0].sweepAngle, 0.0001)
        assertEquals(25.0, segments[0].percentage, 0.0001)
        assertEquals(90.0, segments[1].startAngle, 0.0001)
        assertEquals(270.0, segments[1].sweepAngle, 0.0001)
        assertEquals(75.0, segments[1].percentage, 0.0001)
    }

    @Test
    fun targetRotationAlignsSelectedSegmentCenterWithTopPointer() {
        val segments = EventPoolRules.wheelSegments(
            listOf(candidate("first", true, 1), candidate("second", true, 3))
        )

        val target = EventPoolRules.targetRotation(
            currentRotation = 725.0,
            selectedId = "second",
            segments = segments,
            fullTurns = 5
        )
        val selected = segments.single { it.candidate.id == "second" }

        assertTrue(target > 725.0 + 5 * 360.0)
        assertEquals(0.0, (selected.centerAngle + target) % 360.0, 0.0001)
    }

    private fun candidate(id: String, enabled: Boolean, weight: Int) = EventPoolCandidate(
        id = id,
        title = id,
        category = EventCategory.STUDY,
        isEnabled = enabled,
        weight = weight
    )
}
