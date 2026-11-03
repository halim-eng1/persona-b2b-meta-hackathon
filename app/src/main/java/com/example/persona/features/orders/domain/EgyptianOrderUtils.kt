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

        // 1. Guard against non-Egyptian international prefixes (+ or 00)
        val stripped = asciiText.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        if (stripped.contains("+") && !stripped.contains("+20")) return null
        if (stripped.startsWith("00") && !stripped.startsWith("0020")) return null

        val strippedMatch = EGYPTIAN_MOBILE_REGEX.find(stripped)
        if (strippedMatch != null) {
            return "0${strippedMatch.groupValues[1]}"
        }

        // 2. Fallback to searching unstripped ascii text
        if (asciiText.contains("+") && !asciiText.contains("+20")) return null
        val match = EGYPTIAN_MOBILE_REGEX.find(asciiText) ?: return null
        return "0${match.groupValues[1]}"
    }

    /**
     * ITU-T E.164 compliant phone regex: matches 7 to 15 digits with optional leading '+'.
     */
    val INTERNATIONAL_PHONE_REGEX = Regex("""^\+?[0-9]{7,15}$""")

    /**
     * Extracts candidate phone numbers from multi-line text or clipboard blocks.
     */
    val CLIPBOARD_PHONE_REGEX = Regex("""(?:\+?[0-9]{1,4}[\s\-]?)?(?:\([0-9]{1,4}\)[\s\-]?)?[0-9]{2,4}[\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,6}""")

    /**
     * Validates whether [phone] represents a valid international phone number under ITU-T E.164
     * (7 to 15 digits, optional leading '+', whitespace and formatting tolerant).
     */
    fun isValidPhoneNumber(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        val ascii = convertArabicDigitsToAscii(phone.trim())
        val stripped = ascii.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        val digitsOnly = if (stripped.startsWith("+")) stripped.substring(1) else stripped
        return digitsOnly.all { it.isDigit() } && digitsOnly.length in 7..15
    }

    /**
     * Extracts a candidate phone number from text or clipboard content.
     */
    fun extractPhoneNumber(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val ascii = convertArabicDigitsToAscii(text.trim())

        // 1. Direct validation if single candidate
        val stripped = ascii.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        if (isValidPhoneNumber(stripped)) return stripped

        // 2. Pattern matching within clipboard text
        val match = CLIPBOARD_PHONE_REGEX.find(ascii) ?: return null
        val candidate = match.value.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        return if (isValidPhoneNumber(candidate)) candidate else null
    }

    /**
     * Validates whether [phone] contains or represents a valid Egyptian mobile phone.
     */
    fun isValidEgyptianMobile(phone: String?): Boolean {
        return extractEgyptianMobilePhone(phone) != null
    }

    /**
     * Formats an Egyptian or international phone number for consistent BiDi display across Arabic (RTL)
     * and English (LTR) layouts without number inversion or '+' sign swapping.
     * Wrapped in Unicode Left-to-Right Embedding (\u202A ... \u202C).
     *
     * Example: "01012460925" or "+20 10 1246 0925" -> "\u202A010 1246 0925\u202C"
     */
    fun formatDisplayPhone(rawPhone: String?): String {
        if (rawPhone.isNullOrBlank()) return ""
        val clean = convertArabicDigitsToAscii(rawPhone.trim())
        val normalized = extractEgyptianMobilePhone(clean)
        val formatted = if (normalized != null && normalized.length == 11) {
            "${normalized.substring(0, 3)} ${normalized.substring(3, 7)} ${normalized.substring(7)}"
        } else {
            clean
        }
        // Wrap in Left-to-Right Embedding (LRE \u202A ... PDF \u202C) to lock phone direction
        return "\u202A$formatted\u202C"
    }

    /**
     * Formats a contact or phone display string: if the input is a valid phone number,
     * it formats it cleanly and locks its LTR direction so it never inverts in Arabic UI.
     */
    fun formatContactDisplayName(nameOrPhone: String?): String {
        if (nameOrPhone.isNullOrBlank()) return ""
        val clean = nameOrPhone.trim()
        val isPhone = isValidPhoneNumber(clean) || extractEgyptianMobilePhone(clean) != null
        if (isPhone && (clean.startsWith("+") || clean.startsWith("0") || clean.startsWith("2") || clean.all { it.isDigit() || it.isWhitespace() || it in "+-()" })) {
            return formatDisplayPhone(clean)
        }
        return clean
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
