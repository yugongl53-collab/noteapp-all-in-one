package com.yuncun.noteapp.ui

import com.yuncun.noteapp.ui.screens.SegmentedDateTimeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证时间与日期分段输入器的跳格、补零、合法性校验与格式化规则。
 */
class SegmentedDateTimeRulesTest {

    @Test
    fun parseTimeString_handlesStandardAndEmptyFormats() {
        assertEquals("09" to "30", SegmentedDateTimeRules.parseTimeString("09:30"))
        assertEquals("9" to "5", SegmentedDateTimeRules.parseTimeString("9:5"))
        assertEquals("" to "", SegmentedDateTimeRules.parseTimeString(""))
        assertEquals("12" to "", SegmentedDateTimeRules.parseTimeString("12"))
    }

    @Test
    fun formatTimeString_handlesAutoPadding() {
        assertEquals("09:05", SegmentedDateTimeRules.formatTimeString("9", "5", autoPad = true))
        assertEquals("9:5", SegmentedDateTimeRules.formatTimeString("9", "5", autoPad = false))
        assertEquals("18:30", SegmentedDateTimeRules.formatTimeString("18", "30", autoPad = true))
        assertEquals("", SegmentedDateTimeRules.formatTimeString("", "", autoPad = true))
    }

    @Test
    fun hourValidation_logicalJumpFor3To9() {
        for (digit in '3'..'9') {
            val result = SegmentedDateTimeRules.validateHourInput("", digit.toString())
            assertTrue("小时输入 $digit 应被接受", result.accepted)
            assertTrue("小时首位输入 $digit 应触发逻辑跳格", result.autoJump)
            assertEquals(digit.toString(), result.newText)
        }
    }

    @Test
    fun hourValidation_waitsForSecondDigitFor0To2() {
        for (digit in '0'..'2') {
            val result = SegmentedDateTimeRules.validateHourInput("", digit.toString())
            assertTrue("小时输入 $digit 应被接受", result.accepted)
            assertFalse("小时首位输入 $digit 不应立即跳格", result.autoJump)
            assertEquals(digit.toString(), result.newText)
        }
    }

    @Test
    fun hourValidation_fullWidthJumpForValidTwoDigits() {
        val validHours = listOf("00", "08", "12", "19", "20", "23")
        for (h in validHours) {
            val result = SegmentedDateTimeRules.validateHourInput(h.take(1), h)
            assertTrue("小时 $h 应被接受", result.accepted)
            assertTrue("小时 $h 满位应触发跳格", result.autoJump)
            assertEquals(h, result.newText)
        }
    }

    @Test
    fun hourValidation_rejectsInvalidHours() {
        val invalidHours = listOf("24", "25", "29", "99")
        for (h in invalidHours) {
            val result = SegmentedDateTimeRules.validateHourInput("2", h)
            assertFalse("小时 $h 越界应被拒绝", result.accepted)
            assertEquals("2", result.newText)
        }
    }

    @Test
    fun minuteValidation_rejectsFirstDigitGreaterThan5() {
        for (digit in '6'..'9') {
            val result = SegmentedDateTimeRules.validateMinuteInput("", digit.toString())
            assertFalse("分钟首位 $digit 越界应被拒绝", result.accepted)
            assertEquals("", result.newText)
        }

        for (digit in '0'..'5') {
            val result = SegmentedDateTimeRules.validateMinuteInput("", digit.toString())
            assertTrue("分钟首位 $digit 应被接受", result.accepted)
            assertEquals(digit.toString(), result.newText)
        }
    }

    @Test
    fun minuteValidation_acceptsValidTwoDigitsAndRejectsOver59() {
        val validMinutes = listOf("00", "09", "30", "45", "59")
        for (m in validMinutes) {
            val result = SegmentedDateTimeRules.validateMinuteInput(m.take(1), m)
            assertTrue("分钟 $m 应被接受", result.accepted)
            assertEquals(m, result.newText)
        }

        val invalidMinutes = listOf("60", "75", "99")
        for (m in invalidMinutes) {
            val result = SegmentedDateTimeRules.validateMinuteInput("5", m)
            assertFalse("分钟 $m 越界应被拒绝", result.accepted)
            assertEquals("5", result.newText)
        }
    }

    @Test
    fun parseDateString_handlesVariousFormats() {
        assertEquals(Triple("2026", "08", "26"), SegmentedDateTimeRules.parseDateString("2026-08-26"))
        assertEquals(Triple("", "08", "26"), SegmentedDateTimeRules.parseDateString("08-26"))
        assertEquals(Triple("", "", ""), SegmentedDateTimeRules.parseDateString(""))
    }

    @Test
    fun formatDateString_handlesAutoPaddingAndInclusionOfYear() {
        assertEquals(
            "2026-08-06",
            SegmentedDateTimeRules.formatDateString("2026", "8", "6", autoPad = true, includeYear = true)
        )
        assertEquals(
            "08-06",
            SegmentedDateTimeRules.formatDateString("2026", "8", "6", autoPad = true, includeYear = false)
        )
        assertEquals(
            "2026-8-6",
            SegmentedDateTimeRules.formatDateString("2026", "8", "6", autoPad = false, includeYear = true)
        )
    }

    @Test
    fun yearValidation_autoJumpAt4Digits() {
        val r1 = SegmentedDateTimeRules.validateYearInput("", "2")
        assertFalse(r1.autoJump)
        assertEquals("2", r1.newText)

        val r3 = SegmentedDateTimeRules.validateYearInput("20", "202")
        assertFalse(r3.autoJump)
        assertEquals("202", r3.newText)

        val r4 = SegmentedDateTimeRules.validateYearInput("202", "2026")
        assertTrue(r4.autoJump)
        assertEquals("2026", r4.newText)
    }

    @Test
    fun monthValidation_logicalJumpFor2To9() {
        for (digit in '2'..'9') {
            val result = SegmentedDateTimeRules.validateMonthInput("", digit.toString())
            assertTrue("月份输入 $digit 应被接受", result.accepted)
            assertTrue("月份首位输入 $digit 应触发逻辑跳格", result.autoJump)
            assertEquals(digit.toString(), result.newText)
        }
    }

    @Test
    fun monthValidation_waitsForSecondDigitFor0And1() {
        for (digit in listOf("0", "1")) {
            val result = SegmentedDateTimeRules.validateMonthInput("", digit)
            assertTrue("月份输入 $digit 应被接受", result.accepted)
            assertFalse("月份首位输入 $digit 不应立即跳格", result.autoJump)
            assertEquals(digit, result.newText)
        }
    }

    @Test
    fun monthValidation_acceptsValidTwoDigitsAndRejectsInvalid() {
        val validMonths = listOf("01", "09", "10", "11", "12")
        for (m in validMonths) {
            val result = SegmentedDateTimeRules.validateMonthInput(m.take(1), m)
            assertTrue("月份 $m 应被接受", result.accepted)
            assertTrue("月份 $m 满位应触发跳格", result.autoJump)
            assertEquals(m, result.newText)
        }

        val invalidMonths = listOf("00", "13", "14", "19", "99")
        for (m in invalidMonths) {
            val result = SegmentedDateTimeRules.validateMonthInput("1", m)
            assertFalse("月份 $m 越界应被拒绝", result.accepted)
            assertEquals("1", result.newText)
        }
    }

    @Test
    fun dayValidation_respectsMonthBoundaries() {
        // 2月平年28天，闰年29天
        assertEquals(28, SegmentedDateTimeRules.getMaxDaysInMonth(2025, 2))
        assertEquals(29, SegmentedDateTimeRules.getMaxDaysInMonth(2024, 2))
        assertEquals(29, SegmentedDateTimeRules.getMaxDaysInMonth(2000, 2))
        assertEquals(28, SegmentedDateTimeRules.getMaxDaysInMonth(1900, 2))
        // 4月30天
        assertEquals(30, SegmentedDateTimeRules.getMaxDaysInMonth(2026, 4))
        // 1月31天
        assertEquals(31, SegmentedDateTimeRules.getMaxDaysInMonth(2026, 1))

        // 2026年4月30日合法，31日非法
        val validApril = SegmentedDateTimeRules.validateDayInput("3", "30", year = 2026, month = 4)
        assertTrue(validApril.accepted)
        assertEquals("30", validApril.newText)

        val invalidApril = SegmentedDateTimeRules.validateDayInput("3", "31", year = 2026, month = 4)
        assertFalse(invalidApril.accepted)
        assertEquals("3", invalidApril.newText)

        // 2025年2月28日合法，29日非法
        val validFeb = SegmentedDateTimeRules.validateDayInput("2", "28", year = 2025, month = 2)
        assertTrue(validFeb.accepted)
        val invalidFeb = SegmentedDateTimeRules.validateDayInput("2", "29", year = 2025, month = 2)
        assertFalse(invalidFeb.accepted)

        // 2024年2月29日合法
        val leapFeb = SegmentedDateTimeRules.validateDayInput("2", "29", year = 2024, month = 2)
        assertTrue(leapFeb.accepted)
    }

    @Test
    fun dayValidation_rejects00AndGreaterThan31() {
        val r00 = SegmentedDateTimeRules.validateDayInput("0", "00", year = 2026, month = 1)
        assertFalse(r00.accepted)

        val r32 = SegmentedDateTimeRules.validateDayInput("3", "32", year = 2026, month = 1)
        assertFalse(r32.accepted)
    }
}
