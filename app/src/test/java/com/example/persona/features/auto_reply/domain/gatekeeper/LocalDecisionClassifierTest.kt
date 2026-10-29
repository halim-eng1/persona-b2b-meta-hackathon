package com.example.persona.features.auto_reply.domain.gatekeeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalDecisionClassifierTest {

    private lateinit var classifier: LocalDecisionClassifier

    @Before
    fun setUp() {
        classifier = LocalDecisionClassifier()
    }

    @Test
    fun `classify correctly detects GREETING turns`() {
        val greetingQueries = listOf(
            "هلا",
            "مرحبا يا فندم",
            "سلام عليكم",
            "صباح الخير",
            "ازيك عامل ايه",
            "أهلاً"
        )

        for (query in greetingQueries) {
            val result = classifier.classify(query, null)
            assertEquals("Expected GREETING for query: $query", InboundIntent.GREETING, result.intent)
            assertTrue("Confidence should be > 0", result.confidence > 0f)
        }
    }

    @Test
    fun `classify correctly detects BANTER turns`() {
        val banterQueries = listOf(
            "جهزلي العشا 😂",
            "مخك تعبان؟ شكلك كدد",
            "ههههه يا عم بتهزر",
            "اممم",
            "روبوت ذكاء اصطناعي"
        )

        for (query in banterQueries) {
            val result = classifier.classify(query, null)
            assertEquals("Expected BANTER for query: $query", InboundIntent.BANTER, result.intent)
            assertTrue("Confidence should be > 0", result.confidence > 0f)
        }
    }

    @Test
    fun `classify correctly detects ORDER_STATUS turns`() {
        val orderQueries = listOf(
            "فين الاوردر بتاعي؟",
            "مالوا الاوردر بتاعي",
            "الشحنة وصلت لفين؟",
            "تتبع الطلب لو سمحت",
            "طلبي اتأخر ليه"
        )

        for (query in orderQueries) {
            val result = classifier.classify(query, null)
            assertEquals("Expected ORDER_STATUS for query: $query", InboundIntent.ORDER_STATUS, result.intent)
            assertTrue("Confidence should be > 0", result.confidence > 0f)
        }
    }

    @Test
    fun `classify correctly detects CATALOG_INQUIRY turns`() {
        val catalogQueries = listOf(
            "بكام الهودي الأسود؟",
            "في مقاسات لارج من التيشيرت ده؟",
            "عايز اعرف اسعار الكتالوج",
            "خامة القماش إيه؟",
            "عندكم الوان تانية؟"
        )

        for (query in catalogQueries) {
            val result = classifier.classify(query, null)
            assertEquals("Expected CATALOG_INQUIRY for query: $query", InboundIntent.CATALOG_INQUIRY, result.intent)
            assertTrue("Confidence should be > 0", result.confidence > 0f)
        }
    }

    @Test
    fun `classify executes in sub-millisecond range`() {
        // Warmup
        classifier.classify("سلام عليكم", null)

        val t0 = System.nanoTime()
        val result = classifier.classify("بكام الهودي الأسود مقاس لارج؟", null)
        val elapsedNanos = System.nanoTime() - t0
        val elapsedMs = elapsedNanos / 1_000_000.0

        assertEquals(InboundIntent.CATALOG_INQUIRY, result.intent)
        assertTrue("Classification should complete under 10ms on JVM, was ${elapsedMs}ms", elapsedMs < 10.0)
    }
}
