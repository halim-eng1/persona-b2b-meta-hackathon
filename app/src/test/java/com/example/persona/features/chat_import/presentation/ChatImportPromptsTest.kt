package com.example.persona.features.chat_import.presentation

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ChatImportPromptsTest {

    private var defaultLocale: Locale? = null

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        defaultLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun getBusinessPrompt_returnsEnglish_whenLocaleIsEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val prompt = ChatImportPrompts.getBusinessPrompt()
        assertTrue(prompt.contains("Enterprise Knowledge Base Architect"))
    }

    @Test
    fun getBusinessPrompt_returnsArabic_whenLocaleIsArabic() {
        Locale.setDefault(Locale("ar"))
        val prompt = ChatImportPrompts.getBusinessPrompt()
        assertTrue(prompt.contains("أنت خبير ومستشار استراتيجي"))
    }

    @Test
    fun getPersonalPrompt_returnsEnglish_whenLocaleIsEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val prompt = ChatImportPrompts.getPersonalPrompt()
        assertTrue(prompt.contains("Personal AI Persona Architect"))
    }

    @Test
    fun getPersonalPrompt_returnsArabic_whenLocaleIsArabic() {
        Locale.setDefault(Locale("ar"))
        val prompt = ChatImportPrompts.getPersonalPrompt()
        assertTrue(prompt.contains("أنت مستشار خبير في إعداد وتخصيص المساعدين الشخصيين"))
    }
}
