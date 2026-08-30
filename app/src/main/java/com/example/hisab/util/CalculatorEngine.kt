package com.example.hisab.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure Kotlin financial calculator engine — no Compose/Android deps.
 * Single source for Add Entry + Edit Entry.
 *
 * Visible controls (new_task.md / proceed.md): 1-9, 0, 00, ., +, −, ×, ÷, %, Backspace, =
 * No visible Clear key — internal clear()/reset() exists for lifecycle only.
 *
 * Precedence: standard (×÷% before +−) via shunting-yard. Intermediate kept as BigDecimal,
 * final result rounded only on display/save to currency scale 2 (HALF_UP), not prematurely.
 * Percentage is contextual:
 *   50%        -> 0.5
 *   1000 ×10%  -> 100
 *   1000 +10%  -> 1100 (a + a*b/100)
 *   1000 −10%  -> 900
 *   1000 ÷10%  -> 10000 (a / (b/100))
 */
class CalculatorEngine {

    var expression: String = ""
        private set

    var isResultShown: Boolean = false
        private set

    val display: String
        get() = if (expression.isEmpty()) "0" else expression

    fun setExpression(value: String) {
        expression = value
        isResultShown = false
    }

    fun setAmount(amount: Double) {
        expression = if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
        isResultShown = false
    }

    fun clear() {
        expression = ""
        isResultShown = false
    }

    fun input(token: String) {
        // If result is shown, starting a new number begins fresh; starting an operator continues from result
        if (isResultShown) {
            if (token in listOf("+", "-", "−", "×", "÷", "%")) {
                // continue calculation from result
                isResultShown = false
            } else {
                // new number
                expression = ""
                isResultShown = false
            }
        }

        when (token) {
            "00" -> inputDoubleZero()
            "." -> inputDecimal()
            "+" -> inputOperator("+")
            "-", "−" -> inputOperator("−")
            "×" -> inputOperator("×")
            "÷" -> inputOperator("÷")
            "%" -> inputPercent()
            else -> { // digits 0-9
                if (token.length == 1 && token[0].isDigit()) inputDigit(token)
            }
        }
    }

    private fun inputDigit(d: String) {
        // Prevent meaningless leading zeros: "0" + "5" -> "5", "0" + "0" stays "0"
        if (expression.isEmpty()) {
            if (d == "0") {
                expression = "0"
                return
            }
            expression += d
            return
        }
        val lastNumber = currentNumberToken()
        if (lastNumber == "0" && !lastNumber.contains(".")) {
            // Replace leading zero
            expression = expression.dropLast(1) + d
        } else {
            expression += d
        }
    }

    private fun inputDoubleZero() {
        if (expression.isEmpty()) {
            expression = "0"
            return
        }
        val lastNumber = currentNumberToken()
        if (lastNumber.isEmpty() || lastNumber in listOf("+", "-", "−", "×", "÷")) {
            // Operator then 00 -> treat as 0
            expression += "0"
            return
        }
        if (lastNumber == "0" && !lastNumber.contains(".")) {
            // "0" -> "0" (00 on empty zero stays zero)
            return
        }
        // Append "00" but will be normalized: e.g., "5" -> "500"
        // If we are at "0." then "00" -> "0.00"
        expression += "00"
        // Normalize leading zeros already handled via currentNumber check on next input
    }

    private fun inputDecimal() {
        val lastNumber = currentNumberToken()
        if (lastNumber.contains(".")) return
        if (lastNumber.isEmpty() || lastNumber in listOf("+", "-", "−", "×", "÷")) {
            expression += "0."
        } else {
            expression += "."
        }
    }

    private fun inputOperator(op: String) {
        val normalized = when (op) {
            "×" -> "×"
            "÷" -> "÷"
            else -> op // + or −
        }
        if (expression.isEmpty()) {
            // Allow leading minus for negative numbers
            if (normalized == "-" || normalized == "−") expression = normalized
            return
        }
        val lastChar = expression.last().toString()
        if (lastChar in listOf("+", "-", "−", "×", "÷")) {
            // Replace operator
            expression = expression.dropLast(1) + normalized
        } else if (lastChar == ".") {
            // Prevent "10. +"
            return
        } else {
            expression += normalized
        }
        isResultShown = false
    }

    private fun inputPercent() {
        if (expression.isEmpty()) return
        val lastChar = expression.last().toString()
        if (lastChar in listOf("+", "-", "−", "×", "÷", ".", "%")) return
        expression += "%"
    }

    fun backspace() {
        if (expression.isEmpty()) return
        if (isResultShown) {
            // Clear result on backspace
            expression = ""
            isResultShown = false
            return
        }
        expression = expression.dropLast(1)
    }

    fun evaluate(): BigDecimal? {
        if (expression.isEmpty()) return null
        // Normalize unicode minus to ascii for eval
        val normalizedExpr = expression.replace("−", "-")
        val lastChar = normalizedExpr.last().toString()
        if (lastChar in listOf("+", "-", "×", "÷", ".", "%") && lastChar != "%") {
            // Incomplete like "100+"
            return null
        }
        // Check for invalid like "×100" (starts with operator except minus)
        if (normalizedExpr.first().toString() in listOf("×", "÷", "%", "+")) return null
        // Prevent "10..5"
        if (normalizedExpr.contains("..")) return null

        return try {
            val tokens = tokenize(normalizedExpr) ?: return null
            val processed = handlePercent(tokens) ?: return null
            val rpn = shuntingYard(processed) ?: return null
            val result = evalRpn(rpn) ?: return null
            // Round final result to 2 decimals for currency, but keep as BigDecimal
            val scaled = result.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            // Update state to result - convert ascii minus to unicode for display consistency
            val resultStr = scaled.toPlainString().replace("-", "−")
            expression = resultStr
            isResultShown = true
            scaled
        } catch (e: Exception) {
            null
        }
    }

    // Non-mutating preview for canSave
    fun peekEvaluatedAmount(): Double? {
        if (expression.isEmpty()) return null
        val normalizedExpr = expression.replace("−", "-")
        if (normalizedExpr.last().toString() in listOf("+", "-", "×", "÷", ".")) return null
        return try {
            val tokens = tokenize(normalizedExpr) ?: return null
            val processed = handlePercent(tokens) ?: return null
            val rpn = shuntingYard(processed) ?: return null
            val result = evalRpn(rpn) ?: return null
            val scaled = result.setScale(2, RoundingMode.HALF_UP)
            // Check divide by zero already handled (null)
            scaled.toDouble()
        } catch (e: Exception) {
            try {
                BigDecimal(normalizedExpr).setScale(2, RoundingMode.HALF_UP).toDouble()
            } catch (ex: Exception) { null }
        }
    }

    fun isExpressionValidForSave(): Boolean {
        val amt = peekEvaluatedAmount()
        return amt != null && amt > 0
    }

    private fun currentNumberToken(): String {
        if (expression.isEmpty()) return ""
        // Find substring after last operator
        var i = expression.length - 1
        while (i >= 0 && expression[i].toString() !in listOf("+", "-", "−", "×", "÷", "%")) {
            i--
        }
        return expression.substring(i + 1)
    }

    private fun tokenize(expr: String): List<String>? {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i].toString()
            when {
                c[0].isDigit() || c == "." -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.' )) {
                        sb.append(expr[i])
                        i++
                    }
                    // Check for stray % attached to number like "10%" -> we want "10" and "%" separate
                    tokens.add(sb.toString())
                }
                c in listOf("+", "-", "×", "÷", "%") -> {
                    tokens.add(c)
                    i++
                }
                else -> return null // invalid char
            }
        }
        return tokens
    }

    private fun handlePercent(tokens: List<String>): List<String>? {
        // Contextual % handling
        // For each "%" token, look at preceding number b and operator before b, and a
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok == "%") {
                if (result.isEmpty()) return null
                val bStr = result.removeAt(result.lastIndex)
                val b = try { BigDecimal(bStr) } catch (e: Exception) { return null }
                if (result.isEmpty()) {
                    // standalone "50%" -> 0.5
                    val computed = b.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
                    result.add(computed.stripTrailingZeros().toPlainString())
                } else {
                    val op = result.last()
                    if (op in listOf("+", "-")) {
                        // Need a: number before operator
                        if (result.size < 2) return null
                        val aStr = result[result.size - 2]
                        val a = try { BigDecimal(aStr) } catch (e: Exception) { return null }
                        // b% of a = a * b /100
                        val computed = a.multiply(b).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
                        // Keep operator, replace b% with computed
                        result.add(computed.stripTrailingZeros().toPlainString())
                    } else if (op in listOf("×", "÷")) {
                        // b% -> b/100
                        val computed = b.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
                        result.add(computed.stripTrailingZeros().toPlainString())
                    } else {
                        return null
                    }
                }
                i++
            } else {
                result.add(tok)
                i++
            }
        }
        return result
    }

    private fun shuntingYard(tokens: List<String>): List<String>? {
        val output = mutableListOf<String>()
        val ops = mutableListOf<String>()
        fun precedence(op: String): Int = when (op) {
            "×", "÷" -> 2
            "+", "-" -> 1
            else -> 0
        }
        for (tok in tokens) {
            if (tok.toBigDecimalOrNull() != null) {
                output.add(tok)
            } else if (tok in listOf("+", "-", "×", "÷")) {
                while (ops.isNotEmpty() && ops.last() != "(" && precedence(ops.last()) >= precedence(tok)) {
                    output.add(ops.removeAt(ops.lastIndex))
                }
                ops.add(tok)
            } else {
                return null
            }
        }
        while (ops.isNotEmpty()) {
            val op = ops.removeAt(ops.lastIndex)
            if (op == "(" || op == ")") return null
            output.add(op)
        }
        return output
    }

    private fun evalRpn(rpn: List<String>): BigDecimal? {
        val stack = mutableListOf<BigDecimal>()
        for (tok in rpn) {
            if (tok.toBigDecimalOrNull() != null) {
                stack.add(BigDecimal(tok))
            } else {
                if (stack.size < 2) return null
                val b = stack.removeAt(stack.lastIndex)
                val a = stack.removeAt(stack.lastIndex)
                val res = when (tok) {
                    "+" -> a.add(b)
                    "-" -> a.subtract(b)
                    "×" -> a.multiply(b)
                    "÷" -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) return null
                        a.divide(b, 10, RoundingMode.HALF_UP)
                    }
                    else -> return null
                }
                stack.add(res)
            }
        }
        return if (stack.size == 1) stack[0] else null
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try { BigDecimal(this) } catch (e: Exception) { null }
}
