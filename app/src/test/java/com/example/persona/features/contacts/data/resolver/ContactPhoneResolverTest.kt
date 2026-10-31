package com.example.persona.features.contacts.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactPhoneResolverTest {

    @Test
    fun `extractPhoneFromTitle normalizes raw Egyptian mobile numbers`() {
        assertEquals("01009251246", ContactPhoneResolver.extractPhoneFromTitle("+20 10 09251246"))
        assertEquals("01009251246", ContactPhoneResolver.extractPhoneFromTitle("0100 925 1246"))
        assertEquals("01009251246", ContactPhoneResolver.extractPhoneFromTitle("+201009251246"))
        assertEquals("01123456789", ContactPhoneResolver.extractPhoneFromTitle("01123456789"))
    }

    @Test
    fun `extractPhoneFromTitle returns null for non-numeric contact names`() {
        assertNull(ContactPhoneResolver.extractPhoneFromTitle("My Number 2•"))
        assertNull(ContactPhoneResolver.extractPhoneFromTitle("Ahmed Ali"))
        assertNull(ContactPhoneResolver.extractPhoneFromTitle("Halim•"))
        assertNull(ContactPhoneResolver.extractPhoneFromTitle(""))
        assertNull(ContactPhoneResolver.extractPhoneFromTitle(null))
    }

    @Test
    fun `normalizePhoneNumber standardizes diverse number formats`() {
        assertEquals("01009251246", ContactPhoneResolver.normalizePhoneNumber("+20 10 0925 1246"))
        assertEquals("01234567890", ContactPhoneResolver.normalizePhoneNumber("(012) 3456-7890"))
        assertEquals("01555554444", ContactPhoneResolver.normalizePhoneNumber("٠١٥٥٥٥٥٤٤٤٤"))
        assertNull(ContactPhoneResolver.normalizePhoneNumber("abc"))
    }
}
