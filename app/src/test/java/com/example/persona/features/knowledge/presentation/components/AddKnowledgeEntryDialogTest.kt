package com.example.persona.features.knowledge.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AddKnowledgeEntryDialogTest {

    @Test
    fun `sanitizeArabicIndicPrice normalizes Arabic digits and decimal separators to ASCII`() {
        assertEquals("150", sanitizeArabicIndicPrice("١٥٠"))
        assertEquals("299.50", sanitizeArabicIndicPrice("٢٩٩٫٥٠"))
        assertEquals("45.5", sanitizeArabicIndicPrice("٤٥،٥"))
        assertEquals("1200.75", sanitizeArabicIndicPrice("1200,75"))
        assertEquals("500", sanitizeArabicIndicPrice(" ٥٠٠ "))
    }
}
