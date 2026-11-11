package com.example.parser

import java.security.MessageDigest

object SmsParsingEngine {

    fun hashMessage(sender: String?, body: String): String {
        val input = "${(sender ?: "").trim().lowercase()}:${body.trim()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun parse(sender: String?, body: String): ParsedTransaction? {
        // Intelligent Word & Token Identifier Gatekeeper:
        // Filter out marketing promos, lottery ads, OTPs, and general chatter containing figures
        if (!TransactionWordClassifier.isValidFinancialTransactionCandidate(sender, body)) {
            return null
        }

        val detectedProvider = SmsProviderDetector.detectProvider(sender, body)

        return when (detectedProvider) {
            "AIRTEL" -> AirtelSmsParser.parse(body) ?: MtnSmsParser.parse(body)
            "MTN" -> MtnSmsParser.parse(body) ?: AirtelSmsParser.parse(body)
            else -> {
                AirtelSmsParser.parse(body) ?: MtnSmsParser.parse(body)
            }
        }
    }
}
