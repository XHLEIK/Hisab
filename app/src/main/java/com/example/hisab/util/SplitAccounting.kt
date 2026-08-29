package com.example.hisab.util

import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionSubtype
import com.example.hisab.data.model.TransactionType

object SplitAccounting {

    const val SPLIT_SUBTYPE = "SPLIT_REIMBURSEMENT"

    fun isSplitReimbursement(tx: TransactionEntity): Boolean =
        tx.type == TransactionType.EXPENSE && tx.subtype == TransactionSubtype.SPLIT_REIMBURSEMENT.name

    fun isNormalExpense(tx: TransactionEntity): Boolean =
        tx.type == TransactionType.EXPENSE && !isSplitReimbursement(tx)

    fun balanceContribution(tx: TransactionEntity): Double = when {
        tx.type == TransactionType.INCOME -> tx.amount
        tx.type == TransactionType.EXPENSE && isSplitReimbursement(tx) -> tx.amount
        tx.type == TransactionType.EXPENSE -> -tx.amount
        tx.type == TransactionType.TRANSFER -> 0.0
        else -> 0.0
    }

    fun transferLeg(tx: TransactionEntity, accountName: String): Double {
        if (tx.type != TransactionType.TRANSFER) return 0.0
        var delta = 0.0
        if (tx.account == accountName) delta -= tx.amount
        if (tx.toAccount == accountName) delta += tx.amount
        return delta
    }

    /**
     * Authoritative current balance for ONE account, derived from the ledger.
     *
     * Only rows whose `account` (or transfer `toAccount`) is [accountName] move this balance:
     *   income +amount · normal expense −amount · split reimbursement +amount ·
     *   transfer out −amount · transfer in +amount.
     *
     * The account scoping guard is load-bearing: without it every account absorbs the
     * global income/expense totals and only differs by its transfer legs — which is
     * exactly the bug that made the dashboard's Accounts Overview disagree with Analytics.
     */
    fun accountBalance(accountName: String, rows: List<TransactionEntity>): Double =
        rows.sumOf { tx ->
            var delta = 0.0
            if (tx.account == accountName) delta += balanceContribution(tx)
            if (tx.type == TransactionType.TRANSFER) {
                if (tx.account == accountName) delta -= tx.amount
                if (tx.toAccount == accountName) delta += tx.amount
            }
            delta
        }

    /** A savings bucket: by account NAME or by the account's declared TYPE. */
    fun isSavingsAccountName(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.contains("savings") || lower.contains("saving")
    }

    fun isSavingsAccount(account: AccountEntity): Boolean =
        isSavingsAccountName(account.name) || account.type.equals("SAVINGS", ignoreCase = true)

    /**
     * Home hero "Net Balance": the sum of every non-savings account's current balance.
     * The widget is labelled "Primary + Secondary Accounts", so savings is excluded —
     * but not by hard-coding those two names: ANY account not detected as savings counts.
     * Falls back to the grand total only when every account is a savings account.
     */
    fun primaryPlusSecondaryBalance(balances: Map<String, Double>): Double {
        val filtered = balances.filterKeys { !isSavingsAccountName(it) }
        return if (filtered.isNotEmpty()) filtered.values.sum() else balances.values.sum()
    }

    fun grossExpense(categoryId: Long, rows: List<TransactionEntity>): Double =
        rows.filter { it.categoryId == categoryId && isNormalExpense(it) }.sumOf { it.amount }

    fun splitReimbursement(categoryId: Long, rows: List<TransactionEntity>): Double =
        rows.filter { it.categoryId == categoryId && isSplitReimbursement(it) }.sumOf { it.amount }

    fun netExpense(categoryId: Long, rows: List<TransactionEntity>): Double =
        grossExpense(categoryId, rows) - splitReimbursement(categoryId, rows)

    fun grossExpenseTotal(rows: List<TransactionEntity>): Double =
        rows.filter { isNormalExpense(it) }.sumOf { it.amount }

    fun splitTotal(rows: List<TransactionEntity>): Double =
        rows.filter { isSplitReimbursement(it) }.sumOf { it.amount }

    fun netExpenseTotal(rows: List<TransactionEntity>): Double =
        grossExpenseTotal(rows) - splitTotal(rows)

    fun incomeTotal(rows: List<TransactionEntity>): Double =
        rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

    fun remainingUnreimbursed(categoryId: Long, rows: List<TransactionEntity>): Double =
        grossExpense(categoryId, rows) - splitReimbursement(categoryId, rows)

    fun isEligibleForSplit(categoryId: Long, rows: List<TransactionEntity>): Boolean =
        remainingUnreimbursed(categoryId, rows) > 0.0 && grossExpense(categoryId, rows) > 0.0
}
