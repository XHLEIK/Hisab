package com.example.hisab.util

import com.example.hisab.data.db.entity.TransactionEntity

/**
 * The ONE financial-summary model. Every surface that reports money — Home, Analytics,
 * PDF, XLSX, CSV — must derive its numbers through [of], never re-invent the arithmetic.
 *
 * It deliberately keeps two different concepts apart:
 *  - PERIOD METRICS (income, gross/net expense, transfer activity): sums over the rows
 *    handed in — callers pass a month's rows for a monthly report, all rows for all-time.
 *  - CURRENT ACCOUNT STATE ([accountBalances], [combinedBalance]): per-account balances
 *    via [SplitAccounting.accountBalance]. Callers should pass the FULL ledger's balances
 *    (see [SplitAccounting.accountBalance] for the semantics); a month-scoped row list
 *    cannot produce a current balance.
 *
 * Transfer activity is volume moved between the user's own accounts — it is NOT income,
 * NOT expense, and NOT any account's balance.
 */
data class FinancialSummary(
    val totalIncome: Double,
    val grossExpense: Double,
    val splitReimbursements: Double,
    val netExpense: Double,
    val transferActivity: Double,
    val accountBalances: Map<String, Double>,
    /** Sum of every account's current balance — a statement's combined/net worth. */
    val combinedBalance: Double
) {
    companion object {
        /**
         * [transactions] scope the PERIOD METRICS only. [currentBalances] are the
         * authoritative current account balances (compute from the full ledger with
         * [SplitAccounting.accountBalance]); when omitted, balances fall back to being
         * derived from [transactions] themselves — correct only if that list is the
         * complete ledger.
         */
        fun of(
            transactions: List<TransactionEntity>,
            currentBalances: Map<String, Double> = emptyMap(),
            accountNames: List<String> = emptyList()
        ): FinancialSummary {
            val totalIncome = transactions.filter { it.type == com.example.hisab.data.model.TransactionType.INCOME }.sumOf { it.amount }
            val grossExpense = SplitAccounting.grossExpenseTotal(transactions)
            val splits = SplitAccounting.splitTotal(transactions)
            val transferActivity = transactions.filter { it.type == com.example.hisab.data.model.TransactionType.TRANSFER }.sumOf { it.amount }

            val names = currentBalances.keys.ifEmpty { accountNames }
            val balances: Map<String, Double> =
                if (currentBalances.isNotEmpty()) currentBalances
                else if (names.isNotEmpty()) names.associateWith { SplitAccounting.accountBalance(it, transactions) }
                else emptyMap()

            return FinancialSummary(
                totalIncome = totalIncome,
                grossExpense = grossExpense,
                splitReimbursements = splits,
                netExpense = grossExpense - splits,
                transferActivity = transferActivity,
                accountBalances = balances,
                combinedBalance = balances.values.sum()
            )
        }
    }
}
