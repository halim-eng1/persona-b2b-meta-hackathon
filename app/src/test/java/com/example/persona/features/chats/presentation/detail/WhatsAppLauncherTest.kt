package com.example.persona.features.chats.presentation.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppLauncherTest {

    @Test
    fun `normalizePhoneNumber normalizes Egyptian local numbers`() {
        assertEquals("201012345678", WhatsAppLauncher.normalizePhoneNumber("01012345678"))
        assertEquals("201198765432", WhatsAppLauncher.normalizePhoneNumber("01198765432"))
        assertEquals("201234567890", WhatsAppLauncher.normalizePhoneNumber("01234567890"))
        assertEquals("201500000000", WhatsAppLauncher.normalizePhoneNumber("01500000000"))
    }

    @Test
    fun `normalizePhoneNumber strips spaces and special characters`() {
        assertEquals("201012345678", WhatsAppLauncher.normalizePhoneNumber("+20 101 234 5678"))
        assertEquals("201012345678", WhatsAppLauncher.normalizePhoneNumber("+20-101-234-5678"))
    }

    @Test
    fun `normalizePhoneNumber handles international prefixes`() {
        assertEquals("966501234567", WhatsAppLauncher.normalizePhoneNumber("+966501234567"))
        assertEquals("201012345678", WhatsAppLauncher.normalizePhoneNumber("201012345678"))
    }
}
