package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.EventPoolCandidate
import com.yuncun.noteapp.domain.rules.EventPoolRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证抽取边界：空启用集合不抽取，索引范围只对应启用项目。 */
class EventPoolRulesTest {
    @Test
    fun draw_filtersDisabledItemsBeforeSelecting() {
        val items = listOf(
            candidate("disabled", false),
            candidate("first", true),
            candidate("second", true)
        )
        var bound = 0

        val result = EventPoolRules.draw(items) { size -> bound = size; 1 }

        assertEquals(2, bound)
        assertEquals("second", result?.id)
    }

    @Test
    fun draw_withoutEnabledItemsReturnsEmptyGuidanceState() {
        assertNull(EventPoolRules.draw(listOf(candidate("disabled", false))) { 0 })
    }

    @Test
    fun draw_rejectsBrokenRandomSourceInsteadOfSelectingOutsideCandidates() {
        val result = runCatching { EventPoolRules.draw(listOf(candidate("enabled", true))) { 1 } }

        assertTrue(result.isFailure)
    }

    private fun candidate(id: String, enabled: Boolean) = EventPoolCandidate(
        id = id,
        title = id,
        category = EventCategory.STUDY,
        isEnabled = enabled
    )
}
