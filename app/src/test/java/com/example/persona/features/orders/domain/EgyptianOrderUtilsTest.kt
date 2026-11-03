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
    fun `validates ITU-T E164 international phone numbers correctly`() {
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("01012345678"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("+15551234567"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("+201012345678"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("+442071838750"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("+966 50 123 4567"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("(555) 123-4567"))
        assertTrue(EgyptianOrderUtils.isValidPhoneNumber("٠١٠١٢٣٤٥٦٧٨"))

        assertFalse(EgyptianOrderUtils.isValidPhoneNumber("12345")) // < 7 digits
        assertFalse(EgyptianOrderUtils.isValidPhoneNumber("+1234567890123456")) // > 15 digits
        assertFalse(EgyptianOrderUtils.isValidPhoneNumber("not-a-number"))
        assertFalse(EgyptianOrderUtils.isValidPhoneNumber(""))
        assertFalse(EgyptianOrderUtils.isValidPhoneNumber(null))
    }

    @Test
    fun `extracts international phone numbers from text and clipboard`() {
        assertEquals("01012345678", EgyptianOrderUtils.extractPhoneNumber("01012345678"))
        assertEquals("+15551234567", EgyptianOrderUtils.extractPhoneNumber("+15551234567"))
        assertEquals("+15551234567", EgyptianOrderUtils.extractPhoneNumber("Please reach me at +1 555 123 4567 for details"))
        assertEquals("01012345678", EgyptianOrderUtils.extractPhoneNumber("الرقم: 01012345678"))
        assertNull(EgyptianOrderUtils.extractPhoneNumber("No numbers here"))
        assertNull(EgyptianOrderUtils.extractPhoneNumber(""))
        assertNull(EgyptianOrderUtils.extractPhoneNumber(null))
    }

    @Test
    fun `formatDisplayPhone wraps phone in BiDi isolating markers`() {
        val formattedUs = EgyptianOrderUtils.formatDisplayPhone("+15551234567")
        assertTrue(formattedUs.startsWith("\u202A"))
        assertTrue(formattedUs.endsWith("\u202C"))
        assertTrue(formattedUs.contains("+15551234567"))

        val formattedEg = EgyptianOrderUtils.formatDisplayPhone("01012345678")
        assertTrue(formattedEg.startsWith("\u202A"))
        assertTrue(formattedEg.endsWith("\u202C"))
        assertTrue(formattedEg.contains("010 1234 5678"))
    }

    @Test
    fun `parses Egyptian decimal monetary amounts with Arabic and English numerals`() {
        assertEquals(550.0, EgyptianOrderUtils.parseEgyptianDecimal("550")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("550.50")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("550,50")!!, 0.001)
        assertEquals(550.50, EgyptianOrderUtils.parseEgyptianDecimal("٥٥٠٫٥٠")!!, 0.001)
        assertEquals(300.0, EgyptianOrderUtils.parseEgyptianDecimal("٣٠٠ ج.م")!!, 0.001)
        assertEquals(1000.0, EgyptianOrderUtils.parseEgyptianDecimal("1,000")!!, 0.001)
        assertEquals(1000.0, EgyptianOrderUtils.parseEgyptianDecimal("1،000")!!, 0.001)
        assertEquals(1500.50, EgyptianOrderUtils.parseEgyptianDecimal("1,500.50")!!, 0.001)
        assertEquals(10.50, EgyptianOrderUtils.parseEgyptianDecimal("10,50")!!, 0.001)
        assertEquals(10.5, EgyptianOrderUtils.parseEgyptianDecimal("10,5")!!, 0.001)
        assertEquals(1000000.0, EgyptianOrderUtils.parseEgyptianDecimal("1,000,000")!!, 0.001)
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal(""))
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal("   "))
        assertNull(EgyptianOrderUtils.parseEgyptianDecimal(null))
    }

    @Test
    fun `extracts Egyptian mobile number in mixed text containing foreign phone`() {
        val mixed = "US contact +15551234567, local delivery brother 01012345678"
        assertEquals("01012345678", EgyptianOrderUtils.extractEgyptianMobilePhone(mixed))
        assertNull(EgyptianOrderUtils.extractEgyptianMobilePhone("+15551234567"))
    }

    @Test
    fun `formats money cleanly without floating point precision noise`() {
        assertEquals("550", EgyptianOrderUtils.formatMoney(550.0))
        assertEquals("550", EgyptianOrderUtils.formatMoney(550.0000000001))
        assertEquals("550.50", EgyptianOrderUtils.formatMoney(550.5))
        assertEquals("300.25", EgyptianOrderUtils.formatMoney(300.25))
    }
}
