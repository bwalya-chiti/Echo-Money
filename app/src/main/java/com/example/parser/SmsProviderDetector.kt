package com.example.parser

object SmsProviderDetector {
    fun detectProvider(sender: String?, body: String): String {
        val s = (sender ?: "").trim().uppercase()
        val b = body.uppercase()

        // Explicit sender checks
        if (s.contains("AIRTEL") || s.contains("115")) return "AIRTEL"
        if (s.contains("MTN") || s.contains("MOMO") || s.contains("303")) return "MTN"

        // Body content checks
        if (b.contains("*115#") || b.contains("AIRTEL MONEY") || b.contains("TILL NUMBER MNOWALLET")) {
            return "AIRTEL"
        }
        if (b.contains("YELLO") || b.contains("FINANCIAL TRANSACTION ID") || b.contains("MOBILE MONEY")) {
            return "MTN"
        }

        // Generic checks for TID and Bal patterns
        if (b.contains("TID") && (b.contains("BAL IS ZMW") || b.contains("YOUR AVAILABLE BALANCE IS"))) {
            return "AIRTEL"
        }

        return "UNKNOWN"
    }
}
