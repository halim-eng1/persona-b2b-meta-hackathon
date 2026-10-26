package com.example.persona.features.chat_import.data.normalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ArabicTextNormalizerTest {

    private val normalizer = ArabicTextNormalizer()

    @Test
    fun normalize_tashkeel_removesDiacritics() {
        val result = normalizer.normalize("مَرْحَبًا")
        assertEquals("مرحبا", result)
    }

    @Test
    fun normalize_alefHamzaAbove_standardizes() {
        val result = normalizer.normalize("أحمد")
        assertEquals("احمد", result)
    }

    @Test
    fun normalize_alefHamzaBelow_standardizes() {
        val result = normalizer.normalize("إسلام")
        assertEquals("اسلام", result)
    }

    @Test
    fun normalize_alefMadda_standardizes() {
        val result = normalizer.normalize("آمين")
        assertEquals("امين", result)
    }

    @Test
    fun normalize_tehMarbuta_convertsToHeh() {
        val result = normalizer.normalize("مدرسة")
        assertEquals("مدرسه", result)
    }

    @Test
    fun normalize_alefMaksura_convertsToYaa() {
        val result = normalizer.normalize("على")
        assertEquals("علي", result)
    }

    @Test
    fun normalize_tatweel_removesTatweel() {
        val result = normalizer.normalize("هـــذا")
        assertEquals("هذا", result)
    }

    @Test
    fun normalize_pureLatin_returnsNull() {
        val result = normalizer.normalize("Hello World")
        assertNull(result)
    }

    @Test
    fun normalize_mixedText_normalizesArabicPreservesLatin() {
        val result = normalizer.normalize("Hello مَرْحَبًا")
        assertEquals("Hello مرحبا", result)
    }

    @Test
    fun normalize_emptyString_returnsNull() {
        val result = normalizer.normalize("")
        assertNull(result)
    }

    @Test
    fun normalize_emojiOnly_returnsNull() {
        val result = normalizer.normalize("👋😄")
        assertNull(result)
    }

    @Test
    fun normalize_wawWithHamza_convertsToWaw() {
        val result = normalizer.normalize("سُؤَال")
        assertEquals("سوال", result)
    }

    @Test
    fun normalize_performanceCheck() {
        val input = "مَرْحَبًا بِكَ يَا صَدِيقِي فِي هَـــذَا الْيَوْمِ الْجَمِيلِ"
        
        // Warm up
        repeat(100) { normalizer.normalize(input) }

        val duration = measureTimeMillis {
            repeat(1000) {
                normalizer.normalize(input)
            }
        }
        
        // 1000 normalizations should execute within reasonable limits (expected < 50ms)
        assertTrue("Normalization took too long: ${duration}ms", duration < 200)
    }
}
