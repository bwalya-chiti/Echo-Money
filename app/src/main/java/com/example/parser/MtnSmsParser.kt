package com.example.parser

import com.example.data.model.Money
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object MtnSmsParser {

    private fun cleanReference(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().trimEnd('.', ',', ':', ';', ' ')
    }

    private fun parseDateString(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "dd-MMM-yyyy",
            "dd-MMMM-yyyy"
        )
        val trimmed = raw.trim()
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                val d = sdf.parse(trimmed)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    fun parse(smsBody: String): ParsedTransaction? {
        val normalized = smsBody.replace("\r", " ").replace("\n", " ").trim()

        // 1. MTN CASH WITHDRAWAL (DIRECT MATCH FOR USER'S SCREENSHOT)
        // "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        val pWithdrawnDirect = Pattern.compile(
            "You have withdrawn\\s+(?:ZMW|K)?\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\s*from your mobile money account\\s+(?:at\\s+on|at|on)?\\s*([\\d-]+\\s+[\\d:]+)\\.?\\s*Your new balance\\s*:\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Financial Transaction Id\\s*:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mWithdrawnDirect = pWithdrawnDirect.matcher(normalized)
        if (mWithdrawnDirect.find()) {
            val amountMinor = Money.parseToMinor(mWithdrawnDirect.group(1))
            val dateStr = mWithdrawnDirect.group(2)?.trim()
            val balanceMinor = Money.parseToMinor(mWithdrawnDirect.group(3))
            val txnId = cleanReference(mWithdrawnDirect.group(4))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "CASH_WITHDRAWAL",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Cash Withdrawal",
                amountMinor = amountMinor,
                recipient = "Cash Withdrawal",
                merchantOrAgent = "MTN MoMo Agent",
                description = "Cash withdrawal from mobile money account",
                balanceAfterMinor = balanceMinor,
                transactionReference = txnId,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 1.0f
            )
        }

        // 2. MTN CASH OUT WITH AGENT & FEE
        // "Yello, Cash Out of 50.00 ZMW from [AGENT]. Fee 1.50 ZMW. Your new balance: 100.00 ZMW. Financial Transaction ID: [REFERENCE]."
        val pCashOut = Pattern.compile(
            "Yello,?\\s*(?:Cash Out|Withdrawal) of (?:ZMW|K)?\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\s*from\\s+([^.]+?)\\.?\\s*Fee (?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*Your new balance:\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mCashOut = pCashOut.matcher(normalized)
        if (mCashOut.find()) {
            val amountMinor = Money.parseToMinor(mCashOut.group(1))
            val agent = mCashOut.group(2)?.trim()
            val feeMinor = Money.parseToMinor(mCashOut.group(3))
            val balanceMinor = Money.parseToMinor(mCashOut.group(4))
            val txnId = cleanReference(mCashOut.group(5))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "CASH_WITHDRAWAL",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Cash Withdrawal",
                amountMinor = amountMinor,
                feeMinor = feeMinor,
                recipient = "Agent: $agent",
                merchantOrAgent = agent,
                balanceAfterMinor = balanceMinor,
                transactionReference = txnId,
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 3. FAILED TRANSACTION
        // "Yello, transaction failed. Your balance is insufficient to complete the transaction of ZMW 50.00. Financial Transaction ID: FL109783."
        val pFailed = Pattern.compile(
            "Yello,?\\s*transaction failed(?:\\.|,)?\\s*(.*?)(?:Financial Transaction ID\\s*:\\s*([A-Za-z0-9._]+))",
            Pattern.CASE_INSENSITIVE
        )
        val mFailed = pFailed.matcher(normalized)
        if (mFailed.find()) {
            val reason = mFailed.group(1)?.trim()?.trimEnd('.', ',') ?: "Transaction Failed"
            val txnId = cleanReference(mFailed.group(2))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "FAILED",
                direction = "NONE",
                status = "FAILED",
                categoryName = "Failed Transaction",
                amountMinor = 0L,
                description = reason,
                transactionReference = txnId,
                rawSms = smsBody,
                confidence = 1.0f
            )
        }

        // 4. MONEY RECEIVED FROM AIRTEL NFS (WITH SENDER DETAILS & NOTE REFERENCE)
        // "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895."
        val pReceivedDetailed = Pattern.compile(
            "(?:Yello\\.?\\s*)?You have received (?:ZMW|K)?\\s*([\\d,.]+)\\s*from\\s+([^.]+?)\\s+on your mobile money account at\\s+([0-9T:. -]+?)\\.?\\s*Message from sender:\\s*(.*?)\\.?\\s*Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mReceivedDetailed = pReceivedDetailed.matcher(normalized)
        if (mReceivedDetailed.find()) {
            val amountMinor = Money.parseToMinor(mReceivedDetailed.group(1))
            val sender = mReceivedDetailed.group(2)?.trim()
            val dateStr = mReceivedDetailed.group(3)?.trim()
            val rawNote = mReceivedDetailed.group(4)?.trim()
            val cleanNote = rawNote?.trimEnd('.', ' ')?.takeIf { it.isNotBlank() }
            val txnId = cleanReference(mReceivedDetailed.group(5))
            val isAirtelNfs = sender?.contains("AIRTEL", ignoreCase = true) == true && sender.contains("NFS", ignoreCase = true)
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "INCOMING_TRANSFER",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Received Money",
                amountMinor = amountMinor,
                sender = sender,
                description = cleanNote,
                transactionReference = txnId,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.99f,
                isPotentialInternalTransfer = isAirtelNfs
            )
        }

        // 5. MONEY RECEIVED FROM AIRTEL NFS (GENERIC)
        // "Yello.You have received ZMW 145.00 from AIRTEL NFS on your mobile money account at 2026-11-01 11:31:56. Message from sender: . Financial Transaction ID: 10978746895. Dial *115# to view balance. Thank you for using Momo from MTN."
        val pReceivedGeneric = Pattern.compile(
            "(?:Yello\\.?\\s*)?You have received (?:ZMW|K)?\\s*([\\d,.]+)\\s*from\\s+AIRTEL NFS\\s+on your mobile money account at\\s+([0-9T:. -]+?)\\.?\\s*(?:Message from sender:.*?\\.?\\s*)?Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mReceivedGeneric = pReceivedGeneric.matcher(normalized)
        if (mReceivedGeneric.find()) {
            val amountMinor = Money.parseToMinor(mReceivedGeneric.group(1))
            val dateStr = mReceivedGeneric.group(2)?.trim()
            val txnId = cleanReference(mReceivedGeneric.group(3))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "INCOMING_TRANSFER",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Received Money",
                amountMinor = amountMinor,
                sender = "AIRTEL NFS",
                transactionReference = txnId,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.98f,
                isPotentialInternalTransfer = true
            )
        }

        // 6. DATA BUNDLE PAYMENT
        // "Yello, Payment of ZMW 5.00 to Mobile Money successful at 2026-10-28 18:34:03. Message: You have successfully purchased K5:6GB/5hrs for 0961234567 valid until 2026-10-28 23:34:03. Your new balance: 30.76 ZMW. Financial Transaction ID: MT772910."
        val pDataBundle = Pattern.compile(
            "Yello,?\\s*Payment of (?:ZMW|K)?\\s*([\\d,.]+)\\s*to\\s+([^.]+?)\\s*successful at\\s+([\\d-]+\\s+[\\d:]+)\\.?\\s*Message:\\s*([^.]+?purchased\\s+([^.]+?)\\s+for\\s+([^.]+?)\\s+valid until\\s+([^.]+?))\\.\\s*Your new balance:\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mDataBundle = pDataBundle.matcher(normalized)
        if (mDataBundle.find()) {
            val amountMinor = Money.parseToMinor(mDataBundle.group(1))
            val recipient = mDataBundle.group(2)?.trim()
            val dateStr = mDataBundle.group(3)?.trim()
            val bundleDesc = mDataBundle.group(5)?.trim() ?: mDataBundle.group(4)?.trim()
            val balanceMinor = Money.parseToMinor(mDataBundle.group(8))
            val txnId = cleanReference(mDataBundle.group(9))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "MERCHANT_PAYMENT",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Internet & Data Bundles",
                amountMinor = amountMinor,
                recipient = recipient,
                merchantOrAgent = recipient,
                description = bundleDesc,
                balanceAfterMinor = balanceMinor,
                transactionReference = txnId,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 7. OTHER MOBILE MONEY PAYMENT
        // "Yello, Payment of ZMW 3.00 to Mobile Money Data Bundle successful at [DATE]. Message: CBP. Your new balance: [BALANCE] ZMW. Financial Transaction ID: [REFERENCE]."
        val pOtherPayment = Pattern.compile(
            "Yello,?\\s*Payment of (?:ZMW|K)?\\s*([\\d,.]+)\\s*to\\s+([^.]+?)\\s*successful at\\s+([^.]+?)\\.?\\s*Message:\\s*([^.]*?)\\.?\\s*Your new balance:\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mOtherPayment = pOtherPayment.matcher(normalized)
        if (mOtherPayment.find()) {
            val amountMinor = Money.parseToMinor(mOtherPayment.group(1))
            val recipient = mOtherPayment.group(2)?.trim()
            val dateStr = mOtherPayment.group(3)?.trim()
            val msg = mOtherPayment.group(4)?.trim()
            val balanceMinor = Money.parseToMinor(mOtherPayment.group(5))
            val txnId = cleanReference(mOtherPayment.group(6))
            val isData = recipient?.contains("data", ignoreCase = true) == true || msg?.contains("data", ignoreCase = true) == true
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "MERCHANT_PAYMENT",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = if (isData) "Internet & Data Bundles" else "Merchant Payment",
                amountMinor = amountMinor,
                recipient = recipient,
                merchantOrAgent = recipient,
                description = msg,
                balanceAfterMinor = balanceMinor,
                transactionReference = txnId,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.97f
            )
        }

        // 8. REVERSAL
        // "Yello, transaction [TID] of ZMW [AMOUNT] has been reversed. Your new balance: [BALANCE] ZMW. Financial Transaction ID: [REV_TID]"
        val pReversal = Pattern.compile(
            "Yello,?\\s*transaction\\s+([A-Za-z0-9._]+)\\s+of (?:ZMW|K)?\\s*([\\d,.]+)\\s*has been reversed\\.?\\s*Your new balance:\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Financial Transaction ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mReversal = pReversal.matcher(normalized)
        if (mReversal.find()) {
            val origTid = cleanReference(mReversal.group(1))
            val amountMinor = Money.parseToMinor(mReversal.group(2))
            val balanceMinor = Money.parseToMinor(mReversal.group(3))
            val txnId = cleanReference(mReversal.group(4))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "REVERSAL",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Reversal & Refund",
                amountMinor = amountMinor,
                balanceAfterMinor = balanceMinor,
                transactionReference = txnId,
                originalTransactionReference = origTid,
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 9. BALANCE-ONLY UPDATE
        // "Yello, your MTN Mobile Money balance is 143.26 ZMW. Thank you."
        val pBalOnly = Pattern.compile(
            "your MTN Mobile Money balance is\\s*([\\d,.]+)\\s*(?:ZMW|K)?",
            Pattern.CASE_INSENSITIVE
        )
        val mBalOnly = pBalOnly.matcher(normalized)
        if (mBalOnly.find() && !normalized.contains("withdrawn", ignoreCase = true) && !normalized.contains("payment", ignoreCase = true)) {
            val balanceMinor = Money.parseToMinor(mBalOnly.group(1))
            return ParsedTransaction(
                provider = "MTN",
                transactionType = "BALANCE_UPDATE",
                direction = "NONE",
                status = "SUCCESSFUL",
                categoryName = "Balance Check",
                amountMinor = 0L,
                balanceAfterMinor = balanceMinor,
                rawSms = smsBody,
                confidence = 1.0f
            )
        }

        // 10. Robust Fallback for MTN when Financial Transaction ID is present
        if (normalized.contains("Financial Transaction ID", ignoreCase = true) ||
            normalized.contains("Financial Transaction Id", ignoreCase = true) ||
            normalized.contains("Yello", ignoreCase = true)
        ) {
            val amountPattern = Pattern.compile("(?:ZMW|K)\\s*([\\d,.]+)|([\\d,.]+)\\s*(?:ZMW|K)", Pattern.CASE_INSENSITIVE)
            val amtMatcher = amountPattern.matcher(normalized)
            if (amtMatcher.find()) {
                val amtStr = amtMatcher.group(1) ?: amtMatcher.group(2)
                val amountMinor = Money.parseToMinor(amtStr)
                val isIncome = normalized.contains("received", ignoreCase = true)
                val isWithdrawal = normalized.contains("withdrawn", ignoreCase = true) || normalized.contains("cash out", ignoreCase = true)
                val txnIdPattern = Pattern.compile("Financial Transaction Id\\s*:\\s*([A-Za-z0-9._]+)", Pattern.CASE_INSENSITIVE)
                val txnIdMatcher = txnIdPattern.matcher(normalized)
                val txnId = if (txnIdMatcher.find()) cleanReference(txnIdMatcher.group(1)) else null
                return ParsedTransaction(
                    provider = "MTN",
                    transactionType = when {
                        isWithdrawal -> "CASH_WITHDRAWAL"
                        isIncome -> "INCOMING_TRANSFER"
                        else -> "OUTGOING_TRANSFER"
                    },
                    direction = if (isIncome) "CREDIT" else "DEBIT",
                    status = "SUCCESSFUL",
                    categoryName = when {
                        isWithdrawal -> "Cash Withdrawal"
                        isIncome -> "Received Money"
                        else -> "General Expense"
                    },
                    amountMinor = amountMinor,
                    transactionReference = txnId,
                    rawSms = smsBody,
                    confidence = 0.85f,
                    requiresReview = true,
                    reviewReason = "Matched via fallback pattern"
                )
            }
        }

        return null
    }
}
