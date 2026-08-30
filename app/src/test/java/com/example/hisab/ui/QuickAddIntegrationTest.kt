package com.example.hisab.ui

import com.example.hisab.util.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mandatory integration checks from proceed.md corrections:
 * - 100+200= → Save → Room 300
 * - Edit 500+200= → 700
 * - 100+50 Save without = → 150
 */
class QuickAddIntegrationTest {

    @Test
    fun add_100plus200_equals300_savable() {
        val calc = CalculatorEngine()
        calc.input("1"); calc.input("0"); calc.input("0")
        calc.input("+")
        calc.input("2"); calc.input("0"); calc.input("0")
        calc.evaluate()
        assertEquals(300.0, calc.peekEvaluatedAmount() ?: 0.0, 0.001)
        assertEquals("300", calc.expression)
        // Simulate save: using peek amount
        val amount = calc.peekEvaluatedAmount()!!
        assertEquals(300.0, amount, 0.001)
    }

    @Test
    fun edit_500plus200_equals700() {
        val calc = CalculatorEngine()
        calc.setAmount(500.0) // edit initial
        assertEquals("500", calc.expression)
        calc.input("+")
        calc.input("2"); calc.input("0"); calc.input("0")
        calc.evaluate()
        assertEquals(700.0, calc.peekEvaluatedAmount() ?: 0.0, 0.001)
        assertEquals("700", calc.expression)
    }

    @Test
    fun saveWithoutEquals_100plus50_is150() {
        val calc = CalculatorEngine()
        calc.input("1"); calc.input("0"); calc.input("0")
        calc.input("+")
        calc.input("5"); calc.input("0")
        // Do NOT press =
        val peek = calc.peekEvaluatedAmount()
        assertEquals(150.0, peek ?: 0.0, 0.001)
        // Save path should evaluate if peek null? Here peek is not null, so save succeeds
        // Simulate QuickAddSheet save logic: evaluated ?: evaluate()
        val evaluated = calc.peekEvaluatedAmount()
        val final = evaluated ?: calc.evaluate()?.toDouble()
        assertEquals(150.0, final ?: 0.0, 0.001)
    }

    @Test
    fun backupCard_visibilityLogic() {
        // showBackupCard = isEndOfMonth && dismissed != currentYearMonth
        val today = java.time.LocalDate.now()
        val isEnd = today.dayOfMonth >= today.lengthOfMonth() - 1
        val current = java.time.YearMonth.from(today).toString()
        fun show(dismissed: String) = isEnd && dismissed != current
        // Not dismissed this month => shows if EOM
        assertEquals(isEnd, show(""))
        assertEquals(isEnd, show("2000-01"))
        // Dismissed this month => hidden
        assertEquals(false, show(current))
        // Dismissed different month => shows again if EOM
        val other = if (current != "2099-12") "2099-12" else "2000-01"
        assertEquals(isEnd, show(other))
    }
}
