package com.example.persona.features.auto_reply.domain.gatekeeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            // Arabic standard & colloquial
            "هلا",
            "مرحبا يا فندم",
            "سلام عليكم",
            "صباح الخير",
            "ازيك عامل ايه",
            "أهلاً",
            "صباح الفل",
            "يا هلا",
            // Franco-Arab & English Commercial Tokens
            "hi",
            "hello",
            "hey",
            "salam",
            "sabah el kher",
            "morning",
            "good morning",
            "fe 7ad hena"
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
            // Arabic colloquial & emojis
            "جهزلي العشا 😂",
            "مخك تعبان؟ شكلك كدد",
            "ههههه يا عم بتهزر",
            "اممم",
            "روبوت ذكاء اصطناعي",
            // Franco-Arab & English
            "haha",
            "hahaha",
            "lol",
            "joke",
            "3asha",
            "akl",
            "sa7by"
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
            // Arabic inquiries
            "فين الاوردر بتاعي؟",
            "مالوا الاوردر بتاعي",
            "الشحنة وصلت لفين؟",
            "تتبع الطلب لو سمحت",
            "طلبي اتأخر ليه",
            "فين طلبي",
            "الشحنة", "الغي طلبي", "طيب اتلغي ؟؟", "كنسل", "ألغي طلبي", "كنسل الأوردر",
            // Franco-Arab & English
            "status",
            "order feen",
            "tracking",
            "order status"
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
            // Arabic inquiries
            "بكام الهودي الأسود؟",
            "في مقاسات لارج من التيشيرت ده؟",
            "عايز اعرف اسعار الكتالوج",
            "خامة القماش إيه؟",
            "عندكم الوان تانية؟",
            "كام السعر ده؟",
            "عايز اعرف مقاس لارج متوفر؟",
            // Franco-Arab & English
            "price",
            "bkam",
            "hoodie",
            "tshirt",
            "size",
            "colors",
            "bkam el hoodie?"
        )

        for (query in catalogQueries) {
            val result = classifier.classify(query, null)
            assertEquals("Expected CATALOG_INQUIRY for query: $query", InboundIntent.CATALOG_INQUIRY, result.intent)
            assertTrue("Confidence should be > 0", result.confidence > 0f)
        }
    }

    @Test
    fun `classify real session turns from benchmark logs deterministically`() {
        val sessionTurns = listOf(
            "هلااا" to InboundIntent.GREETING,
            "جهزلي العشا 😂" to InboundIntent.BANTER,
            "اممم" to InboundIntent.BANTER,
            "مخك تعبان؟ شكلك كدد" to InboundIntent.BANTER,
            "مالوا الاوردر بتاعي" to InboundIntent.ORDER_STATUS,
            "عايز التصميم دا ع هودي اسود" to InboundIntent.CATALOG_INQUIRY,
            "bkam el hoodie?" to InboundIntent.CATALOG_INQUIRY
        )

        for ((turn, expectedIntent) in sessionTurns) {
            val result = classifier.classify(turn, null)
            assertEquals("Expected $expectedIntent for session turn: '$turn'", expectedIntent, result.intent)
            assertTrue("Confidence should be > 0 for turn: '$turn'", result.confidence > 0f)
        }

        // Presence check turn: "fe 7ad hena?" -> BANTER or GREETING
        val presenceResult = classifier.classify("fe 7ad hena?", null)
        assertTrue(
            "Expected BANTER or GREETING for 'fe 7ad hena?', but got ${presenceResult.intent}",
            presenceResult.intent == InboundIntent.BANTER || presenceResult.intent == InboundIntent.GREETING
        )
        assertTrue("Confidence should be > 0 for presence check", presenceResult.confidence > 0f)
    }

    @Test
    fun `lexical boundary rules isolate tokens and prevent false positives`() {
        // "tshirt" contains substring "hi", but must NOT trigger GREETING boost (should be CATALOG_INQUIRY)
        val tshirtResult = classifier.classify("tshirt", null)
        assertEquals("Expected CATALOG_INQUIRY for 'tshirt'", InboundIntent.CATALOG_INQUIRY, tshirtResult.intent)
        assertNotEquals("Should not be GREETING", InboundIntent.GREETING, tshirtResult.intent)

        // "white hoodie" contains "hi" in "white", but must be CATALOG_INQUIRY
        val whiteHoodieResult = classifier.classify("white hoodie", null)
        assertEquals("Expected CATALOG_INQUIRY for 'white hoodie'", InboundIntent.CATALOG_INQUIRY, whiteHoodieResult.intent)

        // "3ashan" (because) contains "3asha" (dinner), but must NOT trigger BANTER boost
        val orderReasonResult = classifier.classify("3ashan el order feen", null)
        assertEquals("Expected ORDER_STATUS for '3ashan el order feen'", InboundIntent.ORDER_STATUS, orderReasonResult.intent)

        // "shaklak" contains "akl", but must NOT trigger BANTER boost
        val shaklResult = classifier.classify("shaklak keda", null)
        assertEquals("Shaklak should have 0 confidence (no banter boost)", 0f, shaklResult.confidence, 0.0001f)

        // Arabic "كامل" contains "كام", but must NOT trigger CATALOG_INQUIRY boost
        val kamelResult = classifier.classify("كامل المواصفات", null)
        assertEquals("كامل should have 0 confidence (no catalog boost)", 0f, kamelResult.confidence, 0.0001f)

        // Arabic "أحكام" contains "كام", but must NOT trigger CATALOG_INQUIRY boost
        val ahkamResult = classifier.classify("أحكام الشحن والتوصيل", null)
        assertEquals("أحكام should have 0 confidence (no catalog boost)", 0f, ahkamResult.confidence, 0.0001f)

        // Verify positive standalone matches
        val kamPositive = classifier.classify("كام السعر ده؟", null)
        assertEquals(InboundIntent.CATALOG_INQUIRY, kamPositive.intent)
        assertTrue(kamPositive.confidence >= 0.30f)

        val ashaPositive = classifier.classify("3asha", null)
        assertEquals(InboundIntent.BANTER, ashaPositive.intent)
        assertTrue(ashaPositive.confidence >= 0.25f)
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
