package com.example.parser

import com.example.data.model.Money
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object AirtelSmsParser {

    private fun cleanReference(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().trimEnd('.', ',', ':', ';', ' ')
    }

    private fun parseDateString(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "dd-MMMM-yyyy",
            "dd-MMM-yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy"
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

        // 1. FAILED TRANSACTION
        // "Dear Customer, TXN Failed with TID: XX261101.1128.S83424, Balance not available to complete the transaction.. Please try again later.Thank you."
        val pFailed = Pattern.compile(
            "(?:Dear Customer,?\\s*)?TXN Failed with TID:\\s*([A-Za-z0-9._]+)[,.]?\\s*(Balance not available[^.]*|[^.]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mFailed = pFailed.matcher(normalized)
        if (mFailed.find()) {
            val tid = cleanReference(mFailed.group(1))
            val reason = mFailed.group(2)?.trim() ?: "Failed Transaction"
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "FAILED",
                direction = "NONE",
                status = "FAILED",
                categoryName = "Failed Transaction",
                amountMinor = 0L,
                description = reason,
                transactionReference = tid,
                rawSms = smsBody,
                confidence = 1.0f
            )
        }

        // 2. LOAN PROCEEDS / NASOVA LOAN FROM FIKILIZA
        // "Dear customer you have been credited with a NaSova loan of 148.00 ZMW from FIKILIZA. New balance 148.72 ZMW. Txn. Id: LN261101.1131.D86835"
        val pLoan = Pattern.compile(
            "(?:Dear customer\\s+)?you have been credited with a\\s+([A-Za-z0-9_]+)?\\s*loan of\\s*(?:ZMW|K)?\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\s*from\\s+([^.]+?)\\.?\\s*New balance\\s*(?:ZMW|K)?\\s*([\\d,.]+)\\s*(?:ZMW|K)?\\.?\\s*Txn\\.?\\s*Id:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mLoan = pLoan.matcher(normalized)
        if (mLoan.find()) {
            val loanType = mLoan.group(1)?.trim() ?: "NaSova"
            val amountMinor = Money.parseToMinor(mLoan.group(2))
            val lender = mLoan.group(3)?.trim() ?: "FIKILIZA"
            val balanceMinor = Money.parseToMinor(mLoan.group(4))
            val tid = cleanReference(mLoan.group(5))
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "LOAN_DISBURSEMENT",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Loan Proceeds",
                amountMinor = amountMinor,
                sender = "$lender ($loanType Loan)",
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                rawSms = smsBody,
                confidence = 1.0f
            )
        }

        // 3. PAYMENT TO NFS SETTLEMENT TILL
        // "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436."
        val pNfsTill = Pattern.compile(
            "Payment of (?:ZMW|K)?\\s*([\\d,.]+)\\s*Till Number\\s+([^.]+?)\\.?\\s*Airtel Money bal is (?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*TID\\s*:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mNfsTill = pNfsTill.matcher(normalized)
        if (mNfsTill.find()) {
            val amountMinor = Money.parseToMinor(mNfsTill.group(1))
            val recipient = mNfsTill.group(2)?.trim()
            val balanceMinor = Money.parseToMinor(mNfsTill.group(3))
            val tid = cleanReference(mNfsTill.group(4))
            val isNfs = recipient?.contains("NFS", ignoreCase = true) == true
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "OUTGOING_TRANSFER",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = if (isNfs) "Inter-Bank / NFS Transfer" else "Merchant Payment",
                amountMinor = amountMinor,
                recipient = recipient,
                merchantOrAgent = recipient,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                rawSms = smsBody,
                confidence = 0.98f,
                isPotentialInternalTransfer = isNfs
            )
        }

        // 4. MONEY SENT (SENT MONEY TO P2P)
        // "You have sent Money to Bwalya Blessings on 0766313205.Amount K145. Txn. ID : MP261101.1131.K33436.Your available balance is K0.90"
        val pSentMoney = Pattern.compile(
            "You have sent Money to\\s+([^.]+?)\\s+on\\s+([^.]+?)\\.?\\s*Amount\\s*(?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*Txn\\.?\\s*ID\\s*:\\s*([A-Za-z0-9._]+?)\\.?\\s*Your available balance is (?:ZMW|K)?\\s*([\\d,.]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mSentMoney = pSentMoney.matcher(normalized)
        if (mSentMoney.find()) {
            val recipient = mSentMoney.group(1)?.trim()
            val dateOrPhone = mSentMoney.group(2)?.trim()
            val amountMinor = Money.parseToMinor(mSentMoney.group(3))
            val tid = cleanReference(mSentMoney.group(4))
            val balanceMinor = Money.parseToMinor(mSentMoney.group(5))
            val isPhone = dateOrPhone?.matches(Regex("^[0-9+ ]+$")) == true
            val parsedDate = if (isPhone) System.currentTimeMillis() else parseDateString(dateOrPhone)
            val fullRecipient = if (isPhone) "$recipient ($dateOrPhone)" else recipient
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "OUTGOING_TRANSFER",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Person-to-Person Transfer",
                amountMinor = amountMinor,
                recipient = fullRecipient,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                transactionDate = parsedDate,
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 5. MONEY RECEIVED
        // "You have received ZMW 50.00 from John Lungu.Dial *115# to check your new Bal. TID: AT8829104."
        val pReceived = Pattern.compile(
            "You have received (?:ZMW|K)?\\s*([\\d,.]+)\\s*from\\s+([^.]+?)\\.?\\s*Dial \\*115# to check your new Bal\\.?\\s*TID\\s*:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mReceived = pReceived.matcher(normalized)
        if (mReceived.find()) {
            val amountMinor = Money.parseToMinor(mReceived.group(1))
            val sender = mReceived.group(2)?.trim()
            val tid = cleanReference(mReceived.group(3))
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "INCOMING_TRANSFER",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Received Money",
                amountMinor = amountMinor,
                sender = sender,
                transactionReference = tid,
                rawSms = smsBody,
                confidence = 0.98f
            )
        }

        // 6. MERCHANT PAYMENT
        // "PAID ZMW 10.00 to GLOBAL PAY COLLECTIONS Charge ZMW 0.00, TID AT77281. Bal ZMW 8.22 Date: 27-October-2026"
        val pMerchant = Pattern.compile(
            "PAID (?:ZMW|K)?\\s*([\\d,.]+)\\s*to\\s+([^,]+?)\\s+Charge (?:ZMW|K)?\\s*([\\d,.]+),?\\s*TID\\s*:?\\s*([A-Za-z0-9._]+?)\\.?\\s*Bal (?:ZMW|K)?\\s*([\\d,.]+)\\s*Date:\\s*([^\\n\\r.]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mMerchant = pMerchant.matcher(normalized)
        if (mMerchant.find()) {
            val amountMinor = Money.parseToMinor(mMerchant.group(1))
            val merchant = mMerchant.group(2)?.trim()
            val feeMinor = Money.parseToMinor(mMerchant.group(3))
            val tid = cleanReference(mMerchant.group(4))
            val balanceMinor = Money.parseToMinor(mMerchant.group(5))
            val dateStr = mMerchant.group(6)?.trim()
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "MERCHANT_PAYMENT",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Merchant Payment",
                amountMinor = amountMinor,
                feeMinor = feeMinor,
                recipient = merchant,
                merchantOrAgent = merchant,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 7. ALTERNATIVE MONEY SENT
        // "Money sent to Daudeni Phiri on 28-October-2026.Amount ZMW 75.00. Your bal is ZMW 226.04.TID: AT33819."
        val pAltSent = Pattern.compile(
            "Money sent to\\s+([^.]+?)\\s+on\\s+([^.]+?)\\.?\\s*Amount\\s*(?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*Your bal is (?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*TID\\s*:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mAltSent = pAltSent.matcher(normalized)
        if (mAltSent.find()) {
            val recipient = mAltSent.group(1)?.trim()
            val dateStr = mAltSent.group(2)?.trim()
            val amountMinor = Money.parseToMinor(mAltSent.group(3))
            val balanceMinor = Money.parseToMinor(mAltSent.group(4))
            val tid = cleanReference(mAltSent.group(5))
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "OUTGOING_TRANSFER",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Person-to-Person Transfer",
                amountMinor = amountMinor,
                recipient = recipient,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                transactionDate = parseDateString(dateStr),
                rawSms = smsBody,
                confidence = 0.98f
            )
        }

        // 8. CASH WITHDRAWAL
        // "You have withdrawn ZMW 200.00 from KABWE AGENT 04. Bal is ZMW 21.04. TID: AT119284."
        val pWithdraw = Pattern.compile(
            "You have withdrawn (?:ZMW|K)?\\s*([\\d,.]+)\\s*from\\s+([^.]+?)\\.?\\s*Bal is (?:ZMW|K)?\\s*([\\d,.]+)\\.?\\s*TID\\s*:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mWithdraw = pWithdraw.matcher(normalized)
        if (mWithdraw.find()) {
            val amountMinor = Money.parseToMinor(mWithdraw.group(1))
            val agent = mWithdraw.group(2)?.trim()
            val balanceMinor = Money.parseToMinor(mWithdraw.group(3))
            val tid = cleanReference(mWithdraw.group(4))
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "CASH_WITHDRAWAL",
                direction = "DEBIT",
                status = "SUCCESSFUL",
                categoryName = "Cash Withdrawal",
                amountMinor = amountMinor,
                recipient = "Agent: $agent",
                merchantOrAgent = agent,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 9. REVERSAL / REFUND
        // "Reversal of transaction [TID] has been completed. ZMW [AMOUNT] has been credited back to your Airtel Money account. New balance: ZMW [BALANCE]. Txn ID: [REV_TID]"
        val pReversal = Pattern.compile(
            "Reversal of transaction\\s+([A-Za-z0-9._]+)\\s+has been completed\\.?\\s*(?:ZMW|K)?\\s*([\\d,.]+)\\s*has been credited back.*?New balance:\\s*(?:ZMW|K)?\\s*([\\d,.]+).*?Txn\\.?\\s*ID:\\s*([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mReversal = pReversal.matcher(normalized)
        if (mReversal.find()) {
            val origTid = cleanReference(mReversal.group(1))
            val amountMinor = Money.parseToMinor(mReversal.group(2))
            val balanceMinor = Money.parseToMinor(mReversal.group(3))
            val tid = cleanReference(mReversal.group(4))
            return ParsedTransaction(
                provider = "AIRTEL",
                transactionType = "REVERSAL",
                direction = "CREDIT",
                status = "SUCCESSFUL",
                categoryName = "Reversal & Refund",
                amountMinor = amountMinor,
                balanceAfterMinor = balanceMinor,
                transactionReference = tid,
                originalTransactionReference = origTid,
                rawSms = smsBody,
                confidence = 0.99f
            )
        }

        // 10. BALANCE-ONLY NOTIFICATION
        // "Dear Customer, your Airtel Money balance is ZMW 0.90. Thank you."
        val pBalOnly = Pattern.compile(
            "(?:your )?Airtel Money (?:available )?balance is (?:ZMW|K)?\\s*([\\d,.]+)",
            Pattern.CASE_INSENSITIVE
        )
        val mBalOnly = pBalOnly.matcher(normalized)
        if (mBalOnly.find() && !normalized.contains("sent", ignoreCase = true) && !normalized.contains("withdrawn", ignoreCase = true) && !normalized.contains("paid", ignoreCase = true)) {
            val balanceMinor = Money.parseToMinor(mBalOnly.group(1))
            return ParsedTransaction(
                provider = "AIRTEL",
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

        // Fallback generic Airtel matcher if format varies slightly
        if (normalized.contains("TID", ignoreCase = true) &&
            (normalized.contains("received", ignoreCase = true) ||
             normalized.contains("sent", ignoreCase = true) ||
             normalized.contains("payment", ignoreCase = true))
        ) {
            val amountPattern = Pattern.compile("(?:ZMW|K)\\s*([\\d,.]+)|([\\d,.]+)\\s*(?:ZMW|K)", Pattern.CASE_INSENSITIVE)
            val amtMatcher = amountPattern.matcher(normalized)
            if (amtMatcher.find()) {
                val amtStr = amtMatcher.group(1) ?: amtMatcher.group(2)
                val amountMinor = Money.parseToMinor(amtStr)
                val isIncome = normalized.contains("received", ignoreCase = true)
                val tidPattern = Pattern.compile("TID\\s*:?\\s*([A-Za-z0-9._]+)", Pattern.CASE_INSENSITIVE)
                val tidMatcher = tidPattern.matcher(normalized)
                val tid = if (tidMatcher.find()) cleanReference(tidMatcher.group(1)) else null
                return ParsedTransaction(
                    provider = "AIRTEL",
                    transactionType = if (isIncome) "INCOMING_TRANSFER" else "OUTGOING_TRANSFER",
                    direction = if (isIncome) "CREDIT" else "DEBIT",
                    status = "SUCCESSFUL",
                    categoryName = if (isIncome) "Received Money" else "General Expense",
                    amountMinor = amountMinor,
                    transactionReference = tid,
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
