package com.example.persona.features.orders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EgyptianOrderUtilsTest {

    @Test
    fun `extracts and normalizes local 11-digit Egyptian mobile numbers`() {
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("01012345678"))
        assertEquals("01198765432", EgyptianOrderUtils.extractEgyptianMobilePhone("01198765432"))
        assertEquals("01234567890", EgyptianOrderUtils.extractEgyptianMobilePhone("01234567890"))
        assertEquals("01555554444", EgyptianOrderUtils.extractEgyptianMobilePhone("01555554444"))
    }

    @Test
    fun `extracts and normalizes international Egyptian formats to 11 digits`() {
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("+201012345678"))
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("00201012345678"))
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("201012345678"))
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("1012345678"))
    }

    @Test
    fun `handles formatted strings with spaces dashes and parentheses`() {
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("+20 10 1234 5678"))
        assertEquals("01155554444", EgyptianOrderUtils.extractEgyptianMobilePhone("011-5555-4444"))
        assertEquals("01234567890", EgyptianOrderUtils.extractEgyptianMobilePhone("(012) 3456 7890"))
        assertEquals("01599998888", EgyptianOrderUtils.extractEgyptianMobilePhone("الرقم هو: +20-15-9999-8888 شكرا"))
    }

    @Test
    fun `normalizes Eastern Arabic and Persian digits to ASCII phone`() {
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("01155554444", EgyptianOrderUtils.extractEgyptianMobilePhone("۰۱۱۵۵۵۵۴۴۴۴"))
    }

    @Test
    fun `rejects invalid prefixes corrupt text and incomplete numbers`() {
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("01412345678")) // 014 does not exist
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("01612345678")) // 016 does not exist
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("12345"))
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("none"))
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("غير مسجل"))
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone(""))
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone(null))
    }

    @Test
    fun `validates mobile phone presence correctly`() {
        assertTrue(EgyptianOrderUtils.isValidEgyptianMobile("01012345678"))
        assertTrue(EgyptianOrderUtils.isValidEgyptianMobile("+201198765432"))
        assertFalse(EgyptianOrderUtils.isValidEgyptianMobile("none"))
        assertFalse(EgyptianOrderUtils.isValidEgyptianMobile(""))
        assertFalse(EgyptianOrderUtils.isValidEgyptianMobile(null))
    }

    @Test
    fun `parses Egyptian decimal monetary amounts with Arabic and English numerals`() {
        assertEquals(550.0, EgyptianOrderUtils.parseEgyptianDecimal("550")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("550.50")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("550,50")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("٥٥٠٫٥٠")!!, 0.001)
        assertEquals(300.0, EgyptianOrderUtils.parseEgyptianDecimal("٣٠٠ ج.م")!!, 0.001)
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal(""))
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal("   "))
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal(null))
    }

    @Test
    fun `formats money cleanly without floating point precision noise`() {
        assertEquals("550", EgyptianOrderUtils.formatMoney(550.0))
        assertEquals("550", EgyptianOrderUtils.formatMoney(550.0000000001))
        assertEquals("550.50", EgyptianOrderUtils.formatMoney(550.5))
        assertEquals("300.25", EgyptianOrderUtils.formatMoney(300.25))
    }
}
