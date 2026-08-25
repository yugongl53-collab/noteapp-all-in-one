package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.rules.IdeaRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定 M2 灵感输入规范与精确 30×24 小时回收站边界。 */
class IdeaRulesTest {
    @Test
    fun normalize_trimsContentAndDeduplicatesCommaSeparatedTags() {
        val normalized = IdeaRules.normalize(" 记录这一刻 ", " 学习,生活，学习\n 工作 ")

        assertEquals("记录这一刻", normalized.content)
        assertEquals(listOf("学习", "生活", "工作"), normalized.tags)
    }

    @Test
    fun normalize_rejectsBlankContent() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            IdeaRules.normalize("  \n ", "标签")
        }

        assertEquals("灵感正文不能为空", failure.message)
    }

    @Test
    fun expiration_reachesBoundaryOnlyAfterThirtyTimesTwentyFourHours() {
        val deletedAt = Instant.parse("2026-08-01T08:00:00Z")

        assertFalse(IdeaRules.isExpired(deletedAt, Instant.parse("2026-08-31T07:59:59Z")))
        assertTrue(IdeaRules.isExpired(deletedAt, Instant.parse("2026-08-31T08:00:00Z")))
    }
}
