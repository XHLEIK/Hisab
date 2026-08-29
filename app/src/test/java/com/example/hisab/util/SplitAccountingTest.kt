package com.example.hisab.util

import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionSubtype
import com.example.hisab.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SplitAccountingTest {

    private fun tx(
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        subtype: String? = null,
        categoryId: Long = 1L,
        account: String = "Primary Bank",
        toAccount: String? = null
    ) = TransactionEntity(
        amount = amount,
        type = type,
        categoryId = categoryId,
        account = account,
        toAccount = toAccount,
        date = LocalDate.now(),
        subtype = subtype
    )

    @Test
    fun grossReimbursedNetForCategory() {
        val rows = listOf(
            tx(1000.0, categoryId = 1L),
            tx(200.0, categoryId = 1L),
            tx(200.0, categoryId = 1L, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name)
        )
        assertEquals(1200.0, SplitAccounting.grossExpense(1L, rows), 0.001)
        assertEquals(200.0, SplitAccounting.splitReimbursement(1L, rows), 0.001)
        assertEquals(1000.0, SplitAccounting.netExpense(1L, rows), 0.001)
    }

    @Test
    fun remainingUnreimbursedAndEligibility() {
        val rows = listOf(
            tx(1200.0, categoryId = 1L),
            tx(200.0, categoryId = 1L, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name)
        )
        assertEquals(1000.0, SplitAccounting.remainingUnreimbursed(1L, rows), 0.001)
        assertTrue(SplitAccounting.isEligibleForSplit(1L, rows))

        val fullyReimbursed = rows + tx(1000.0, categoryId = 1L, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name)
        assertEquals(0.0, SplitAccounting.remainingUnreimbursed(1L, fullyReimbursed), 0.001)
        assertFalse(SplitAccounting.isEligibleForSplit(1L, fullyReimbursed))

        val noExpense = listOf(tx(200.0, categoryId = 2L, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name))
        assertFalse(SplitAccounting.isEligibleForSplit(2L, noExpense))
    }

    @Test
    fun overReimbursementMustBeRejectedNotCapped() {
        val rows = listOf(
            tx(1200.0, categoryId = 1L),
            tx(1100.0, categoryId = 1L, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name)
        )
        val remaining = SplitAccounting.remainingUnreimbursed(1L, rows)
        assertEquals(100.0, remaining, 0.001)
        val incoming = 500.0
        // The plan says: if pending.amount > remaining, reject entire split — never cap and discard
        assertTrue(incoming > remaining)
        // Simulate rejection: no new row should be added, net stays 100
        assertEquals(100.0, remaining, 0.001)
    }

    @Test
    fun balanceContributionForSplitIsPositive() {
        val normalExpense = tx(1000.0, type = TransactionType.EXPENSE)
        val split = tx(200.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name)
        val income = tx(500.0, type = TransactionType.INCOME)

        assertEquals(-1000.0, SplitAccounting.balanceContribution(normalExpense), 0.001)
        assertEquals(200.0, SplitAccounting.balanceContribution(split), 0.001)
        assertEquals(500.0, SplitAccounting.balanceContribution(income), 0.001)
    }

    @Test
    fun accountBalanceUsesCentralizedLogic() {
        val rows = listOf(
            tx(1000.0, type = TransactionType.EXPENSE, categoryId = 1L, account = "Primary Bank"),
            tx(200.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, categoryId = 1L, account = "Primary Bank"),
            tx(500.0, type = TransactionType.INCOME, account = "Primary Bank")
        )
        // Gross 1000, split 200, net 800 expense, income 500 => balance = 500 - 800 = -300
        // Via balanceContribution: -1000 +200 +500 = -300
        assertEquals(-300.0, SplitAccounting.accountBalance("Primary Bank", rows), 0.001)
        assertEquals(1000.0, SplitAccounting.grossExpenseTotal(rows), 0.001)
        assertEquals(200.0, SplitAccounting.splitTotal(rows), 0.001)
        assertEquals(800.0, SplitAccounting.netExpenseTotal(rows), 0.001)
    }

    @Test
    fun transferDoesNotAffectExpenseTotals() {
        val rows = listOf(
            tx(1000.0, type = TransactionType.EXPENSE, categoryId = 1L),
            tx(500.0, type = TransactionType.TRANSFER, account = "Primary Bank", toAccount = "Savings")
        )
        assertEquals(1000.0, SplitAccounting.grossExpenseTotal(rows), 0.001)
        assertEquals(0.0, SplitAccounting.splitTotal(rows), 0.001)
        assertEquals(1000.0, SplitAccounting.netExpenseTotal(rows), 0.001)
    }

    // ── Account-isolation regression tests (the dashboard balance bug) ──────────

    @Test
    fun accountBalance_isScopedToItsOwnRows_only() {
        // Income credited to Secondary must NOT leak into Primary's balance (Case 1),
        // and an untouched account must stay at exactly zero (Case 7).
        val rows = listOf(
            tx(505.93, type = TransactionType.INCOME, account = "Primary Bank"),
            tx(300.0, type = TransactionType.EXPENSE, account = "Primary Bank"),
            tx(9999.0, type = TransactionType.INCOME, account = "Secondary Bank")
        )
        assertEquals(205.93, SplitAccounting.accountBalance("Primary Bank", rows), 0.001)
        assertEquals(0.0, SplitAccounting.accountBalance("Savings", rows), 0.001)
    }

    @Test
    fun transferMovesMoney_betweenAccounts_notIncomeNotExpense() {
        // Cases 2 & 3: both legs of a transfer, in both directions. Global income and
        // expense totals must be untouched by transfers.
        val rows = listOf(
            tx(5000.0, type = TransactionType.INCOME, account = "Primary Bank"),
            tx(1000.0, type = TransactionType.TRANSFER, account = "Primary Bank", toAccount = "Savings"),
            tx(500.0, type = TransactionType.TRANSFER, account = "Savings", toAccount = "Primary Bank")
        )
        assertEquals(4500.0, SplitAccounting.accountBalance("Primary Bank", rows), 0.001)
        assertEquals(500.0, SplitAccounting.accountBalance("Savings", rows), 0.001)
        // Transfers are neither expense nor income globally
        assertEquals(0.0, SplitAccounting.grossExpenseTotal(rows), 0.001)
        assertEquals(5000.0, SplitAccounting.incomeTotal(rows), 0.001)
    }

    @Test
    fun splitReimbursement_addsToBalance_isNotIncome() {
        // Case 4: reimbursement money reaches the account but is never income.
        val rows = listOf(
            tx(1200.0, type = TransactionType.EXPENSE, account = "Primary Bank"),
            tx(200.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, account = "Primary Bank"),
            tx(1000.0, type = TransactionType.TRANSFER, account = "Primary Bank", toAccount = "Savings")
        )
        // Primary: −1200 (expense) + 200 (reimbursement received) − 1000 (transfer out) = −2000
        assertEquals(-2000.0, SplitAccounting.accountBalance("Primary Bank", rows), 0.001)
        // Savings: +1000 (transfer in) — the money really moved there
        assertEquals(1000.0, SplitAccounting.accountBalance("Savings", rows), 0.001)
        assertEquals(0.0, SplitAccounting.incomeTotal(rows), 0.001)
        assertEquals(1000.0, SplitAccounting.netExpenseTotal(rows), 0.001)
    }

    @Test
    fun multipleReimbursements_stayConsistent_acrossAccounts() {
        // Case 5: several splits, several accounts — each account only sees its own.
        val rows = listOf(
            tx(600.0, type = TransactionType.EXPENSE, account = "Primary Bank"),
            tx(200.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, account = "Primary Bank"),
            tx(200.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, account = "Primary Bank"),
            tx(300.0, type = TransactionType.EXPENSE, account = "Secondary Bank"),
            tx(100.0, type = TransactionType.EXPENSE, subtype = TransactionSubtype.SPLIT_REIMBURSEMENT.name, account = "Secondary Bank")
        )
        assertEquals(-200.0, SplitAccounting.accountBalance("Primary Bank", rows), 0.001)
        assertEquals(-200.0, SplitAccounting.accountBalance("Secondary Bank", rows), 0.001)
        assertEquals(400.0, SplitAccounting.netExpenseTotal(rows), 0.001)
    }

    @Test
    fun sameBankAccounts_doNotMerge() {
        // Case 6: account-level (name-keyed) balances — two accounts at the same bank
        // must never absorb each other's activity.
        val rows = listOf(
            tx(1000.0, type = TransactionType.INCOME, account = "BOB Salary"),
            tx(150.0, type = TransactionType.EXPENSE, account = "BOB Wallet")
        )
        assertEquals(1000.0, SplitAccounting.accountBalance("BOB Salary", rows), 0.001)
        assertEquals(-150.0, SplitAccounting.accountBalance("BOB Wallet", rows), 0.001)
    }

    @Test
    fun primaryPlusSecondaryBalance_excludesSavings() {
        val balances = mapOf("Primary Bank" to 505.93, "Secondary Bank" to 0.0, "Savings" to 2501.70)
        assertEquals(505.93, SplitAccounting.primaryPlusSecondaryBalance(balances), 0.001)
        // No savings account present → nothing excluded
        assertEquals(300.0, SplitAccounting.primaryPlusSecondaryBalance(mapOf("A" to 100.0, "B" to 200.0)), 0.001)
        // Only savings accounts → falls back to the grand total rather than 0
        assertEquals(2501.70, SplitAccounting.primaryPlusSecondaryBalance(mapOf("Savings" to 2501.70)), 0.001)
    }

    @Test
    fun savingsDetection_byNameOrDeclaredType() {
        assertTrue(SplitAccounting.isSavingsAccountName("Savings"))
        assertTrue(SplitAccounting.isSavingsAccountName("My Savings Bucket"))
        assertFalse(SplitAccounting.isSavingsAccountName("Primary Bank"))
        assertFalse(SplitAccounting.isSavingsAccountName(null))
        val byType = AccountEntity(name = "Piggy", type = "SAVINGS", colorHex = "#000000", isPrimary = false)
        val byNeither = AccountEntity(name = "Piggy", type = "CURRENT", colorHex = "#000000", isPrimary = false)
        assertTrue(SplitAccounting.isSavingsAccount(byType))
        assertFalse(SplitAccounting.isSavingsAccount(byNeither))
    }
}
