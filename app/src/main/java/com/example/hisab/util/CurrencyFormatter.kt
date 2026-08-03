package com.example.hisab.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    private val indianFormat = DecimalFormat("#,##,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("en-IN")))
    private val indianFormatNoDecimal = DecimalFormat("#,##,##0", DecimalFormatSymbols(Locale.forLanguageTag("en-IN")))

    /**
     * Formats amount with ₹ symbol and Indian numbering (e.g., ₹1,23,456.50 or ₹1,23,456)
     * If amount has non-zero decimal part (e.g. 22.50), it ALWAYS displays decimals!
     */
    fun format(amount: Double, alwaysShowDecimal: Boolean = false): String {
        val absVal = abs(amount)
        val hasFraction = abs(absVal - absVal.toLong()) >= 0.005
        val formatted = if (alwaysShowDecimal || hasFraction) {
            indianFormat.format(absVal)
        } else {
            indianFormatNoDecimal.format(absVal)
        }
        return if (amount < 0) "−₹$formatted" else "₹$formatted"
    }

    /**
     * Compact format for large amounts (e.g., ₹1.2L, ₹45K)
     */
    fun formatCompact(amount: Double): String {
        val absAmount = abs(amount)
        val prefix = if (amount < 0) "−" else ""
        return when {
            absAmount >= 10_000_000 -> "${prefix}₹${String.format(Locale.US, "%.1f", absAmount / 10_000_000)}Cr"
            absAmount >= 100_000 -> "${prefix}₹${String.format(Locale.US, "%.1f", absAmount / 100_000)}L"
            absAmount >= 1_000 -> "${prefix}₹${String.format(Locale.US, "%.1f", absAmount / 1_000)}K"
            else -> format(amount)
        }
    }

    /**
     * Formats just the number without ₹ symbol.
     */
    fun formatNumber(amount: Double, alwaysShowDecimal: Boolean = false): String {
        val absVal = abs(amount)
        val hasFraction = abs(absVal - absVal.toLong()) >= 0.005
        return if (alwaysShowDecimal || hasFraction) {
            indianFormat.format(absVal)
        } else {
            indianFormatNoDecimal.format(absVal)
        }
    }
}
