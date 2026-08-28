package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.EventCategory
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.TextRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证固定事件性质和用户文本规范化的公共契约。 */
class CommonRulesTest {
    @Test
    fun selectableCategories_containsAllFiveCategories() {
        assertEquals(5, EventCategory.selectable.size)
        assertTrue(EventCategory.selectable.contains(EventCategory.WORK))
        assertTrue(EventCategory.selectable.contains(EventCategory.STUDY))
        assertTrue(EventCategory.selectable.contains(EventCategory.HIGH_QUALITY_ENTERTAINMENT))
        assertTrue(EventCategory.selectable.contains(EventCategory.LOW_QUALITY_ENTERTAINMENT))
        assertTrue(EventCategory.selectable.contains(EventCategory.SOCIAL))
    }

    @Test
    fun normalizeRequiredText_trimsAndRejectsBlankInput() {
        assertEquals("阅读", TextRules.normalizeRequiredText("  阅读  "))
        val error = runCatching { TextRules.normalizeRequiredText(" \n ") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun normalizeTags_trimsRemovesBlankAndKeepsFirstOccurrence() {
        assertEquals(
            listOf("工作", "重要"),
            TextRules.normalizeTags(listOf(" 工作 ", "", "工作", "重要 "))
        )
    }

    @Test
    fun academicTermName_usesStableChineseFormat() {
        assertEquals("2026-2027秋季学期", TermSeason.FALL.displayName(2026))
        assertEquals("2026-2027春季学期", TermSeason.SPRING.displayName(2026))
    }
}
