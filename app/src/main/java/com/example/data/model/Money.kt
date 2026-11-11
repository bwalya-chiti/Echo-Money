package com.example.data.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Exact financial precision utilities.
 *
 * Implements integer minor units (Ngwee) for Zambian Kwacha (ZMW):
 * K1.00 = 100 minor units.
 * K10.50 = 1050 minor units.
 * K0.01 = 1 minor unit.
 *
 * Eliminates IEEE 754 binary floating-point drift across ledger operations.
 */
object Money {
    const val MINOR_UNITS_PER_ZMW = 100L

    fun fromZmw(amountZmw: Double): Long {
        return (amountZmw * MINOR_UNITS_PER_ZMW).roundToLong()
    }

    fun toZmw(minorUnits: Long): Double {
        return minorUnits.toDouble() / MINOR_UNITS_PER_ZMW
    }

    fun parseToMinor(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.replace("ZMW", "", ignoreCase = true)
            .replace("K", "", ignoreCase = true)
            .replace(",", "")
            .trim()
            .trimEnd('.')
        val d = cleaned.toDoubleOrNull() ?: 0.0
        return fromZmw(d)
    }

    fun format(minorUnits: Long, includeCurrency: Boolean = true): String {
        val symbols = DecimalFormatSymbols(Locale.ENGLISH)
        val df = DecimalFormat("#,##0.00", symbols)
        val value = toZmw(minorUnits)
        val formattedNumber = df.format(value)
        return if (includeCurrency) "ZMW $formattedNumber" else formattedNumber
    }

    fun formatShort(minorUnits: Long): String {
        val symbols = DecimalFormatSymbols(Locale.ENGLISH)
        val df = DecimalFormat("#,##0.00", symbols)
        return "K${df.format(toZmw(minorUnits))}"
    }

    fun formatSigned(minorUnits: Long, direction: String): String {
        val prefix = when (direction.uppercase()) {
            "CREDIT" -> "+ "
            "DEBIT" -> "- "
            else -> ""
        }
        return "$prefix${format(minorUnits)}"
    }
}
