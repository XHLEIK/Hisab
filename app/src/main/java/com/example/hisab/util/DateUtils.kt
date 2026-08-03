package com.example.hisab.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateUtils {

    private val fullDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    /**
     * Returns a relative date string: "Today", "Yesterday", or formatted date.
     */
    fun formatRelative(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(fullDateFormatter)
        }
    }

    /**
     * Short format: "Jul 30" or "Today"
     */
    fun formatShort(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(shortDateFormatter)
        }
    }

    /**
     * Format a YearMonth: "August 2026"
     */
    fun formatMonthYear(yearMonth: YearMonth): String =
        yearMonth.atDay(1).format(monthYearFormatter)

    /**
     * Short month year: "Aug 2026"
     */
    fun formatShortMonthYear(yearMonth: YearMonth): String =
        yearMonth.atDay(1).format(shortMonthYearFormatter)

    /**
     * Just the month: "Aug"
     */
    fun formatShortMonth(yearMonth: YearMonth): String =
        yearMonth.atDay(1).format(shortMonthFormatter)

    /**
     * Number of days elapsed in the given month (up to today if current month).
     */
    fun daysElapsed(yearMonth: YearMonth): Int {
        val today = LocalDate.now()
        val currentYearMonth = YearMonth.from(today)
        return if (yearMonth == currentYearMonth) {
            today.dayOfMonth
        } else if (yearMonth.isBefore(currentYearMonth)) {
            yearMonth.lengthOfMonth()
        } else {
            0
        }
    }

    /**
     * Get the short weekday name (Mon, Tue, etc.)
     */
    fun weekdayShortName(dayOfWeek: Int): String {
        return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }

    /**
     * Check if a date is in the current month.
     */
    fun isCurrentMonth(yearMonth: YearMonth): Boolean =
        yearMonth == YearMonth.now()
}
