package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.accounting.AccountingEngine
import com.example.data.accounting.ReconciliationEngine
import com.example.data.model.Account
import com.example.data.model.AuditEvent
import com.example.data.model.Category
import com.example.data.model.InternalTransfer
import com.example.data.model.JournalEntry
import com.example.data.model.JournalLine
import com.example.data.model.Money
import com.example.data.model.ReconciliationDiscrepancy
import com.example.data.model.SmsLog
import com.example.data.model.Transaction
import com.example.data.model.TransactionRelationship
import com.example.parser.ParsedTransaction
import com.example.parser.SmsParsingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

sealed class IngestionResult {
    data class Success(val transaction: Transaction, val isInternalTransfer: Boolean) : IngestionResult()
    data class Merged(val transaction: Transaction, val reason: String) : IngestionResult()
    data class BalanceUpdated(val provider: String, val newBalanceMinor: Long) : IngestionResult() {
        val newBalance: Double get() = Money.toZmw(newBalanceMinor)
    }
    data class Duplicate(val reason: String) : IngestionResult()
    data class Failed(val error: String) : IngestionResult()
    data class ReviewNeeded(val transaction: Transaction) : IngestionResult()
}

class TransactionRepository(private val database: AppDatabase) {

    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()
    private val journalDao = database.journalDao()
    private val transactionRelationshipDao = database.transactionRelationshipDao()
    private val auditEventDao = database.auditEventDao()
    private val discrepancyDao = database.reconciliationDiscrepancyDao()
    private val categoryDao = database.categoryDao()
    private val smsLogDao = database.smsLogDao()
    private val internalTransferDao = database.internalTransferDao()
    private val appSettingDao = database.appSettingDao()

    val accountsFlow: Flow<List<Account>> = accountDao.getAllAccountsFlow()
    val transactionsFlow: Flow<List<Transaction>> = transactionDao.getAllTransactionsFlow()
    val categoriesFlow: Flow<List<Category>> = categoryDao.getAllCategoriesFlow()
    val smsLogsFlow: Flow<List<SmsLog>> = smsLogDao.getAllLogsFlow()
    val internalTransfersFlow: Flow<List<InternalTransfer>> = internalTransferDao.getAllTransfersFlow()
    val reviewNeededFlow: Flow<List<Transaction>> = transactionDao.getTransactionsNeedingReviewFlow()
    val discrepanciesFlow: Flow<List<ReconciliationDiscrepancy>> = discrepancyDao.getUnresolvedDiscrepanciesFlow()
    val auditEventsFlow: Flow<List<AuditEvent>> = auditEventDao.getAllAuditEventsFlow()

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        accountDao.getAllAccounts()
    }

    suspend fun getTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        transactionDao.getAllTransactions()
    }

    suspend fun updateOpeningBalance(provider: String, balance: Double) = withContext(Dispatchers.IO) {
        val minor = Money.fromZmw(balance)
        accountDao.updateOpeningBalanceMinor(provider, minor)
        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "OPENING_BALANCE_UPDATED",
                recordTable = "accounts",
                recordId = null,
                details = "Opening balance for $provider set to ${Money.format(minor)}"
            )
        )
    }

    suspend fun reconcileAccount(provider: String, targetBalance: Double) = withContext(Dispatchers.IO) {
        val minor = Money.fromZmw(targetBalance)
        accountDao.updateLastReportedBalanceMinor(provider, minor, System.currentTimeMillis())
        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "RECONCILED",
                recordTable = "accounts",
                recordId = null,
                details = "Account $provider manually reconciled to target balance ${Money.format(minor)}"
            )
        )
    }

    suspend fun insertTransaction(transaction: Transaction): Long = withContext(Dispatchers.IO) {
        val id = transactionDao.insertTransaction(transaction)
        val saved = transaction.copy(id = id)

        // Post balanced double-entry journal
        AccountingEngine.postTransaction(database, saved)

        if (transaction.balanceAfterMinor != null && transaction.status == "SUCCESSFUL") {
            accountDao.updateLastReportedBalanceMinor(transaction.provider, transaction.balanceAfterMinor, transaction.transactionDate)
            ReconciliationEngine.auditBalanceAgainstSms(database, transaction.provider, transaction.balanceAfterMinor, transaction.transactionReference, transaction.transactionDate)
        }
        id
    }

    suspend fun updateTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
        // Refresh journal entries if modified
        journalDao.deleteJournalForTransaction(transaction.id)
        AccountingEngine.postTransaction(database, transaction)
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        journalDao.deleteJournalForTransaction(id)
        transactionDao.deleteTransactionById(id)
        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "TRANSACTION_DELETED",
                recordTable = "transactions",
                recordId = id,
                details = "Deleted transaction ID $id and corresponding journal lines"
            )
        )
    }

    suspend fun resolveDiscrepancy(discrepancyId: Long, notes: String) = withContext(Dispatchers.IO) {
        discrepancyDao.resolveDiscrepancy(discrepancyId, notes)
    }

    suspend fun toggleInternalTransfer(transactionId: Long) = withContext(Dispatchers.IO) {
        val txn = transactionDao.getTransactionById(transactionId) ?: return@withContext
        val updated = txn.copy(
            isInternalTransfer = !txn.isInternalTransfer,
            categoryName = if (!txn.isInternalTransfer) "Internal Account Transfer" else "Person-to-Person Transfer"
        )
        transactionDao.updateTransaction(updated)
        // Re-post journal entry with updated internal transfer semantics
        journalDao.deleteJournalForTransaction(transactionId)
        AccountingEngine.postTransaction(database, updated)
    }

    suspend fun resolveReview(transactionId: Long, approved: Boolean, correctedCategory: String? = null) = withContext(Dispatchers.IO) {
        val txn = transactionDao.getTransactionById(transactionId) ?: return@withContext
        if (approved) {
            val updated = txn.copy(
                needsReview = false,
                reviewReason = null,
                status = "SUCCESSFUL",
                categoryName = correctedCategory ?: txn.categoryName
            )
            transactionDao.updateTransaction(updated)
            journalDao.deleteJournalForTransaction(transactionId)
            AccountingEngine.postTransaction(database, updated)
        } else {
            val updated = txn.copy(
                needsReview = false,
                reviewReason = "Rejected by user",
                status = "REJECTED"
            )
            transactionDao.updateTransaction(updated)
            journalDao.deleteJournalForTransaction(transactionId)
        }
    }

    suspend fun getJournalLinesForTransaction(transactionId: Long): List<JournalLine> = withContext(Dispatchers.IO) {
        val entry = journalDao.getJournalEntryForTransaction(transactionId) ?: return@withContext emptyList()
        journalDao.getJournalLinesForEntry(entry.id)
    }

    suspend fun postCompensatingReversal(transactionId: Long, reason: String): Transaction? = withContext(Dispatchers.IO) {
        val original = transactionDao.getTransactionById(transactionId) ?: return@withContext null
        if (original.status == "REVERSED") return@withContext original

        val reversedRef = "REV_${original.transactionReference ?: original.id}"
        val revDirection = if (original.direction == "DEBIT") "CREDIT" else "DEBIT"

        // Mark original as REVERSED
        val updatedOriginal = original.copy(
            status = "REVERSED",
            reviewReason = "Compensating adjustment posted: $reason"
        )
        transactionDao.updateTransaction(updatedOriginal)

        // Create the compensating transaction
        val revTxn = Transaction(
            accountId = original.accountId,
            provider = original.provider,
            transactionReference = reversedRef,
            originalTransactionReference = original.transactionReference ?: original.id.toString(),
            transactionType = "REVERSAL",
            direction = revDirection,
            status = "SUCCESSFUL",
            categoryName = "Transaction Reversal",
            amountMinor = original.amountMinor,
            currency = original.currency,
            feeMinor = 0L,
            sender = if (original.direction == "DEBIT") (original.recipient ?: "Reversal Counterparty") else original.sender,
            recipient = if (original.direction == "DEBIT") original.sender else (original.recipient ?: "Reversal Counterparty"),
            merchantOrAgent = original.merchantOrAgent,
            description = "Compensating Reversal for #${original.id}: $reason",
            transactionDate = System.currentTimeMillis(),
            detectedAt = System.currentTimeMillis(),
            balanceAfterMinor = null,
            rawSms = "MANUAL_COMPENSATING_ADJUSTMENT for #${original.id}: $reason",
            classificationConfidence = 1.0f,
            isInternalTransfer = false,
            transferGroupId = null,
            needsReview = false,
            reviewReason = null
        )

        val revId = transactionDao.insertTransaction(revTxn)
        val finalRevTxn = revTxn.copy(id = revId)

        // Link with REVERSAL_REFUND relationship
        transactionRelationshipDao.insertRelationship(
            TransactionRelationship(
                relationshipType = "REVERSAL_REFUND",
                sourceTransactionId = revId,
                targetTransactionId = original.id,
                matchingEvidence = "Manual Compensating Reversal: $reason",
                confidence = 1.0f,
                status = "CONFIRMED_USER"
            )
        )

        // Post balanced double-entry journal lines
        AccountingEngine.postTransaction(database, finalRevTxn)

        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "TRANSACTION_REVERSED",
                recordTable = "transactions",
                recordId = original.id,
                details = "Compensating reversal #$revId posted for #${original.id}: $reason"
            )
        )

        finalRevTxn
    }

    suspend fun processIncomingSms(sender: String?, body: String): IngestionResult = withContext(Dispatchers.IO) {
        val hash = SmsParsingEngine.hashMessage(sender, body)

        // 1. Check exact duplicate SMS log (Level 1)
        val existingLog = smsLogDao.getLogByHash(hash)
        if (existingLog != null) {
            return@withContext IngestionResult.Duplicate("SMS already processed previously (hash: ${hash.take(8)})")
        }

        // Store raw message before processing
        smsLogDao.insertLog(
            SmsLog(
                messageHash = hash,
                provider = sender ?: "UNKNOWN",
                sender = sender ?: "",
                processingStatus = "PARSING",
                errorMessage = null,
                rawBody = body
            )
        )

        // 2. Parse SMS with specialized parsers
        val parsed: ParsedTransaction? = SmsParsingEngine.parse(sender, body)
        if (parsed == null) {
            smsLogDao.insertLog(
                SmsLog(
                    messageHash = hash,
                    provider = sender ?: "UNKNOWN",
                    sender = sender ?: "",
                    processingStatus = "FAILED_PROCESSING",
                    errorMessage = "Unrecognized SMS format or pattern",
                    rawBody = body
                )
            )
            return@withContext IngestionResult.Failed("Unrecognized transaction format from SMS")
        }

        // 3. Handle Balance-Only Notifications (Evidence of balance, zero ledger distortion)
        if (parsed.transactionType == "BALANCE_UPDATE") {
            if (parsed.balanceAfterMinor != null) {
                accountDao.updateLastReportedBalanceMinor(parsed.provider, parsed.balanceAfterMinor, parsed.transactionDate)
                ReconciliationEngine.auditBalanceAgainstSms(database, parsed.provider, parsed.balanceAfterMinor, null, parsed.transactionDate)
            }
            smsLogDao.insertLog(
                SmsLog(
                    messageHash = hash,
                    provider = parsed.provider,
                    sender = sender ?: "",
                    processingStatus = "POSTED",
                    errorMessage = null,
                    rawBody = body
                )
            )
            return@withContext IngestionResult.BalanceUpdated(parsed.provider, parsed.balanceAfterMinor ?: 0L)
        }

        // 4. Handle Failed Transactions (Audit preserved, zero balance impact, direction NONE)
        if (parsed.status == "FAILED" || parsed.transactionType == "FAILED") {
            val account = accountDao.getAccountByProvider(parsed.provider)
            val failedTxn = Transaction(
                accountId = account?.id ?: 0L,
                provider = parsed.provider,
                transactionReference = parsed.transactionReference,
                transactionType = "FAILED",
                direction = "NONE",
                status = "FAILED",
                categoryName = "Failed Transaction",
                amountMinor = 0L,
                description = parsed.description ?: "Transaction Failed",
                transactionDate = parsed.transactionDate,
                detectedAt = System.currentTimeMillis(),
                rawSms = parsed.rawSms,
                classificationConfidence = parsed.confidence,
                isInternalTransfer = false
            )
            val id = transactionDao.insertTransaction(failedTxn)
            smsLogDao.insertLog(
                SmsLog(
                    messageHash = hash,
                    provider = parsed.provider,
                    sender = sender ?: "",
                    processingStatus = "POSTED",
                    errorMessage = null,
                    rawBody = body
                )
            )
            return@withContext IngestionResult.Success(failedTxn.copy(id = id), false)
        }

        // 5. Strict Deduplication by Provider + Reference (Level 2)
        if (!parsed.transactionReference.isNullOrBlank()) {
            val existing = transactionDao.getTransactionByReference(parsed.provider, parsed.transactionReference)
            if (existing != null) {
                // Synthesize companion SMS details (e.g. replace technical NFS Till with actual Person recipient)
                val mergedRecipient = when {
                    !parsed.recipient.isNullOrBlank() && (existing.recipient.isNullOrBlank() || existing.recipient.contains("SETTLEMENT", ignoreCase = true)) -> parsed.recipient
                    else -> existing.recipient ?: parsed.recipient
                }
                val mergedSender = when {
                    !parsed.sender.isNullOrBlank() && (existing.sender.isNullOrBlank() || existing.sender == "AIRTEL NFS") -> parsed.sender
                    else -> existing.sender ?: parsed.sender
                }
                val mergedMerchantOrAgent = existing.merchantOrAgent ?: parsed.merchantOrAgent ?: parsed.recipient
                val mergedDescription = when {
                    !parsed.description.isNullOrBlank() && existing.description.isNullOrBlank() -> parsed.description
                    else -> existing.description
                }
                val mergedBalance = parsed.balanceAfterMinor ?: existing.balanceAfterMinor
                val mergedFee = if (existing.feeMinor <= 0L && parsed.feeMinor > 0L) parsed.feeMinor else existing.feeMinor

                val merged = existing.copy(
                    recipient = mergedRecipient,
                    sender = mergedSender,
                    merchantOrAgent = mergedMerchantOrAgent,
                    description = mergedDescription,
                    balanceAfterMinor = mergedBalance,
                    feeMinor = mergedFee
                )
                transactionDao.updateTransaction(merged)

                if (parsed.balanceAfterMinor != null) {
                    accountDao.updateLastReportedBalanceMinor(parsed.provider, parsed.balanceAfterMinor, parsed.transactionDate)
                    ReconciliationEngine.auditBalanceAgainstSms(database, parsed.provider, parsed.balanceAfterMinor, parsed.transactionReference, parsed.transactionDate)
                }

                smsLogDao.insertLog(
                    SmsLog(
                        messageHash = hash,
                        provider = parsed.provider,
                        sender = sender ?: "",
                        processingStatus = "POSTED",
                        errorMessage = "Merged companion details for TID ${parsed.transactionReference}",
                        rawBody = body
                    )
                )

                // Refresh journal entry with merged details
                journalDao.deleteJournalForTransaction(merged.id)
                AccountingEngine.postTransaction(database, merged)

                return@withContext IngestionResult.Merged(merged, "Merged with existing TID ${parsed.transactionReference}")
            }
        } else {
            // Level 3: Semantic deduplication check when transaction reference is missing
            val window = 5 * 60 * 1000L
            val dup = transactionDao.findPotentialDuplicate(
                parsed.provider,
                parsed.amountMinor,
                parsed.transactionDate - window,
                parsed.transactionDate + window
            )
            if (dup != null && dup.amountMinor == parsed.amountMinor) {
                smsLogDao.insertLog(
                    SmsLog(
                        messageHash = hash,
                        provider = parsed.provider,
                        sender = sender ?: "",
                        processingStatus = "REQUIRES_REVIEW",
                        errorMessage = "Potential duplicate amount ${Money.format(parsed.amountMinor)} without distinct TID",
                        rawBody = body
                    )
                )
                val account = accountDao.getAccountByProvider(parsed.provider)
                val reviewTxn = Transaction(
                    accountId = account?.id ?: 0L,
                    provider = parsed.provider,
                    transactionReference = parsed.transactionReference,
                    transactionType = parsed.transactionType,
                    direction = parsed.direction,
                    status = "REVIEW_NEEDED",
                    categoryName = parsed.categoryName,
                    amountMinor = parsed.amountMinor,
                    feeMinor = parsed.feeMinor,
                    sender = parsed.sender,
                    recipient = parsed.recipient,
                    merchantOrAgent = parsed.merchantOrAgent,
                    description = parsed.description,
                    transactionDate = parsed.transactionDate,
                    balanceAfterMinor = parsed.balanceAfterMinor,
                    rawSms = parsed.rawSms,
                    classificationConfidence = 0.5f,
                    needsReview = true,
                    reviewReason = "Probable duplicate: exact amount within 5 min window"
                )
                val id = transactionDao.insertTransaction(reviewTxn)
                return@withContext IngestionResult.ReviewNeeded(reviewTxn.copy(id = id))
            }
        }

        // 6. Handle Reversals & Relationships
        var originalTxnId: Long? = null
        if (parsed.transactionType == "REVERSAL" && !parsed.originalTransactionReference.isNullOrBlank()) {
            val original = transactionDao.getTransactionByReference(parsed.provider, parsed.originalTransactionReference)
            if (original != null) {
                originalTxnId = original.id
                transactionDao.updateTransaction(original.copy(status = "REVERSED"))
            }
        }

        // 7. Level 4: Cross-Network Internal Transfer Matching (Airtel <-> MTN via NFS)
        val oppositeProvider = if (parsed.provider == "AIRTEL") "MTN" else "AIRTEL"
        var isInternal = parsed.isPotentialInternalTransfer
        var transferGroupId: String? = null

        var candidateOpposite: Transaction? = null
        if (!parsed.transactionReference.isNullOrBlank()) {
            candidateOpposite = transactionDao.findMatchingTransferByReferenceOrNote(oppositeProvider, parsed.transactionReference)
        }
        if (candidateOpposite == null && !parsed.description.isNullOrBlank()) {
            candidateOpposite = transactionDao.findMatchingTransferByReferenceOrNote(oppositeProvider, parsed.description)
        }
        if (candidateOpposite == null) {
            val window = 20 * 60 * 1000L
            candidateOpposite = transactionDao.findPotentialOppositeTransfer(
                oppositeProvider,
                parsed.amountMinor,
                parsed.transactionDate - window,
                parsed.transactionDate + window
            )
        }

        val account = accountDao.getAccountByProvider(parsed.provider)
        val accountId = account?.id ?: 0L

        val transaction = Transaction(
            accountId = accountId,
            provider = parsed.provider,
            transactionReference = parsed.transactionReference,
            transactionType = parsed.transactionType,
            direction = parsed.direction,
            status = if (parsed.requiresReview) "REVIEW_NEEDED" else "SUCCESSFUL",
            categoryName = parsed.categoryName,
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            feeMinor = parsed.feeMinor,
            sender = parsed.sender,
            recipient = parsed.recipient,
            merchantOrAgent = parsed.merchantOrAgent,
            description = parsed.description,
            originalTransactionReference = parsed.originalTransactionReference,
            transactionDate = parsed.transactionDate,
            detectedAt = System.currentTimeMillis(),
            balanceAfterMinor = parsed.balanceAfterMinor,
            rawSms = parsed.rawSms,
            classificationConfidence = parsed.confidence,
            isInternalTransfer = false,
            transferGroupId = null,
            needsReview = parsed.requiresReview,
            reviewReason = parsed.reviewReason
        )

        val insertedId = transactionDao.insertTransaction(transaction)

        if (candidateOpposite != null && (isInternal || candidateOpposite.recipient?.contains("NFS", ignoreCase = true) == true || candidateOpposite.sender?.contains("NFS", ignoreCase = true) == true || candidateOpposite.description == parsed.transactionReference)) {
            isInternal = true
            val groupId = "TRANSFER_${parsed.transactionReference ?: candidateOpposite.transactionReference ?: System.currentTimeMillis()}"
            transferGroupId = groupId

            val updatedCurrent = transaction.copy(
                id = insertedId,
                isInternalTransfer = true,
                transferGroupId = groupId,
                categoryName = "Internal Account Transfer"
            )
            transactionDao.updateTransaction(updatedCurrent)

            val updatedOpposite = candidateOpposite.copy(
                isInternalTransfer = true,
                transferGroupId = groupId,
                categoryName = "Internal Account Transfer"
            )
            transactionDao.updateTransaction(updatedOpposite)

            internalTransferDao.insertTransfer(
                InternalTransfer(
                    sourceTransactionId = if (parsed.direction == "DEBIT") insertedId else candidateOpposite.id,
                    destinationTransactionId = if (parsed.direction == "CREDIT") insertedId else candidateOpposite.id,
                    amountMinor = parsed.amountMinor,
                    status = "MATCHED"
                )
            )

            transactionRelationshipDao.insertRelationship(
                TransactionRelationship(
                    relationshipType = "INTERNAL_TRANSFER",
                    sourceTransactionId = if (parsed.direction == "DEBIT") insertedId else candidateOpposite.id,
                    targetTransactionId = if (parsed.direction == "CREDIT") insertedId else candidateOpposite.id,
                    matchingEvidence = "NFS Cross-network match (${parsed.provider} <-> $oppositeProvider)",
                    confidence = 0.98f,
                    status = "CONFIRMED_AUTO"
                )
            )
        }

        // Post balanced Double-Entry journal entries
        val finalTxn = transaction.copy(
            id = insertedId,
            isInternalTransfer = isInternal,
            transferGroupId = transferGroupId
        )
        AccountingEngine.postTransaction(database, finalTxn)

        // Record relationships for Reversals, Loans, Cash withdrawals
        if (parsed.transactionType == "REVERSAL" && originalTxnId != null) {
            transactionRelationshipDao.insertRelationship(
                TransactionRelationship(
                    relationshipType = "REVERSAL_REFUND",
                    sourceTransactionId = insertedId,
                    targetTransactionId = originalTxnId,
                    matchingEvidence = "Reversal TID ${parsed.originalTransactionReference}",
                    confidence = 1.0f,
                    status = "CONFIRMED_AUTO"
                )
            )
        } else if (parsed.transactionType == "CASH_WITHDRAWAL") {
            transactionRelationshipDao.insertRelationship(
                TransactionRelationship(
                    relationshipType = "CASH_WITHDRAWAL_DUAL_ENTRY",
                    sourceTransactionId = insertedId,
                    targetTransactionId = null,
                    matchingEvidence = "Mobile Money to Cash in Hand wallet",
                    confidence = 1.0f,
                    status = "CONFIRMED_AUTO"
                )
            )
        }

        // Update provider reported balance & check reconciliation discrepancy
        if (parsed.balanceAfterMinor != null) {
            accountDao.updateLastReportedBalanceMinor(parsed.provider, parsed.balanceAfterMinor, parsed.transactionDate)
            ReconciliationEngine.auditBalanceAgainstSms(database, parsed.provider, parsed.balanceAfterMinor, parsed.transactionReference, parsed.transactionDate)
        }

        // Log success in sms_processing
        smsLogDao.insertLog(
            SmsLog(
                messageHash = hash,
                provider = parsed.provider,
                sender = sender ?: "",
                processingStatus = "POSTED",
                errorMessage = null,
                rawBody = body
            )
        )

        auditEventDao.insertAuditEvent(
            AuditEvent(
                eventType = "TRANSACTION_POSTED",
                recordTable = "transactions",
                recordId = insertedId,
                details = "Posted ${finalTxn.transactionType} for ${Money.format(finalTxn.amountMinor)} (Ref: ${finalTxn.transactionReference ?: "N/A"})"
            )
        )

        if (parsed.requiresReview) {
            IngestionResult.ReviewNeeded(finalTxn)
        } else {
            IngestionResult.Success(finalTxn, isInternal)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        transactionDao.deleteAllTransactions()
        smsLogDao.deleteAllLogs()
        accountDao.updateOpeningBalanceMinor("AIRTEL", 0L)
        accountDao.updateOpeningBalanceMinor("MTN", 0L)
        accountDao.updateOpeningBalanceMinor("CASH", 0L)
        accountDao.updateLastReportedBalanceMinor("AIRTEL", 0L, System.currentTimeMillis())
        accountDao.updateLastReportedBalanceMinor("MTN", 0L, System.currentTimeMillis())
        accountDao.updateLastReportedBalanceMinor("CASH", 0L, System.currentTimeMillis())
    }

    suspend fun exportToCsv(): String = withContext(Dispatchers.IO) {
        val txns = transactionDao.getAllTransactions()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val sb = StringBuilder()
        sb.append("ID,Date,Provider,Type,Direction,Status,Category,Amount(ZMW),Fee(ZMW),Sender/Recipient,Balance After(ZMW),Reference,Internal Transfer,Raw SMS\n")
        for (t in txns) {
            val dateStr = sdf.format(Date(t.transactionDate))
            val counterparty = (t.recipient ?: t.sender ?: t.merchantOrAgent ?: "").replace(",", ";")
            val raw = (t.rawSms ?: "").replace("\"", "\"\"").replace("\n", " ")
            val balAfterStr = if (t.balanceAfterMinor != null) Money.format(t.balanceAfterMinor) else ""
            sb.append("${t.id},\"$dateStr\",${t.provider},${t.transactionType},${t.direction},${t.status},\"${t.categoryName}\",${Money.toZmw(t.amountMinor)},${Money.toZmw(t.feeMinor)},\"$counterparty\",\"$balAfterStr\",\"${t.transactionReference ?: ""}\",${t.isInternalTransfer},\"$raw\"\n")
        }
        sb.toString()
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val txns = transactionDao.getAllTransactions()
        val accounts = accountDao.getAllAccounts()
        val root = JSONObject()

        val accArray = JSONArray()
        for (a in accounts) {
            val obj = JSONObject()
            obj.put("provider", a.provider)
            obj.put("accountName", a.accountName)
            obj.put("openingBalanceMinor", a.openingBalanceMinor)
            obj.put("lastReportedBalanceMinor", a.lastReportedBalanceMinor ?: JSONObject.NULL)
            accArray.put(obj)
        }
        root.put("accounts", accArray)

        val txnArray = JSONArray()
        for (t in txns) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("provider", t.provider)
            obj.put("reference", t.transactionReference ?: JSONObject.NULL)
            obj.put("type", t.transactionType)
            obj.put("direction", t.direction)
            obj.put("status", t.status)
            obj.put("category", t.categoryName)
            obj.put("amountMinor", t.amountMinor)
            obj.put("feeMinor", t.feeMinor)
            obj.put("sender", t.sender ?: JSONObject.NULL)
            obj.put("recipient", t.recipient ?: JSONObject.NULL)
            obj.put("merchantOrAgent", t.merchantOrAgent ?: JSONObject.NULL)
            obj.put("description", t.description ?: JSONObject.NULL)
            obj.put("date", t.transactionDate)
            obj.put("balanceAfterMinor", t.balanceAfterMinor ?: JSONObject.NULL)
            obj.put("rawSms", t.rawSms ?: JSONObject.NULL)
            obj.put("isInternalTransfer", t.isInternalTransfer)
            txnArray.put(obj)
        }
        root.put("transactions", txnArray)
        root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (root.has("accounts")) {
                val accs = root.getJSONArray("accounts")
                for (i in 0 until accs.length()) {
                    val obj = accs.getJSONObject(i)
                    val provider = obj.optString("provider")
                    val openBalMinor = if (obj.has("openingBalanceMinor")) {
                        obj.optLong("openingBalanceMinor", 0L)
                    } else {
                        Money.fromZmw(obj.optDouble("openingBalance", 0.0))
                    }
                    if (provider.isNotBlank()) {
                        accountDao.updateOpeningBalanceMinor(provider, openBalMinor)
                    }
                }
            }
            if (root.has("transactions")) {
                val txns = root.getJSONArray("transactions")
                for (i in 0 until txns.length()) {
                    val obj = txns.getJSONObject(i)
                    val amountMinor = if (obj.has("amountMinor")) {
                        obj.optLong("amountMinor", 0L)
                    } else {
                        Money.fromZmw(obj.optDouble("amount", 0.0))
                    }
                    val feeMinor = if (obj.has("feeMinor")) {
                        obj.optLong("feeMinor", 0L)
                    } else {
                        Money.fromZmw(obj.optDouble("fee", 0.0))
                    }
                    val balAfterMinor = when {
                        obj.has("balanceAfterMinor") && !obj.isNull("balanceAfterMinor") -> obj.getLong("balanceAfterMinor")
                        obj.has("balanceAfter") && !obj.isNull("balanceAfter") -> Money.fromZmw(obj.getDouble("balanceAfter"))
                        else -> null
                    }
                    val t = Transaction(
                        provider = obj.optString("provider", "UNKNOWN"),
                        transactionReference = obj.optString("reference", null),
                        transactionType = obj.optString("type", "OUTGOING_TRANSFER"),
                        direction = obj.optString("direction", "DEBIT"),
                        status = obj.optString("status", "SUCCESSFUL"),
                        categoryName = obj.optString("category", "General"),
                        amountMinor = amountMinor,
                        feeMinor = feeMinor,
                        sender = obj.optString("sender", null),
                        recipient = obj.optString("recipient", null),
                        merchantOrAgent = obj.optString("merchantOrAgent", null),
                        description = obj.optString("description", null),
                        transactionDate = obj.optLong("date", System.currentTimeMillis()),
                        balanceAfterMinor = balAfterMinor,
                        rawSms = obj.optString("rawSms", null),
                        isInternalTransfer = obj.optBoolean("isInternalTransfer", false)
                    )
                    val id = transactionDao.insertTransaction(t)
                    AccountingEngine.postTransaction(database, t.copy(id = id))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getSystemDiagnostics(): SystemDiagnostics = withContext(Dispatchers.IO) {
        val txns = transactionDao.getAllTransactions()
        val allLogs = smsLogDao.getAllLogs()
        val journalCount = journalDao.getJournalEntryCount()
        val auditCount = auditEventDao.getAuditEventCount()
        val unresolvedDisc = discrepancyDao.getUnresolvedDiscrepancies().size

        SystemDiagnostics(
            totalSmsLogs = allLogs.size,
            successfulSmsLogs = allLogs.count { it.processingStatus == "POSTED" },
            failedSmsLogs = allLogs.count { it.processingStatus.startsWith("FAILED") },
            reviewNeededSmsLogs = allLogs.count { it.processingStatus == "REQUIRES_REVIEW" },
            totalTransactions = txns.size,
            reversedTransactions = txns.count { it.status == "REVERSED" || it.transactionType == "REVERSAL" },
            internalTransfers = txns.count { it.isInternalTransfer },
            totalJournalEntries = journalCount,
            totalAuditEvents = auditCount,
            unresolvedDiscrepancies = unresolvedDisc,
            databaseVersion = 3,
            walModeActive = true
        )
    }
}

data class SystemDiagnostics(
    val totalSmsLogs: Int,
    val successfulSmsLogs: Int,
    val failedSmsLogs: Int,
    val reviewNeededSmsLogs: Int,
    val totalTransactions: Int,
    val reversedTransactions: Int,
    val internalTransfers: Int,
    val totalJournalEntries: Int,
    val totalAuditEvents: Int,
    val unresolvedDiscrepancies: Int,
    val databaseVersion: Int = 3,
    val walModeActive: Boolean = true
)
