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
    private val EGYPTIAN_MOBILE_REGEX = Regex("""(?<![\d+])(?:\(?\+20|\(?0020|\(?20|\(?0)?[\s\-_()]*1[0125](?:[\s\-_()]*[0-9]){8}(?!\d)""")

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
        val match = EGYPTIAN_MOBILE_REGEX.find(asciiText) ?: return null
        val digits = match.value.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "0$digits"
            digits.length == 11 && digits.startsWith("01") -> digits
            digits.length == 12 && digits.startsWith("201") -> "0${digits.substring(2)}"
            digits.length == 14 && digits.startsWith("00201") -> "0${digits.substring(4)}"
            else -> null
        }
    }

    /**
     * ITU-T E.164 compliant phone regex: matches 7 to 15 digits with optional leading '+'.
     */
    val INTERNATIONAL_PHONE_REGEX = Regex("""^\+?[0-9]{7,15}$""")

    /**
     * Extracts candidate phone numbers from text or clipboard blocks without spanning across line breaks.
     */
    val CLIPBOARD_PHONE_REGEX = Regex("""(?:\+?[0-9]{1,4}[ \t\-]?)?(?:\([0-9]{1,4}\)[ \t\-]?)?[0-9]{2,4}[ \t\-]?[0-9]{3,4}[ \t\-]?[0-9]{3,6}""")

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
     * Extracts a candidate phone number from text or clipboard content line-by-line.
     */
    fun extractPhoneNumber(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val ascii = convertArabicDigitsToAscii(text.trim())

        // 1. Direct validation if single candidate
        val stripped = ascii.replace(Regex("""[\s\-_().,،/\\:]"""), "")
        if (isValidPhoneNumber(stripped)) return stripped

        // 2. Scan line by line to prevent cross-line concatenation
        for (line in ascii.lines()) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.isBlank()) continue
            val match = CLIPBOARD_PHONE_REGEX.find(lineTrimmed)
            if (match != null) {
                val candidate = match.value.replace(Regex("""[ \t\-_().,،/\\:]"""), "")
                if (isValidPhoneNumber(candidate)) return candidate
            }
        }
        return null
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
     * Correctly distinguishes thousands separators (e.g. 1,000 or 1,500.50) from decimal separators (e.g. 10,50).
     *
     * Returns null for blank or unparseable input.
     */
    fun parseEgyptianDecimal(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val ascii = convertArabicDigitsToAscii(input.trim())

        // Strip currency symbols and text (EGP, LE, USD, EUR, GBP, AED, SAR, ج.م, جنيه, $, €, etc.)
        val strippedCurrency = ascii.replace(
            Regex("""(?i)\b(?:egp|le|usd|eur|gbp|aed|sar)\b|(?:ج\.م|جنيه|ج\.م\.)|[$€£¥₹]"""),
            ""
        ).trim()
        if (strippedCurrency.isBlank()) return null

        // 1. Remove Arabic thousands separator (\u066C)
        var s = strippedCurrency.replace('\u066C', ' ')

        // 2. Normalize Arabic decimal separator (\u066B) to standard '.'
        s = s.replace('\u066B', '.')

        // 3. Handle commas (, and ،)
        if (s.contains('.')) {
            // Already has decimal point, e.g. "1,500.50" -> commas are thousands separators
            s = s.replace(",", "").replace("،", "")
        } else {
            // Check if comma is followed by exactly 3 digits, e.g. "1,000" or "25,500"
            if (Regex("""[,،]\d{3}(?:[,،]\d{3})*(?!\d)""").containsMatchIn(s)) {
                s = s.replace(",", "").replace("،", "")
            } else {
                // Decimal separator, e.g. "10,5" or "550,50" -> convert to '.'
                s = s.replace(',', '.').replace('،', '.')
            }
        }

        val clean = s.filter { it.isDigit() || it == '.' || it == '-' }
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
