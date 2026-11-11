package com.example.data.accounting

import com.example.data.AppDatabase
import com.example.data.model.AuditEvent
import com.example.data.model.Money
import com.example.data.model.ReconciliationDiscrepancy

data class AccountReconciliationStatus(
    val provider: String,
    val accountName: String,
    val openingBalanceMinor: Long,
    val totalDebitsMinor: Long,
    val totalCreditsMinor: Long,
    val calculatedBalanceMinor: Long,
    val lastReportedBalanceMinor: Long?,
    val discrepancyMinor: Long,
    val isReconciled: Boolean,
    val lastReconciledTime: Long?
) {
    val openingBalance: Double get() = Money.toZmw(openingBalanceMinor)
    val calculatedBalance: Double get() = Money.toZmw(calculatedBalanceMinor)
    val lastReportedBalance: Double? get() = lastReportedBalanceMinor?.let { Money.toZmw(it) }
    val discrepancy: Double get() = Money.toZmw(discrepancyMinor)
}

object ReconciliationEngine {

    suspend fun calculateRunningBalance(database: AppDatabase, provider: String): Long {
        val accountDao = database.accountDao()
        val journalDao = database.journalDao()

        val walletCode = AccountingEngine.getWalletAccountCode(provider)
        val account = accountDao.getAccountByCode(walletCode)
            ?: accountDao.getAccountByProvider(provider)
            ?: return 0L

        val debits = journalDao.getSumDebitsForAccount(account.id) ?: 0L
        val credits = journalDao.getSumCreditsForAccount(account.id) ?: 0L

        // In double-entry accounting for ASSET accounts:
        // Asset Balance = Opening Balance + Total Debits - Total Credits
        return account.openingBalanceMinor + debits - credits
    }

    suspend fun getAccountStatus(database: AppDatabase, provider: String): AccountReconciliationStatus? {
        val accountDao = database.accountDao()
        val journalDao = database.journalDao()

        val walletCode = AccountingEngine.getWalletAccountCode(provider)
        val account = accountDao.getAccountByCode(walletCode)
            ?: accountDao.getAccountByProvider(provider)
            ?: return null

        val debits = journalDao.getSumDebitsForAccount(account.id) ?: 0L
        val credits = journalDao.getSumCreditsForAccount(account.id) ?: 0L
        val calculated = account.openingBalanceMinor + debits - credits
        val reported = account.lastReportedBalanceMinor

        val discrepancy = if (reported != null) calculated - reported else 0L
        val isReconciled = reported == null || discrepancy == 0L

        return AccountReconciliationStatus(
            provider = provider,
            accountName = account.accountName,
            openingBalanceMinor = account.openingBalanceMinor,
            totalDebitsMinor = debits,
            totalCreditsMinor = credits,
            calculatedBalanceMinor = calculated,
            lastReportedBalanceMinor = reported,
            discrepancyMinor = discrepancy,
            isReconciled = isReconciled,
            lastReconciledTime = account.reconciledAt
        )
    }

    suspend fun auditBalanceAgainstSms(
        database: AppDatabase,
        provider: String,
        reportedBalanceMinor: Long,
        reference: String?,
        timestamp: Long
    ) {
        val calculated = calculateRunningBalance(database, provider)
        val discrepancyMinor = calculated - reportedBalanceMinor

        val discrepancyDao = database.reconciliationDiscrepancyDao()
        val auditEventDao = database.auditEventDao()

        if (discrepancyMinor != 0L) {
            discrepancyDao.insertDiscrepancy(
                ReconciliationDiscrepancy(
                    provider = provider,
                    reportedBalanceMinor = reportedBalanceMinor,
                    calculatedBalanceMinor = calculated,
                    discrepancyMinor = discrepancyMinor,
                    transactionReference = reference,
                    timestamp = timestamp,
                    isResolved = false,
                    resolutionNotes = "Discrepancy of ${Money.format(discrepancyMinor)} detected against reported balance"
                )
            )

            auditEventDao.insertAuditEvent(
                AuditEvent(
                    eventType = "DISCREPANCY_DETECTED",
                    recordTable = "reconciliation_discrepancies",
                    recordId = null,
                    details = "Provider $provider reported ${Money.format(reportedBalanceMinor)} but ledger calculated ${Money.format(calculated)} (difference: ${Money.format(discrepancyMinor)})"
                )
            )
        }
    }
}
