package com.example.parser

import java.util.regex.Pattern

/**
 * Intelligent word and token identifier for incoming SMS messages.
 * Prevents non-financial messages containing figures, promotional SMS,
 * lottery campaigns, OTP codes, and casual chatter from being misidentified
 * as mobile money transactions.
 */
object TransactionWordClassifier {

    // 1. Promotional, marketing, lottery, and subscription keywords
    private val PROMOTIONAL_OR_SPAM_PATTERNS = listOf(
        Pattern.compile("stand\\s+a\\s+chance\\s+to\\s+win", Pattern.CASE_INSENSITIVE),
        Pattern.compile("stand\\s+to\\s+win", Pattern.CASE_INSENSITIVE),
        Pattern.compile("chance\\s+to\\s+win", Pattern.CASE_INSENSITIVE),
        Pattern.compile("win\\s+big", Pattern.CASE_INSENSITIVE),
        Pattern.compile("lucky\\s+winner", Pattern.CASE_INSENSITIVE),
        Pattern.compile("congratulations.*?(?:won|winner)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("recharge\\s+(?:with|and|your|now|to\\s+win|to\\s+get)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("top\\s*up\\s+(?:with|and|now)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("free\\s+(?:data|minutes|airtime|sms|mb|gb)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bonus\\s+(?:data|minutes|airtime)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("enjoy\\s+(?:bonus|up\\s+to|free)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("special\\s+offer", Pattern.CASE_INSENSITIVE),
        Pattern.compile("exclusive\\s+offer", Pattern.CASE_INSENSITIVE),
        Pattern.compile("limited\\s+time\\s+offer", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpromo\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpromotion\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("dial\\s*\\*[0-9]+#\\s+to\\s+(?:get|buy|win|activate|enjoy|subscribe)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("subscribe\\s+to", Pattern.CASE_INSENSITIVE),
        Pattern.compile("text\\s+stop\\s+to", Pattern.CASE_INSENSITIVE),
        Pattern.compile("opt\\s*out", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you\\s+qualify\\s+for\\s+a\\s+loan", Pattern.CASE_INSENSITIVE),
        Pattern.compile("apply\\s+now\\s+for", Pattern.CASE_INSENSITIVE),
        Pattern.compile("emergency\\s+airtime", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bundle\\s+has\\s+expired", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bundle\\s+balance\\s+is\\s+running\\s+low", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you\\s+have\\s+used\\s+\\d+%", Pattern.CASE_INSENSITIVE),
        Pattern.compile("weekly\\s+draw", Pattern.CASE_INSENSITIVE),
        Pattern.compile("jackpot", Pattern.CASE_INSENSITIVE)
    )

    // 2. Security, OTP, and verification messages
    private val SECURITY_OR_OTP_PATTERNS = listOf(
        Pattern.compile("verification\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("one-time\\s+password", Pattern.CASE_INSENSITIVE),
        Pattern.compile("one\\s+time\\s+password", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\botp\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("secret\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+not\\s+share\\s+this\\s+pin", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+not\\s+share\\s+your\\s+otp", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+not\\s+share\\s+this\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("security\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("password\\s+reset", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pin\\s+reset", Pattern.CASE_INSENSITIVE),
        Pattern.compile("is\\s+your\\s+code", Pattern.CASE_INSENSITIVE)
    )

    // 3. Genuine Financial Action indicators (must contain at least one)
    private val FINANCIAL_ACTION_PATTERNS = listOf(
        Pattern.compile("you\\s+have\\s+sent\\s+money", Pattern.CASE_INSENSITIVE),
        Pattern.compile("money\\s+sent\\s+to", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you\\s+have\\s+received", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you\\s+have\\s+transferred", Pattern.CASE_INSENSITIVE),
        Pattern.compile("payment\\s+of.*?till\\s+number", Pattern.CASE_INSENSITIVE),
        Pattern.compile("payment\\s+of.*?successful", Pattern.CASE_INSENSITIVE),
        Pattern.compile("paid\\s+(?:(?:zmw|k)\\s*)?[\\d,.]+\\s+to", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:you\\s+have\\s+withdrawn|cash\\s+out\\s+of)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cash\\s+deposit\\s+of", Pattern.CASE_INSENSITIVE),
        Pattern.compile("credited\\s+with\\s+a.*?loan", Pattern.CASE_INSENSITIVE),
        Pattern.compile("loan\\s+repayment\\s+of", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:reversal\\s+of|has\\s+been\\s+reversed)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:txn\\s+failed\\s+with\\s+tid|transaction\\s+failed)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:airtel\\s+money|mobile\\s+money)\\s+(?:available\\s+)?(?:account\\s+)?balance\\s+is", Pattern.CASE_INSENSITIVE),
        Pattern.compile("your\\s+new\\s+balance\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("your\\s+available\\s+balance\\s+is", Pattern.CASE_INSENSITIVE),
        Pattern.compile("available\\s+mobile\\s+money\\s+balance\\s+is", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bal(?:ance)?\\s+is\\s+(?:zmw|k)?\\s*[\\d,.]+", Pattern.CASE_INSENSITIVE)
    )

    // 4. Legitimate Transaction Reference tokens (TID, Financial Transaction ID)
    private val TRANSACTION_REFERENCE_PATTERN = Pattern.compile(
        "(?:TID\\s*[:#]?\\s*[A-Za-z0-9._]{4,}|Txn\\.?\\s*(?:ID|Id)\\s*[:#]?\\s*[A-Za-z0-9._]{4,}|Financial\\s+Transaction\\s+(?:ID|Id)\\s*[:#]?\\s*[A-Za-z0-9._]{4,})",
        Pattern.CASE_INSENSITIVE
    )

    // 5. Explicit currency figure patterns (must be attached to K or ZMW)
    private val CURRENCY_AMOUNT_PATTERN = Pattern.compile(
        "(?:ZMW|K)\\s*[\\d,.]+|[\\d,.]+\\s*(?:ZMW|K)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Determines whether a message is promotional, marketing, or spam.
     * Note: Confirmed purchase receipts (which may mention bundles or data)
     * are protected from false-positive promotion classification if they
     * contain official transaction IDs and balance updates.
     */
    fun isPromotionalOrSpam(body: String): Boolean {
        val trimmed = body.trim()
        val hasOfficialReceipt = hasTransactionReference(trimmed) &&
                (trimmed.contains("successful at", ignoreCase = true) ||
                 trimmed.contains("Your new balance", ignoreCase = true) ||
                 trimmed.contains("bal is", ignoreCase = true))

        if (hasOfficialReceipt) {
            // Even if receipt mentions "valid until" or bundle details, it is an official receipt
            return false
        }

        for (pattern in PROMOTIONAL_OR_SPAM_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the message is an OTP, PIN, or verification notification.
     */
    fun isSecurityOrOtp(body: String): Boolean {
        for (pattern in SECURITY_OR_OTP_PATTERNS) {
            if (pattern.matcher(body).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the message has an authentic transaction reference (TID or Financial Transaction ID).
     */
    fun hasTransactionReference(body: String): Boolean {
        return TRANSACTION_REFERENCE_PATTERN.matcher(body).find()
    }

    /**
     * Checks if the message contains authentic financial transaction action verbs.
     */
    fun hasFinancialAction(body: String): Boolean {
        for (pattern in FINANCIAL_ACTION_PATTERNS) {
            if (pattern.matcher(body).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the message contains genuine currency figures (ZMW or K with numbers),
     * rather than dates, times, phone numbers, or token digits.
     */
    fun hasCurrencyAmount(body: String): Boolean {
        return CURRENCY_AMOUNT_PATTERN.matcher(body).find()
    }

    /**
     * Main gatekeeper: determines whether an incoming SMS is a plausible mobile money
     * transaction confirmation before passing it to parser regex engines.
     */
    fun isValidFinancialTransactionCandidate(sender: String?, body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.length < 15) return false

        // 1. Filter out security codes / OTPs
        if (isSecurityOrOtp(trimmed)) return false

        // 2. Filter out marketing / promo / lottery campaigns
        if (isPromotionalOrSpam(trimmed)) return false

        // 3. Must contain genuine financial transaction action words
        val hasAction = hasFinancialAction(trimmed)

        // 4. Must contain an authentic transaction reference OR be an official balance notification
        val hasRef = hasTransactionReference(trimmed)
        val isBalanceNotification = trimmed.contains("balance is", ignoreCase = true) &&
                (trimmed.contains("Airtel Money", ignoreCase = true) ||
                 trimmed.contains("Mobile Money", ignoreCase = true))

        if (!hasRef && !isBalanceNotification) {
            return false
        }

        // 5. Must have currency figures (unless it is a failed transaction or balance notification)
        val isFailedTxn = trimmed.contains("TXN Failed", ignoreCase = true) ||
                trimmed.contains("transaction failed", ignoreCase = true)

        if (!hasCurrencyAmount(trimmed) && !isFailedTxn && !isBalanceNotification) {
            return false
        }

        // 6. Must have recognized financial action
        if (!hasAction && !isBalanceNotification && !isFailedTxn) {
            return false
        }

        return true
    }
}
