package com.example.data.accounting

import com.example.data.AppDatabase
import com.example.data.model.Money
import com.example.data.model.Transaction

data class DailyFinancialStatement(
    val dateTimestamp: Long,
    val openingBalanceTotalMinor: Long,
    val closingBalanceTotalMinor: Long,
    val externalMoneyReceivedMinor: Long,
    val earnedIncomeMinor: Long,
    val loanProceedsMinor: Long,
    val externalMoneySentMinor: Long,
    val actualExpenditureMinor: Long,
    val loanRepaymentsMinor: Long,
    val cashWithdrawalsMinor: Long,
    val feesMinor: Long,
    val reversalsMinor: Long,
    val internalTransfersMinor: Long,
    val pendingReviewCount: Int,
    val discrepancyCount: Int,
    val netExternalCashFlowMinor: Long,
    val netEarnedIncomeMinor: Long,
    val netAccountMovementMinor: Long
) {
    val openingBalanceTotal: Double get() = Money.toZmw(openingBalanceTotalMinor)
    val closingBalanceTotal: Double get() = Money.toZmw(closingBalanceTotalMinor)
    val externalMoneyReceived: Double get() = Money.toZmw(externalMoneyReceivedMinor)
    val earnedIncome: Double get() = Money.toZmw(earnedIncomeMinor)
    val loanProceeds: Double get() = Money.toZmw(loanProceedsMinor)
    val externalMoneySent: Double get() = Money.toZmw(externalMoneySentMinor)
    val actualExpenditure: Double get() = Money.toZmw(actualExpenditureMinor)
    val loanRepayments: Double get() = Money.toZmw(loanRepaymentsMinor)
    val cashWithdrawals: Double get() = Money.toZmw(cashWithdrawalsMinor)
    val fees: Double get() = Money.toZmw(feesMinor)
    val reversals: Double get() = Money.toZmw(reversalsMinor)
    val internalTransfers: Double get() = Money.toZmw(internalTransfersMinor)
    val netExternalCashFlow: Double get() = Money.toZmw(netExternalCashFlowMinor)
    val netEarnedIncome: Double get() = Money.toZmw(netEarnedIncomeMinor)
    val netAccountMovement: Double get() = Money.toZmw(netAccountMovementMinor)
}

object ReportingEngine {

    suspend fun generateDailyStatement(database: AppDatabase, targetDate: Long = System.currentTimeMillis()): DailyFinancialStatement {
        val transactionDao = database.transactionDao()
        val accountDao = database.accountDao()
        val discrepancyDao = database.reconciliationDiscrepancyDao()

        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = targetDate
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis - 1

        val allTxns = transactionDao.getAllTransactions()
        val dayTxns = allTxns.filter { it.transactionDate in startOfDay..endOfDay && it.status == "SUCCESSFUL" }

        var received = 0L
        var earned = 0L
        var loans = 0L
        var sent = 0L
        var actualExpenses = 0L
        var loanRepayments = 0L
        var cashWithdrawals = 0L
        var fees = 0L
        var reversals = 0L
        var internalTransfers = 0L

        for (t in dayTxns) {
            fees += t.feeMinor
            when (t.transactionType) {
                "INCOMING_TRANSFER" -> {
                    if (t.isInternalTransfer) {
                        internalTransfers += t.amountMinor
                    } else {
                        received += t.amountMinor
                        val isSalaryOrBiz = t.categoryName.contains("salary", ignoreCase = true) || t.categoryName.contains("business", ignoreCase = true)
                        if (isSalaryOrBiz) earned += t.amountMinor
                    }
                }
                "LOAN_DISBURSEMENT" -> {
                    loans += t.amountMinor
                }
                "OUTGOING_TRANSFER" -> {
                    if (t.isInternalTransfer) {
                        internalTransfers += t.amountMinor
                    } else {
                        sent += t.amountMinor
                        actualExpenses += t.amountMinor
                    }
                }
                "MERCHANT_PAYMENT" -> {
                    sent += t.amountMinor
                    actualExpenses += t.amountMinor
                }
                "CASH_WITHDRAWAL" -> {
                    // Physical cash received: not counted as consumption
                    cashWithdrawals += t.amountMinor
                }
                "LOAN_REPAYMENT" -> {
                    loanRepayments += t.amountMinor
                }
                "REVERSAL", "REFUND" -> {
                    reversals += t.amountMinor
                }
            }
        }

        // Account balances
        val accounts = accountDao.getAllAccounts()
        var openingTotal = 0L
        for (a in accounts) {
            openingTotal += a.openingBalanceMinor
        }
        val airtelBal = ReconciliationEngine.calculateRunningBalance(database, "AIRTEL")
        val mtnBal = ReconciliationEngine.calculateRunningBalance(database, "MTN")
        val cashBal = ReconciliationEngine.calculateRunningBalance(database, "CASH")
        val closingTotal = airtelBal + mtnBal + cashBal

        val netExternalFlow = (received + loans + reversals) - (sent + loanRepayments + fees)
        val netEarned = (earned + received) - (actualExpenses + fees)
        val netMovement = closingTotal - openingTotal

        val pendingReview = allTxns.count { it.needsReview }
        val unresolvedDiscrepancies = discrepancyDao.getUnresolvedDiscrepancies().size

        return DailyFinancialStatement(
            dateTimestamp = startOfDay,
            openingBalanceTotalMinor = openingTotal,
            closingBalanceTotalMinor = closingTotal,
            externalMoneyReceivedMinor = received,
            earnedIncomeMinor = earned,
            loanProceedsMinor = loans,
            externalMoneySentMinor = sent,
            actualExpenditureMinor = actualExpenses,
            loanRepaymentsMinor = loanRepayments,
            cashWithdrawalsMinor = cashWithdrawals,
            feesMinor = fees,
            reversalsMinor = reversals,
            internalTransfersMinor = internalTransfers,
            pendingReviewCount = pendingReview,
            discrepancyCount = unresolvedDiscrepancies,
            netExternalCashFlowMinor = netExternalFlow,
            netEarnedIncomeMinor = netEarned,
            netAccountMovementMinor = netMovement
        )
    }
}
