package com.example.data.accounting

import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.AuditEvent
import com.example.data.model.JournalEntry
import com.example.data.model.JournalLine
import com.example.data.model.Transaction

object AccountingEngine {

    // Standard Account Codes
    const val ASSET_AIRTEL = "ASSET_AIRTEL"
    const val ASSET_MTN = "ASSET_MTN"
    const val ASSET_CASH = "ASSET_CASH"

    const val LIAB_NASOVA = "LIAB_NASOVA"
    const val LIAB_FIKILIZA = "LIAB_FIKILIZA"
    const val LIAB_LOAN_GENERAL = "LIAB_LOAN_GENERAL"

    const val EQUITY_OPENING = "EQUITY_OPENING"
    const val EQUITY_RECONCILIATION = "EQUITY_RECONCILIATION"

    const val INC_TRANSFERS = "INC_TRANSFERS"
    const val INC_SALARY = "INC_SALARY"
    const val INC_BUSINESS = "INC_BUSINESS"
    const val INC_OTHER = "INC_OTHER"

    const val EXP_MERCHANT = "EXP_MERCHANT"
    const val EXP_P2P = "EXP_P2P"
    const val EXP_AIRTIME = "EXP_AIRTIME"
    const val EXP_DATA = "EXP_DATA"
    const val EXP_UTILITIES = "EXP_UTILITIES"
    const val EXP_FEES = "EXP_FEES"
    const val EXP_GENERAL = "EXP_GENERAL"

    const val CLEARING_TRANSFER = "CLEARING_TRANSFER"
    const val CLEARING_LOAN_REPAYMENT = "CLEARING_LOAN_REPAYMENT"

    fun getWalletAccountCode(provider: String): String {
        return when (provider.uppercase()) {
            "AIRTEL" -> ASSET_AIRTEL
            "MTN" -> ASSET_MTN
            "CASH" -> ASSET_CASH
            else -> "ASSET_${provider.uppercase()}"
        }
    }

    private fun getExpenseAccountCode(categoryName: String): String {
        val lower = categoryName.lowercase()
        return when {
            lower.contains("data") || lower.contains("bundle") || lower.contains("internet") -> EXP_DATA
            lower.contains("airtime") -> EXP_AIRTIME
            lower.contains("merchant") -> EXP_MERCHANT
            lower.contains("p2p") || lower.contains("person") || lower.contains("sent") -> EXP_P2P
            lower.contains("utility") || lower.contains("bill") || lower.contains("electricity") -> EXP_UTILITIES
            lower.contains("fee") || lower.contains("charge") -> EXP_FEES
            else -> EXP_GENERAL
        }
    }

    private fun getIncomeAccountCode(categoryName: String): String {
        val lower = categoryName.lowercase()
        return when {
            lower.contains("salary") || lower.contains("wage") -> INC_SALARY
            lower.contains("business") || lower.contains("sales") -> INC_BUSINESS
            else -> INC_TRANSFERS
        }
    }

    /**
     * Posts double-entry journal lines for a transaction.
     * Enforces the fundamental invariant: Total Debits == Total Credits.
     */
    suspend fun postTransaction(database: AppDatabase, transaction: Transaction): JournalEntry? {
        if (transaction.status == "FAILED" || transaction.direction == "NONE" || transaction.amountMinor <= 0L) {
            // Failed or balance-check transactions produce zero journal lines
            return null
        }

        val journalDao = database.journalDao()
        val accountDao = database.accountDao()
        val auditEventDao = database.auditEventDao()

        // Ensure wallet accounts exist
        ensureAccountExists(accountDao, ASSET_AIRTEL, "Airtel Money Zambia", "ASSET")
        ensureAccountExists(accountDao, ASSET_MTN, "MTN Mobile Money Zambia", "ASSET")
        ensureAccountExists(accountDao, ASSET_CASH, "Cash in Hand", "ASSET")

        val walletAccountCode = getWalletAccountCode(transaction.provider)
        val walletAccount = accountDao.getAccountByCode(walletAccountCode)
            ?: accountDao.getAccountByProvider(transaction.provider)
            ?: return null

        val journalEntry = JournalEntry(
            transactionId = transaction.id,
            entryDate = transaction.transactionDate,
            description = transaction.description ?: "${transaction.transactionType} via ${transaction.provider}",
            sourceReference = transaction.transactionReference
        )

        val entryId = journalDao.insertJournalEntry(journalEntry)
        val lines = mutableListOf<JournalLine>()

        when (transaction.transactionType.uppercase()) {
            "INCOMING_TRANSFER" -> {
                if (transaction.isInternalTransfer) {
                    // Internal transfer incoming side
                    val clearingAccount = ensureAccountExists(accountDao, CLEARING_TRANSFER, "Internal Transfer Clearing", "ASSET")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Received internal transfer"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = clearingAccount.id, accountCode = CLEARING_TRANSFER, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Transfer clearing credit"))
                } else {
                    // External income
                    val incomeCode = getIncomeAccountCode(transaction.categoryName)
                    val incomeAccount = ensureAccountExists(accountDao, incomeCode, transaction.categoryName, "INCOME")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Wallet credited"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = incomeAccount.id, accountCode = incomeCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Income recognized"))
                }
            }

            "OUTGOING_TRANSFER", "MERCHANT_PAYMENT" -> {
                if (transaction.isInternalTransfer) {
                    // Internal transfer outgoing side
                    val clearingAccount = ensureAccountExists(accountDao, CLEARING_TRANSFER, "Internal Transfer Clearing", "ASSET")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = clearingAccount.id, accountCode = CLEARING_TRANSFER, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Transfer clearing debit"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Wallet debited"))
                } else {
                    // External expenditure
                    val expCode = getExpenseAccountCode(transaction.categoryName)
                    val expAccount = ensureAccountExists(accountDao, expCode, transaction.categoryName, "EXPENSE")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = expAccount.id, accountCode = expCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Expense recognized"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Wallet debited"))
                }
            }

            "CASH_WITHDRAWAL" -> {
                // Physical Cash Received: Debit Cash in Hand asset, Credit Mobile Money wallet asset.
                // It is NOT recognized as consumption until spent!
                val cashAccount = ensureAccountExists(accountDao, ASSET_CASH, "Cash in Hand", "ASSET")
                lines.add(JournalLine(journalEntryId = entryId, accountId = cashAccount.id, accountCode = ASSET_CASH, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Cash received into physical wallet"))
                lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Mobile money wallet withdrawal"))
            }

            "CASH_DEPOSIT" -> {
                val cashAccount = ensureAccountExists(accountDao, ASSET_CASH, "Cash in Hand", "ASSET")
                lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Mobile money wallet deposit"))
                lines.add(JournalLine(journalEntryId = entryId, accountId = cashAccount.id, accountCode = ASSET_CASH, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Cash deposited from physical wallet"))
            }

            "LOAN_DISBURSEMENT" -> {
                // Loan proceeds: Debit Mobile Money asset, Credit Loan liability.
                // It is NEVER recognized as earned income!
                val liabCode = when {
                    transaction.sender?.contains("FIKILIZA", ignoreCase = true) == true -> LIAB_FIKILIZA
                    transaction.sender?.contains("NASOVA", ignoreCase = true) == true -> LIAB_NASOVA
                    else -> LIAB_LOAN_GENERAL
                }
                val liabAccount = ensureAccountExists(accountDao, liabCode, transaction.sender ?: "Loan Liability", "LIABILITY")
                lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Loan funds received into wallet"))
                lines.add(JournalLine(journalEntryId = entryId, accountId = liabAccount.id, accountCode = liabCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Loan liability incurred"))
            }

            "LOAN_REPAYMENT" -> {
                val liabCode = when {
                    transaction.recipient?.contains("FIKILIZA", ignoreCase = true) == true -> LIAB_FIKILIZA
                    transaction.recipient?.contains("NASOVA", ignoreCase = true) == true -> LIAB_NASOVA
                    else -> CLEARING_LOAN_REPAYMENT
                }
                val liabAccount = ensureAccountExists(accountDao, liabCode, "Loan Repayment", "LIABILITY")
                lines.add(JournalLine(journalEntryId = entryId, accountId = liabAccount.id, accountCode = liabCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Loan liability principal reduction"))
                lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Wallet debited for loan repayment"))
            }

            "REVERSAL", "REFUND" -> {
                // Reversal: Reverse previous financial impact
                val expCode = getExpenseAccountCode(transaction.categoryName)
                val expAccount = ensureAccountExists(accountDao, expCode, transaction.categoryName, "EXPENSE")
                lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Wallet re-credited via reversal"))
                lines.add(JournalLine(journalEntryId = entryId, accountId = expAccount.id, accountCode = expCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Expense reversed"))
            }

            else -> {
                // Default fallback
                if (transaction.direction == "CREDIT") {
                    val incomeAccount = ensureAccountExists(accountDao, INC_OTHER, "Other Income", "INCOME")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Credit"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = incomeAccount.id, accountCode = INC_OTHER, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Income"))
                } else {
                    val expAccount = ensureAccountExists(accountDao, EXP_GENERAL, "General Expense", "EXPENSE")
                    lines.add(JournalLine(journalEntryId = entryId, accountId = expAccount.id, accountCode = EXP_GENERAL, debitMinor = transaction.amountMinor, creditMinor = 0L, description = "Expense"))
                    lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.amountMinor, description = "Debit"))
                }
            }
        }

        // Handle fee line if fee > 0
        if (transaction.feeMinor > 0L) {
            val feeAccount = ensureAccountExists(accountDao, EXP_FEES, "Transaction Fees", "EXPENSE")
            lines.add(JournalLine(journalEntryId = entryId, accountId = feeAccount.id, accountCode = EXP_FEES, debitMinor = transaction.feeMinor, creditMinor = 0L, description = "Transaction fee charge"))
            lines.add(JournalLine(journalEntryId = entryId, accountId = walletAccount.id, accountCode = walletAccountCode, debitMinor = 0L, creditMinor = transaction.feeMinor, description = "Wallet fee debit"))
        }

        // STRICT INVARIANT ENFORCEMENT: Total Debits MUST equal Total Credits
        val totalDebits = lines.sumOf { it.debitMinor }
        val totalCredits = lines.sumOf { it.creditMinor }
        check(totalDebits == totalCredits) {
            "Accounting Invariant Violation: Debits ($totalDebits) != Credits ($totalCredits) for Txn ${transaction.id}"
        }

        journalDao.insertJournalLines(lines)

        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "JOURNAL_POSTED",
                recordTable = "journal_entries",
                recordId = entryId,
                details = "Posted ${lines.size} journal lines for TID ${transaction.transactionReference ?: transaction.id} ($totalDebits minor units)"
            )
        )

        return journalEntry.copy(id = entryId)
    }

    private suspend fun ensureAccountExists(
        accountDao: com.example.data.dao.AccountDao,
        code: String,
        name: String,
        type: String
    ): Account {
        val existing = accountDao.getAccountByCode(code)
        if (existing != null) return existing
        val newAccount = Account(
            provider = if (code.startsWith("ASSET_")) code.removePrefix("ASSET_") else "INTERNAL",
            accountCode = code,
            accountName = name,
            accountType = type,
            openingBalanceMinor = 0L
        )
        val id = accountDao.insertAccount(newAccount)
        return newAccount.copy(id = id)
    }
}
