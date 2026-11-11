package com.example.parser

import java.security.MessageDigest

object SmsParsingEngine {

    fun hashMessage(sender: String?, body: String): String {
        val input = "${(sender ?: "").trim().lowercase()}:${body.trim()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun parse(sender: String?, body: String): ParsedTransaction? {
        val detectedProvider = SmsProviderDetector.detectProvider(sender, body)

        return when (detectedProvider) {
            "AIRTEL" -> AirtelSmsParser.parse(body)
            "MTN" -> MtnSmsParser.parse(body)
            else -> {
                // Try Airtel first then MTN as fallback
                AirtelSmsParser.parse(body) ?: MtnSmsParser.parse(body)
            }
        }
    }
}
