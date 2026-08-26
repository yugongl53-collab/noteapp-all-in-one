package com.yuncun.noteapp.domain.rules

import com.yuncun.noteapp.domain.model.TermPeriod
import com.yuncun.noteapp.domain.model.TermSeason
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 学期周次和假期标签的唯一纯函数实现。 */
object AcademicCalendarRules {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun termWeek(term: TermPeriod, date: LocalDate): Int? {
        if (date < term.startDate || date > term.endDate) return null
        val elapsedWeeks = ChronoUnit.WEEKS.between(weekStart(term.startDate), weekStart(date))
        return elapsedWeeks.toInt() + 1
    }

    fun actualWeekCount(term: TermPeriod): Int =
        requireNotNull(termWeek(term, term.endDate))

    /** 顶部时期按钮始终描述设备当天，不受用户正在浏览的周次影响。 */
    fun currentPeriodLabel(date: LocalDate, terms: List<TermPeriod>): String {
        val currentTerm = terms.firstOrNull { date in it.startDate..it.endDate }
        if (currentTerm != null) {
            val week = requireNotNull(termWeek(currentTerm, date))
            return "${currentTerm.displayName} · 第${week}周"
        }
        return labelForDate(date, terms)?.let { "${it}中" } ?: "未设置学期"
    }

    fun labelForDate(date: LocalDate, terms: List<TermPeriod>): String? {
        val sortedTerms = terms.sortedBy { it.startDate }
        sortedTerms.firstOrNull { date in it.startDate..it.endDate }?.let { return it.displayName }
        if (sortedTerms.isEmpty()) return null

        val previous = sortedTerms.lastOrNull { it.endDate < date }
        val next = sortedTerms.firstOrNull { it.startDate > date }
        // 间隙由前一学期结束后的假期定义；范围外则根据最近学期推断。
        return when {
            previous != null -> vacationAfter(previous.season)
            next != null -> vacationBefore(next.season)
            else -> null
        }
    }

    fun labelsForWeek(weekDate: LocalDate, terms: List<TermPeriod>): List<String> {
        val monday = weekStart(weekDate)
        return (0L..6L)
            .mapNotNull { offset -> labelForDate(monday.plusDays(offset), terms) }
            .distinct()
    }

    private fun vacationAfter(season: TermSeason): String =
        if (season == TermSeason.FALL) "寒假" else "暑假"

    private fun vacationBefore(season: TermSeason): String =
        if (season == TermSeason.FALL) "暑假" else "寒假"
}
