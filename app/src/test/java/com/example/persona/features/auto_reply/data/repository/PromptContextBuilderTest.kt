package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.TopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextBuilderTest {

    @Test
    fun `buildFirstPassSystemPrompt defaults to WITTY_EGYPTIAN`() {
        val defaultPrompt = PromptContextBuilder.buildFirstPassSystemPrompt()
        val explicitWittyPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertEquals(explicitWittyPrompt, defaultPrompt)
        assertTrue(defaultPrompt.contains("Dynamic 4-Gear Calibration"))
        assertTrue(defaultPrompt.contains("ابن بلد لمّاح، واقعي، وسريع البديهة"))
    }

    @Test
    fun `buildFinalPassSystemPrompt defaults to WITTY_EGYPTIAN`() {
        val defaultPrompt = PromptContextBuilder.buildFinalPassSystemPrompt()
        val explicitWittyPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertEquals(explicitWittyPrompt, defaultPrompt)
        assertTrue(defaultPrompt.contains("Dynamic 4-Gear Calibration"))
    }

    @Test
    fun `WITTY_EGYPTIAN prompt contains the 4-gear calibration and grounded humor`() {
        val prompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertTrue(prompt.contains("Dynamic 4-Gear Calibration"))
        assertTrue(prompt.contains("Sharp Grounded Dry Humor"))
        assertTrue(prompt.contains("Comedic Defusion"))
        assertTrue(prompt.contains("خفة دم شيك بدون قلة ذوق"))
        assertTrue(prompt.contains("ابن بلد لمّاح"))
    }

    @Test
    fun `STANDARD_POLITE prompt contains calm customer service rules and zero sarcasm`() {
        val prompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        assertTrue(prompt.contains("Welcoming, courteous, and practical"))
        assertTrue(prompt.contains("3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:"))
        // Must NOT contain witty sarcasm calibration
        assertFalse(prompt.contains("Dynamic 4-Gear Calibration"))
        assertFalse(prompt.contains("الواقعية الساخرة"))
        assertFalse(prompt.contains("Comedic Defusion"))
        assertFalse(prompt.contains("ابن بلد لمّاح"))
    }

    @Test
    fun `invariant sections 1, 2, 4, 5, 6 remain 100 percent identical between tones in first pass`() {
        val witty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val polite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        // Section 1: Output format
        val sec1Witty = extractSection(witty, "1. OUTPUT FORMAT:", "2. IDENTITY & ZERO HALLUCINATION:")
        val sec1Polite = extractSection(polite, "1. OUTPUT FORMAT:", "2. IDENTITY & ZERO HALLUCINATION:")
        assertEquals("Section 1 must be identical", sec1Witty, sec1Polite)

        // Section 2: Identity & Zero Hallucination
        val sec2Witty = extractSection(witty, "2. IDENTITY & ZERO HALLUCINATION:", "3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:")
        val sec2Polite = extractSection(polite, "2. IDENTITY & ZERO HALLUCINATION:", "3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:")
        assertEquals("Section 2 must be identical", sec2Witty, sec2Polite)

        // Section 4: Actions & Workflow
        val sec4Witty = extractSection(witty, "4. ACTIONS & WORKFLOW:", "5. ORDER ACTIONS & EXTRACTION:")
        val sec4Polite = extractSection(polite, "4. ACTIONS & WORKFLOW:", "5. ORDER ACTIONS & EXTRACTION:")
        assertEquals("Section 4 must be identical", sec4Witty, sec4Polite)

        // Section 5: Order Actions & Extraction
        val sec5Witty = extractSection(witty, "5. ORDER ACTIONS & EXTRACTION:", "6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:")
        val sec5Polite = extractSection(polite, "5. ORDER ACTIONS & EXTRACTION:", "6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:")
        assertEquals("Section 5 must be identical", sec5Witty, sec5Polite)

        // Section 6: Out of bounds recovery
        val sec6Witty = witty.substringAfter("6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:").trim()
        val sec6Polite = polite.substringAfter("6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:").trim()
        assertEquals("Section 6 must be identical", sec6Witty, sec6Polite)
    }

    @Test
    fun `mandatory HITL Arabic hand-off phrase is present in both tones`() {
        val canonicalHitl = PromptContextBuilder.CANONICAL_HITL_MESSAGE
        val witty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val polite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        assertTrue(witty.contains(canonicalHitl))
        assertTrue(polite.contains(canonicalHitl))
    }

    @Test
    fun `both tones contain strict emoji mirroring and graceful closing anti-looping rules`() {
        val wittyFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val wittyFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        for (prompt in listOf(wittyFirst, politeFirst, wittyFinal, politeFinal)) {
            assertTrue(prompt.contains("STRICT EMOJI MIRRORING"))
            assertTrue(prompt.contains("Default state: ZERO EMOJIS"))
            assertTrue(prompt.contains("GRACEFUL CLOSING & THANKS (Anti-Looping)"))
            assertTrue(prompt.contains("العفو يا فندم، نورتنا ونتمنى لك يوم سعيد! 💙"))
        }
    }

    @Test
    fun `PersonaTone deserialization and fallback`() {
        // Unknown or legacy strings fall back to WITTY_EGYPTIAN
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString(null))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("CASUAL"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("PROFESSIONAL"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("UNKNOWN_TONE"))

        // Known names deserialize cleanly (case-insensitive)
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("WITTY_EGYPTIAN"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("witty_egyptian"))
        assertEquals(PersonaTone.STANDARD_POLITE, PersonaTone.fromString("STANDARD_POLITE"))
        assertEquals(PersonaTone.STANDARD_POLITE, PersonaTone.fromString("standard_polite"))
    }

    @Test
    fun `StylePostProcessor acts as passive pass-through preserving emojis and text`() {
        val processor = StylePostProcessor()
        val prefs = StylePreferences(
            personaTone = PersonaTone.WITTY_EGYPTIAN,
            styleProfile = StyleProfile.PROFESSIONAL
        )
        val context = ConversationContext(
            intent = IntentType.GENERAL,
            topic = TopicType.GENERAL,
            sentiment = SentimentType.POSITIVE,
            confidence = 1.0f
        )

        val input = "واقفين على رجلينا مستنيين الأوردر بتاعك أهو 😎 قولّي نجهزلك إيه؟"
        val output = processor.process(
            rawReply = input,
            prefs = prefs,
            context = context,
            userEmojiRate = 0.5f
        )

        assertEquals(input, output)
    }

    private fun extractSection(fullText: String, startHeader: String, endHeader: String): String {
        return fullText.substringAfter(startHeader).substringBefore(endHeader).trim()
    }
}
