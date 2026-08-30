package com.example.hisab.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private fun engineWith(expr: String): CalculatorEngine {
        val e = CalculatorEngine()
        e.setExpression(expr)
        return e
    }

    @Test
    fun precedence_10plus20times2_equals50() {
        val e = engineWith("10+20×2")
        val result = e.evaluate()
        assertEquals(BigDecimal("50.00").stripTrailingZeros(), result?.stripTrailingZeros())
        assertEquals("50", e.expression)
    }

    @Test
    fun precedence_withAsciiOperators() {
        val e = CalculatorEngine()
        e.input("1"); e.input("0"); e.input("+"); e.input("2"); e.input("0"); e.input("×"); e.input("2")
        // 10+20×2 =50
        val r = e.evaluate()
        assertEquals(50.0, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun multiplyPercent_1000times10percent_is100() {
        val e = engineWith("1000×10%")
        val r = e.evaluate()
        assertEquals(100.0, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun addPercent_1000plus10percent_is1100() {
        val e = engineWith("1000+10%")
        val r = e.evaluate()
        assertEquals(1100.0, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun minusPercent_1000minus10percent_is900() {
        val e = engineWith("1000−10%")
        val r = e.evaluate()
        // Using unicode minus
        assertEquals(900.0, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun dividePercent_1000div10percent_is10000() {
        val e = engineWith("1000÷10%")
        val r = e.evaluate()
        assertEquals(10000.0, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun standalonePercent_50percent_is0_5() {
        val e = engineWith("50%")
        val r = e.evaluate()
        assertEquals(0.5, r?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun doubleZero_append() {
        val e = CalculatorEngine()
        e.input("5"); e.input("00")
        assertEquals("500", e.expression)
        assertEquals(500.0, e.peekEvaluatedAmount() ?: 0.0, 0.001)
    }

    @Test
    fun doubleZero_onZero_staysZero() {
        val e = CalculatorEngine()
        e.input("0"); e.input("00")
        assertEquals("0", e.expression)
    }

    @Test
    fun decimal_singleDot() {
        val e = CalculatorEngine()
        e.input(".")
        assertEquals("0.", e.expression)
        e.input(".")
        assertEquals("0.", e.expression) // second dot ignored
        e.input("5")
        assertEquals("0.5", e.expression)
    }

    @Test
    fun operatorReplacement() {
        val e = engineWith("100+")
        e.input("-")
        assertEquals("100−", e.expression)
        e.input("×")
        assertEquals("100×", e.expression)
    }

    @Test
    fun divideByZero_returnsNull() {
        val e = engineWith("10÷0")
        val r = e.evaluate()
        assertNull(r)
        // peek also null
        assertNull(e.peekEvaluatedAmount())
    }

    @Test
    fun peek_doesNotMutate() {
        val e = engineWith("10+20")
        val peek = e.peekEvaluatedAmount()
        assertEquals(30.0, peek ?: 0.0, 0.001)
        assertEquals("10+20", e.expression) // not mutated
        val eval = e.evaluate()
        assertEquals(30.0, eval?.toDouble() ?: 0.0, 0.001)
        assertEquals("30", e.expression) // after evaluate mutated
    }

    @Test
    fun backspace_removesLastChar() {
        val e = engineWith("123")
        e.backspace()
        assertEquals("12", e.expression)
        e.backspace(); e.backspace()
        assertEquals("0", e.display) // empty -> display 0 but expression ""
        assertEquals("", e.expression)
    }

    @Test
    fun resultContinuation_operatorContinues() {
        val e = engineWith("10+20")
        e.evaluate() // -> 30
        assertEquals("30", e.expression)
        e.input("+")
        assertEquals("30+", e.expression)
        e.input("5")
        assertEquals("30+5", e.expression)
    }

    @Test
    fun resultContinuation_numberStartsFresh() {
        val e = engineWith("10+20")
        e.evaluate() // 30
        e.input("5")
        assertEquals("5", e.expression)
    }

    @Test
    fun saveWithoutEquals_peekEvaluates() {
        // 100+50 without pressing = should still be savable as 150 via peek
        val e = engineWith("100+50")
        assertEquals(150.0, e.peekEvaluatedAmount() ?: 0.0, 0.001)
        assertEquals(true, e.isExpressionValidForSave())
    }

    @Test
    fun incompleteExpression_notValidForSave() {
        val e = engineWith("100+")
        assertNull(e.peekEvaluatedAmount())
        assertEquals(false, e.isExpressionValidForSave())
    }

    @Test
    fun leadingZeroReplacement() {
        val e = CalculatorEngine()
        e.input("0"); e.input("5")
        assertEquals("5", e.expression)
    }
}
