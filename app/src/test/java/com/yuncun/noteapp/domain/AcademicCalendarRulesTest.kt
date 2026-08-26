package com.yuncun.noteapp.domain

import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import com.yuncun.noteapp.domain.rules.AcademicCalendarRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 验证自然周、学期周次和学期边界标签。 */
class AcademicCalendarRulesTest {
    private val fall = TermPeriod(
        id = "fall",
        academicYearStart = 2026,
        season = TermSeason.FALL,
        startDate = LocalDate.of(2026, 9, 2),
        endDate = LocalDate.of(2027, 1, 15)
    )
    private val spring = TermPeriod(
        id = "spring",
        academicYearStart = 2026,
        season = TermSeason.SPRING,
        startDate = LocalDate.of(2027, 2, 22),
        endDate = LocalDate.of(2027, 6, 30)
    )

    @Test
    fun termWeek_countsThePartialOpeningWeekAsWeekOne() {
        assertEquals(LocalDate.of(2026, 8, 31), AcademicCalendarRules.weekStart(fall.startDate))
        assertEquals(1, AcademicCalendarRules.termWeek(fall, LocalDate.of(2026, 9, 2)))
        assertEquals(2, AcademicCalendarRules.termWeek(fall, LocalDate.of(2026, 9, 7)))
        assertNull(AcademicCalendarRules.termWeek(fall, LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun labelForDate_distinguishesTermsAndVacationGaps() {
        val terms = listOf(fall, spring)
        assertEquals("2026-2027秋季学期", AcademicCalendarRules.labelForDate(LocalDate.of(2026, 10, 1), terms))
        assertEquals("寒假", AcademicCalendarRules.labelForDate(LocalDate.of(2027, 2, 1), terms))
        assertEquals("2026-2027春季学期", AcademicCalendarRules.labelForDate(LocalDate.of(2027, 4, 1), terms))
        assertEquals("暑假", AcademicCalendarRules.labelForDate(LocalDate.of(2027, 7, 1), terms))
    }

    @Test
    fun currentPeriodLabel_inTermIncludesNameAndCalculatedWeek() {
        assertEquals(
            "2026-2027秋季学期 · 第3周",
            AcademicCalendarRules.currentPeriodLabel(LocalDate.of(2026, 9, 16), listOf(fall))
        )
    }

    @Test
    fun currentPeriodLabel_outsideTermUsesVacationAndEmptyTermsUseUnsetState() {
        assertEquals(
            "寒假中",
            AcademicCalendarRules.currentPeriodLabel(LocalDate.of(2027, 2, 1), listOf(fall, spring))
        )
        assertEquals(
            "暑假中",
            AcademicCalendarRules.currentPeriodLabel(LocalDate.of(2026, 8, 25), listOf(fall))
        )
        assertEquals("未设置学期", AcademicCalendarRules.currentPeriodLabel(LocalDate.of(2026, 8, 25), emptyList()))
    }

    @Test
    fun labelsForWeek_keepsBoundaryLabelsInDateOrderWithoutDuplicates() {
        assertEquals(
            listOf("2026-2027秋季学期", "寒假"),
            AcademicCalendarRules.labelsForWeek(LocalDate.of(2027, 1, 11), listOf(fall, spring))
        )
    }
}
