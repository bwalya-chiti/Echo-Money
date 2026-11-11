package com.example.parser

import com.example.data.model.Money

data class ParsedTransaction(
    val provider: String, // AIRTEL, MTN, CASH, UNKNOWN
    val transactionType: String, // INCOMING_TRANSFER, OUTGOING_TRANSFER, MERCHANT_PAYMENT, CASH_WITHDRAWAL, CASH_DEPOSIT, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FEE, REFUND, REVERSAL, FAILED, BALANCE_UPDATE
    val direction: String = "DEBIT", // CREDIT, DEBIT, NONE
    val status: String = "SUCCESSFUL", // SUCCESSFUL, FAILED, PENDING, REVERSED, UNKNOWN
    val categoryName: String,
    val amountMinor: Long,
    val currency: String = "ZMW",
    val feeMinor: Long = 0L,
    val sender: String? = null,
    val recipient: String? = null,
    val merchantOrAgent: String? = null,
    val description: String? = null,
    val transactionReference: String? = null,
    val originalTransactionReference: String? = null,
    val balanceAfterMinor: Long? = null,
    val transactionDate: Long = System.currentTimeMillis(),
    val rawSms: String,
    val confidence: Float = 1.0f,
    val isPotentialInternalTransfer: Boolean = false,
    val requiresReview: Boolean = false,
    val reviewReason: String? = null
) {
    val amount: Double
        get() = Money.toZmw(amountMinor)

    val fee: Double
        get() = Money.toZmw(feeMinor)

    val balanceAfter: Double?
        get() = balanceAfterMinor?.let { Money.toZmw(it) }

    constructor(
        provider: String,
        transactionType: String,
        direction: String = "DEBIT",
        status: String = "SUCCESSFUL",
        categoryName: String,
        amount: Double,
        currency: String = "ZMW",
        fee: Double = 0.0,
        sender: String? = null,
        recipient: String? = null,
        merchantOrAgent: String? = null,
        description: String? = null,
        transactionReference: String? = null,
        originalTransactionReference: String? = null,
        balanceAfter: Double? = null,
        transactionDate: Long = System.currentTimeMillis(),
        rawSms: String,
        confidence: Float = 1.0f,
        isPotentialInternalTransfer: Boolean = false,
        requiresReview: Boolean = false,
        reviewReason: String? = null
    ) : this(
        provider = provider,
        transactionType = transactionType,
        direction = direction,
        status = status,
        categoryName = categoryName,
        amountMinor = Money.fromZmw(amount),
        currency = currency,
        feeMinor = Money.fromZmw(fee),
        sender = sender,
        recipient = recipient,
        merchantOrAgent = merchantOrAgent,
        description = description,
        transactionReference = transactionReference,
        originalTransactionReference = originalTransactionReference,
        balanceAfterMinor = balanceAfter?.let { Money.fromZmw(it) },
        transactionDate = transactionDate,
        rawSms = rawSms,
        confidence = confidence,
        isPotentialInternalTransfer = isPotentialInternalTransfer,
        requiresReview = requiresReview,
        reviewReason = reviewReason
    )
}
