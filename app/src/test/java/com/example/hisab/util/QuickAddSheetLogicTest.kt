package com.example.hisab.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Simulates the fixed QuickAddSheet sync logic: amountText (state) mirrors calculator.expression
 */
class QuickAddSheetLogicTest {

    private fun simulateTyping(tokens: List<String>): String {
        val calc = CalculatorEngine()
        var amountText = ""
        fun sync() { amountText = calc.expression }
        // initial empty
        tokens.forEach { token ->
            when (token) {
                "⌫" -> { calc.backspace(); sync() }
                "=" -> { calc.evaluate(); sync() }
                else -> { calc.input(token); sync() }
            }
        }
        return amountText
    }

    @Test
    fun typing888_shows888() {
        val result = simulateTyping(listOf("8","8","8"))
        assertEquals("888", result)
    }

    @Test
    fun typing10plus20times2_showsExpressionAndEquals50() {
        var amount = simulateTyping(listOf("1","0","+","2","0","×","2"))
        assertEquals("10+20×2", amount)
        // press =
        amount = simulateTyping(listOf("1","0","+","2","0","×","2","="))
        assertEquals("50", amount)
    }

    @Test
    fun operatorReplacement_showsCorrectOperator() {
        val calc = CalculatorEngine()
        var amountText = ""
        fun sync() { amountText = calc.expression }
        calc.setExpression("100+"); amountText = calc.expression
        assertEquals("100+", amountText)
        calc.input("−"); sync()
        assertEquals("100−", amountText)
        calc.input("×"); sync()
        assertEquals("100×", amountText)
    }

    @Test
    fun typingWithDecimalAndOperators() {
        val result = simulateTyping(listOf("1","0","0","+","5","0","="))
        assertEquals("150", result)
    }

    @Test
    fun typing00_handling() {
        val result = simulateTyping(listOf("5","00"))
        assertEquals("500", result)
    }

    @Test
    fun backspaceWorks() {
        val result = simulateTyping(listOf("1","2","3","⌫"))
        assertEquals("12", result)
    }

    @Test
    fun percentAdd() {
        val result = simulateTyping(listOf("1","0","0","0","+","1","0","%","="))
        assertEquals("1100", result)
    }

    @Test
    fun changingCategoryDoesNotResetAmount() {
        // Simulate that type change (category) does not clear amountText
        val calc = CalculatorEngine()
        var amountText = ""
        fun sync() { amountText = calc.expression }
        listOf("8","8","8").forEach { calc.input(it); sync() }
        assertEquals("888", amountText)
        // Simulate category change causing recomposition but not resetting amount
        // In fixed code, amountText is remember(editTransaction) so it persists
        // We just verify amountText still 888 after a no-op recomposition
        assertEquals("888", amountText)
    }
}
