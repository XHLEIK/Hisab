package com.example.hisab.util

import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionSubtype
import com.example.hisab.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Period metrics vs current account state: the distinction the PDF used to collapse
 * ("Total Savings" = transfer volume, "Net Balance" = monthly income − expense).
 */
class FinancialSummaryTest {

    private fun tx(
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        subtype: String? = null,
        account: String = "Primary Bank",
        toAccount: String? = null
    ) = TransactionEntity(
        amount = amount,
        type = type,
        categoryId = 1L,
        account = account,
        toAccount = toAccount,
        date = LocalDate.now(),
        subtype = subtype
    )

    private val fullLedger = listOf(
        tx(21000.0, TransactionType.INCOME, account = "Primary Bank"),
        tx(1500.0, TransactionType.TRANSFER, account = "Primary Bank", toAccount = "Savings"),
        tx(3500.0, TransactionType.TRANSFER, account = "Primary Bank", toAccount = "Savings"),
        tx(500.0, TransactionType.TRANSFER, account = "Savings", toAccount = "Primary Bank"),
        tx(20000.0, TransactionType.EXPENSE, account = "Primary Bank"),
        tx(800.0, TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, account = "Primary Bank"),
        tx(2000.0, TransactionType.EXPENSE, account = "Savings")
    )
    private val names = listOf("Primary Bank", "Secondary Bank", "Savings")

    @Test
    fun periodMetrics_areScopedToTheGivenRows() {
        val augustOnly = listOf(
            tx(100.0, TransactionType.INCOME, account = "Primary Bank"),
            tx(40.0, TransactionType.EXPENSE, account = "Primary Bank")
        )
        val s = FinancialSummary.of(augustOnly, currentBalances = emptyMap(), accountNames = names)
        assertEquals(100.0, s.totalIncome, 0.001)
        assertEquals(40.0, s.grossExpense, 0.001)
        assertEquals(40.0, s.netExpense, 0.001)
        assertEquals(0.0, s.transferActivity, 0.001)
    }

    @Test
    fun currentBalances_comeFromTheFullLedger_evenWhenPeriodRowsAreAMonth() {
        val balances = names.associateWith { SplitAccounting.accountBalance(it, fullLedger) }
        val augustRows = listOf(tx(40.0, TransactionType.EXPENSE, account = "Primary Bank"))
        val s = FinancialSummary.of(augustRows, currentBalances = balances, accountNames = names)

        // Period metrics: August only
        assertEquals(0.0, s.totalIncome, 0.001)
        assertEquals(40.0, s.netExpense, 0.001)
        // Current state: full ledger
        // Primary: 21000 − 20000 + 800 (split) − 1500 − 3500 + 500 = −2700
        assertEquals(-2700.0, s.accountBalances["Primary Bank"]!!, 0.001)
        assertEquals(0.0, s.accountBalances["Secondary Bank"]!!, 0.001)
        // Savings: +1500 +3500 −500 −2000 = 2500
        assertEquals(2500.0, s.accountBalances["Savings"]!!, 0.001)
        assertEquals(-200.0, s.combinedBalance, 0.001)
    }

    @Test
    fun transferActivity_isNotAnyAccountBalance() {
        val balances = names.associateWith { SplitAccounting.accountBalance(it, fullLedger) }
        val s = FinancialSummary.of(fullLedger, currentBalances = balances, accountNames = names)
        // 1500 + 3500 + 500 moved between own accounts…
        assertEquals(5500.0, s.transferActivity, 0.001)
        // …but the Savings BALANCE is only what remained there.
        assertTrue(s.accountBalances["Savings"]!! != s.transferActivity)
    }

    @Test
    fun netExpense_isGrossMinusReimbursements_andReimbursementIsNotIncome() {
        val s = FinancialSummary.of(fullLedger, currentBalances = emptyMap(), accountNames = names)
        assertEquals(21000.0, s.totalIncome, 0.001) // split reimb must not inflate income
        assertEquals(22000.0, s.grossExpense, 0.001) // 20000 Primary + 2000 Savings, both normal expenses
        assertEquals(800.0, s.splitReimbursements, 0.001)
        assertEquals(21200.0, s.netExpense, 0.001)
    }

    @Test
    fun withoutPassedBalances_itFallsBackToDerivingFromGivenRows() {
        // All-records export: rows ARE the full ledger, so deriving balances is correct.
        val s = FinancialSummary.of(fullLedger, accountNames = names)
        assertEquals(2500.0, s.accountBalances["Savings"]!!, 0.001)
        assertEquals(-200.0, s.combinedBalance, 0.001)
    }
}
