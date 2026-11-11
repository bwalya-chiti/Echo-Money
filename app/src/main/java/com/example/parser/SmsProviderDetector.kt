package com.example.parser

object SmsProviderDetector {
    fun detectProvider(sender: String?, body: String): String {
        val s = (sender ?: "").trim().uppercase()
        val b = body.uppercase()

        // 1. Explicit official sender checks
        if (s.contains("AIRTEL") || s == "115") return "AIRTEL"
        if (s.contains("MTN") || s.contains("MOMO") || s == "303") return "MTN"

        // 2. High-confidence distinctive provider markers in SMS body
        if (b.contains("AIRTEL MONEY") || b.contains("TILL NUMBER MNOWALLET") || b.contains("*115#") || b.contains("NASOVA")) {
            return "AIRTEL"
        }
        if (b.contains("FINANCIAL TRANSACTION ID") || b.contains("MOMO FROM MTN") || b.contains("YELLO") || b.contains("MOBILE MONEY")) {
            return "MTN"
        }

        // 3. Distinctive reference signatures
        if (b.contains("TID") && (b.contains("BAL IS ZMW") || b.contains("YOUR AVAILABLE BALANCE IS") || b.contains("NEW BALANCE"))) {
            return "AIRTEL"
        }

        return "UNKNOWN"
    }
}
