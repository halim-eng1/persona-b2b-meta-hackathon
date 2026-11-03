package com.example.persona.features.orders.domain

import java.util.Locale

/**
 * Globalized currency formatting engine supporting multi-currency and localization:
 * - In Arabic ("ar"): outputs Egyptian Pound symbol "ج.م" (e.g. "\u202A550 ج.م\u202C")
 * - In English and other locales: outputs standard dollar symbol "$" (e.g. "\u202A$550\u202C")
 * Wrapped in Unicode Left-to-Right Embedding (\u202A ... \u202C) to ensure symbol and numerals
 * are rendered without BiDi inversion in RTL/LTR layouts.
 */
object CurrencyFormatter {

    fun getCurrencySymbol(appLanguage: String = Locale.getDefault().language): String {
        return when (appLanguage.lowercase()) {
            "ar" -> "ج.م"
            else -> "$"
        }
    }

    fun formatAmountWithCurrency(amount: Double, appLanguage: String = Locale.getDefault().language): String {
        val formattedNumber = EgyptianOrderUtils.formatMoney(amount)
        val symbol = getCurrencySymbol(appLanguage)
        return when (appLanguage.lowercase()) {
            "ar" -> "\u202A$formattedNumber $symbol\u202C"
            else -> "\u202A$symbol$formattedNumber\u202C"
        }
    }
}
