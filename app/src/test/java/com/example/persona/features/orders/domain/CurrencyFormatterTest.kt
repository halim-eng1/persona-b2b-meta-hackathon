package com.example.persona.features.orders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun `getCurrencySymbol returns Egyptian Pound for Arabic and dollar for other languages`() {
        assertEquals("ج.م", CurrencyFormatter.getCurrencySymbol("ar"))
        assertEquals("ج.م", CurrencyFormatter.getCurrencySymbol("AR"))
        assertEquals("$", CurrencyFormatter.getCurrencySymbol("en"))
        assertEquals("$", CurrencyFormatter.getCurrencySymbol("fr"))
        assertEquals("$", CurrencyFormatter.getCurrencySymbol(""))
    }

    @Test
    fun `formatAmountWithCurrency formats Arabic amounts with trailing Egyptian currency and BiDi isolation`() {
        val formatted = CurrencyFormatter.formatAmountWithCurrency(550.0, "ar")
        assertTrue(formatted.startsWith("\u202A"))
        assertTrue(formatted.endsWith("\u202C"))
        assertTrue(formatted.contains("550 ج.م"))
    }

    @Test
    fun `formatAmountWithCurrency formats English amounts with leading dollar sign and BiDi isolation`() {
        val formatted = CurrencyFormatter.formatAmountWithCurrency(550.0, "en")
        assertTrue(formatted.startsWith("\u202A"))
        assertTrue(formatted.endsWith("\u202C"))
        assertTrue(formatted.contains("$550"))
    }

    @Test
    fun `formatAmountWithCurrency handles decimals correctly`() {
        val formattedAr = CurrencyFormatter.formatAmountWithCurrency(550.50, "ar")
        assertTrue(formattedAr.contains("550.50 ج.م"))

        val formattedEn = CurrencyFormatter.formatAmountWithCurrency(79.99, "en")
        assertTrue(formattedEn.contains("$79.99"))
    }
}
