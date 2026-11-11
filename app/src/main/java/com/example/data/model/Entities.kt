package com.example.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["provider"], unique = true),
        Index(value = ["accountCode"])
    ]
)
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String, // AIRTEL, MTN, CASH, LENDER, INTERNAL
    val accountCode: String = provider,
    val accountName: String,
    val accountType: String = "ASSET", // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    val openingBalanceMinor: Long = 0L,
    val lastReportedBalanceMinor: Long? = null,
    val lastBalanceUpdate: Long? = null,
    val isReconciled: Boolean = true,
    val reconciledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val openingBalance: Double
        get() = Money.toZmw(openingBalanceMinor)

    val lastReportedBalance: Double?
        get() = lastReportedBalanceMinor?.let { Money.toZmw(it) }

    @Ignore
    constructor(
        id: Long = 0,
        provider: String,
        accountName: String,
        openingBalance: Double = 0.0,
        lastReportedBalance: Double? = null,
        lastBalanceUpdate: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        provider = provider,
        accountCode = provider,
        accountName = accountName,
        accountType = "ASSET",
        openingBalanceMinor = Money.fromZmw(openingBalance),
        lastReportedBalanceMinor = lastReportedBalance?.let { Money.fromZmw(it) },
        lastBalanceUpdate = lastBalanceUpdate,
        isReconciled = true,
        reconciledAt = null,
        createdAt = createdAt
    )
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["provider", "transactionReference"]),
        Index(value = ["transactionDate"]),
        Index(value = ["isInternalTransfer"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["status"]),
        Index(value = ["needsReview"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 0,
    val provider: String, // AIRTEL, MTN, CASH
    val transactionReference: String? = null,
    val transactionType: String, // INCOMING_TRANSFER, OUTGOING_TRANSFER, MERCHANT_PAYMENT, CASH_WITHDRAWAL, CASH_DEPOSIT, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FEE, REFUND, REVERSAL, FAILED, BALANCE_UPDATE
    val direction: String = "DEBIT", // CREDIT, DEBIT, NONE
    val status: String = "SUCCESSFUL", // SUCCESSFUL, FAILED, PENDING, REVERSED, UNKNOWN, REVIEW_NEEDED
    val categoryId: Long = 0,
    val categoryName: String = "General",
    val amountMinor: Long = 0L,
    val currency: String = "ZMW",
    val feeMinor: Long = 0L,
    val sender: String? = null,
    val recipient: String? = null,
    val merchantOrAgent: String? = null,
    val description: String? = null,
    val originalTransactionReference: String? = null,
    val transactionDate: Long = System.currentTimeMillis(),
    val detectedAt: Long = System.currentTimeMillis(),
    val balanceAfterMinor: Long? = null,
    val rawSms: String? = null,
    val classificationConfidence: Float = 1.0f,
    val isInternalTransfer: Boolean = false,
    val transferGroupId: String? = null,
    val needsReview: Boolean = false,
    val reviewReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val amount: Double
        get() = Money.toZmw(amountMinor)

    val fee: Double
        get() = Money.toZmw(feeMinor)

    val balanceAfter: Double?
        get() = balanceAfterMinor?.let { Money.toZmw(it) }

    @Ignore
    constructor(
        id: Long = 0,
        accountId: Long = 0,
        provider: String,
        transactionReference: String? = null,
        transactionType: String,
        direction: String = "DEBIT",
        status: String = "SUCCESSFUL",
        categoryId: Long = 0,
        categoryName: String = "General",
        amount: Double = 0.0,
        currency: String = "ZMW",
        fee: Double = 0.0,
        sender: String? = null,
        recipient: String? = null,
        merchantOrAgent: String? = null,
        description: String? = null,
        originalTransactionReference: String? = null,
        transactionDate: Long = System.currentTimeMillis(),
        detectedAt: Long = System.currentTimeMillis(),
        balanceAfter: Double? = null,
        rawSms: String? = null,
        classificationConfidence: Float = 1.0f,
        isInternalTransfer: Boolean = false,
        transferGroupId: String? = null,
        needsReview: Boolean = false,
        reviewReason: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        accountId = accountId,
        provider = provider,
        transactionReference = transactionReference,
        transactionType = transactionType,
        direction = direction,
        status = status,
        categoryId = categoryId,
        categoryName = categoryName,
        amountMinor = Money.fromZmw(amount),
        currency = currency,
        feeMinor = Money.fromZmw(fee),
        sender = sender,
        recipient = recipient,
        merchantOrAgent = merchantOrAgent,
        description = description,
        originalTransactionReference = originalTransactionReference,
        transactionDate = transactionDate,
        detectedAt = detectedAt,
        balanceAfterMinor = balanceAfter?.let { Money.fromZmw(it) },
        rawSms = rawSms,
        classificationConfidence = classificationConfidence,
        isInternalTransfer = isInternalTransfer,
        transferGroupId = transferGroupId,
        needsReview = needsReview,
        reviewReason = reviewReason,
        createdAt = createdAt
    )
}

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["entryDate"])
    ]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long? = null,
    val entryDate: Long = System.currentTimeMillis(),
    val description: String,
    val sourceReference: String? = null,
    val postedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_lines",
    indices = [
        Index(value = ["journalEntryId"]),
        Index(value = ["accountId"]),
        Index(value = ["accountCode"])
    ]
)
data class JournalLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalEntryId: Long,
    val accountId: Long,
    val accountCode: String,
    val debitMinor: Long = 0L,
    val creditMinor: Long = 0L,
    val description: String? = null
)

@Entity(
    tableName = "transaction_relationships",
    indices = [
        Index(value = ["sourceTransactionId"]),
        Index(value = ["targetTransactionId"]),
        Index(value = ["relationshipType"])
    ]
)
data class TransactionRelationship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relationshipType: String, // INTERNAL_TRANSFER, REVERSAL_REFUND, LOAN_REPAYMENT, CASH_WITHDRAWAL_DUAL_ENTRY, PARENT_FEE
    val sourceTransactionId: Long,
    val targetTransactionId: Long? = null,
    val matchingEvidence: String,
    val confidence: Float = 1.0f,
    val status: String = "CONFIRMED_AUTO", // CONFIRMED_AUTO, CONFIRMED_USER, PROBABLE, UNMATCHED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["eventType"])
    ]
)
data class AuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // SMS_RECEIVED, PARSED, POSTED, DUPLICATE_DETECTED, RELATIONSHIP_CREATED, RECONCILED, ROLLBACK
    val recordTable: String,
    val recordId: Long? = null,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reconciliation_discrepancies",
    indices = [
        Index(value = ["provider"]),
        Index(value = ["isResolved"])
    ]
)
data class ReconciliationDiscrepancy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val reportedBalanceMinor: Long,
    val calculatedBalanceMinor: Long,
    val discrepancyMinor: Long,
    val transactionReference: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val resolutionNotes: String? = null
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryType: String, // INCOME, EXPENSE, SPECIAL
    val isDefault: Boolean = true,
    val icon: String = "category"
)

@Entity(
    tableName = "sms_processing",
    indices = [Index(value = ["messageHash"], unique = true)]
)
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageHash: String,
    val provider: String,
    val sender: String = "",
    val processingStatus: String, // RECEIVED, STORED, PARSING, PARSED, VALIDATED, MATCHED, POSTED, REQUIRES_REVIEW, FAILED_PROCESSING
    val errorMessage: String? = null,
    val rawBody: String,
    val receivedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "internal_transfers")
data class InternalTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTransactionId: Long,
    val destinationTransactionId: Long,
    val amountMinor: Long,
    val status: String = "MATCHED",
    val createdAt: Long = System.currentTimeMillis()
) {
    val amount: Double
        get() = Money.toZmw(amountMinor)

    @Ignore
    constructor(
        id: Long = 0,
        sourceTransactionId: Long,
        destinationTransactionId: Long,
        amount: Double,
        status: String = "MATCHED",
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        sourceTransactionId = sourceTransactionId,
        destinationTransactionId = destinationTransactionId,
        amountMinor = Money.fromZmw(amount),
        status = status,
        createdAt = createdAt
    )
}

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val settingKey: String,
    val settingValue: String
)
