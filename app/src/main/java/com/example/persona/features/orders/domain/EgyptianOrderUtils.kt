package com.example.persona.features.orders.domain

import java.util.Locale

/**
 * Commercial formatting and parsing utilities tailored for Egyptian B2B fulfillment:
 * - Phone numbers: Egyptian mobile networks (010, 011, 012, 015) with normalization to 11 digits.
 * - Arabic-Indic numerals: conversion of Eastern Arabic (٠-٩) and Persian (۰-۹) numerals.
 * - Financial amounts: decimal parsing with comma/point tolerance and clean COD string formatting.
 */
object EgyptianOrderUtils {

    /**
     * Regex matching Egyptian mobile numbers (10 digits starting with 10, 11, 12, or 15):
     * e.g. 1[0125][0-9]{8}.
     * Accepts optional country prefix (+20, 0020, 20, or leading 0).
     */
    private val EGYPTIAN_MOBILE_REGEX = Regex("""(?:\+?20|0020|20|0)?(1[0125][0-9]{8})""")

    /**
     * Converts Eastern Arabic (٠-٩) and Persian (۰-۹) digits to ASCII standard digits (0-9).
     */
    fun convertArabicDigitsToAscii(input: String): String {
        return input.map { ch ->
            when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch
            }
        }.joinToString("")
    }

    /**
     * Extracts and normalizes an 11-digit Egyptian mobile phone number (`01xxxxxxxxx`).
     *
     * Handles:
     * - International formats (+2010xxxxxxxx, 002010xxxxxxxx, 2010xxxxxxxx)
     * - Local 11-digit format (010xxxxxxxx, 011xxxxxxxx, 012xxxxxxxx, 015xxxxxxxx)
     * - 10-digit without leading zero (10xxxxxxxx)
     * - Formatted strings with spaces, dashes, or parentheses (e.g. "+20 10 1234 5678")
     * - Eastern Arabic numerals (e.g. "٠١٠١٢٣٤٥٦٧٨")
     *
     * Returns null if no valid Egyptian mobile number is present.
     */
    fun extractEgyptianMobilePhone(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val asciiText = convertArabicDigitsToAscii(text)

        // 1. Try stripping common formatting separators
        val stripped = asciiText.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        val strippedMatch = EGYPTIAN_MOBILE_REGEX.find(stripped)
        if (strippedMatch != null) {
            return "0${strippedMatch.groupValues[1]}"
        }

        // 2. Fallback to searching unstripped ascii text
        val match = EGYPTIAN_MOBILE_REGEX.find(asciiText) ?: return null
        return "0${match.groupValues[1]}"
    }

    /**
     * Validates whether [phone] contains or represents a valid Egyptian mobile phone.
     */
    fun isValidEgyptianMobile(phone: String?): Boolean {
        return extractEgyptianMobilePhone(phone) != null
    }

    /**
     * Parses numeric/monetary inputs entered with Arabic or English digits,
     * supporting decimal points (.), Arabic decimal separators (٫), commas (، ,),
     * and currency suffixes.
     *
     * Returns null for blank or unparseable input.
     */
    fun parseEgyptianDecimal(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val ascii = convertArabicDigitsToAscii(input.trim())
        val normalized = ascii.map { ch ->
            when (ch) {
                '٫', '،', ',' -> '.'
                else -> ch
            }
        }.joinToString("")
        val clean = normalized.filter { it.isDigit() || it == '.' || it == '-' }
        return clean.toDoubleOrNull()
    }

    /**
     * Formats monetary COD totals cleanly without floating point precision noise:
     * - 550.0 -> "550"
     * - 550.5 -> "550.50"
     * - 550.0000000001 -> "550"
     */
    fun formatMoney(amount: Double): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            "%.2f".format(Locale.US, rounded)
        }
    }
}
