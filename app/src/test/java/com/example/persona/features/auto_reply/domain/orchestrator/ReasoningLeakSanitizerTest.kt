package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.features.auto_reply.data.repository.PromptContextBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningLeakSanitizerTest {

    @Test
    fun `decodeAgenticResponseSafely decodes flat string refined_search_keyword`() {
        val raw = """
            {
              "action": "RE_QUERY",
              "refined_search_keyword": "كتالوج المنتجات الأسعار",
              "order_action": null,
              "final_reply_text": "ثواني يا فندم براجع الكتالوج",
              "extracted_order": null
            }
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertEquals("كتالوج المنتجات الأسعار", response.refinedSearchKeyword)
        assertEquals("ثواني يا فندم براجع الكتالوج", response.finalReplyText)
    }

    @Test
    fun `decodeAgenticResponseSafely normalizes JSON array refined_search_keyword into flat string`() {
        // Exact structure from production log persona_2026-10-27_12-07-09.log
        val raw = """
            {"action":"RE_QUERY","refined_search_keyword":["كتالوج المنتجات","الأسعار الرسمية","مواصفات الخامات","التصاميم المخصصة"],"order_action":null,"final_reply_text":"ثواني يا فندم، أنا أتحقق من كتالوج المنتجات","extracted_order":null}
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertEquals(
            "كتالوج المنتجات الأسعار الرسمية مواصفات الخامات التصاميم المخصصة",
            response.refinedSearchKeyword
        )
        assertEquals("ثواني يا فندم، أنا أتحقق من كتالوج المنتجات", response.finalReplyText)
    }

    @Test
    fun `decodeAgenticResponseSafely handles single quotes and whitespace in refined_search_keyword array`() {
        val raw = """
            {
              "action": "RE_QUERY",
              "refined_search_keyword": [
                'تيشيرتات صيفي',
                'هوديز أوفر سايز'
              ],
              "order_action": null,
              "final_reply_text": "جاري البحث"
            }
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertEquals("تيشيرتات صيفي هوديز أوفر سايز", response.refinedSearchKeyword)
    }

    @Test
    fun `decodeAgenticResponseSafely handles empty array in refined_search_keyword`() {
        val raw = """
            {
              "action": "RE_QUERY",
              "refined_search_keyword": [],
              "order_action": null,
              "final_reply_text": "جاري البحث"
            }
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertTrue(response.refinedSearchKeyword.isNullOrBlank())
    }

    @Test
    fun `decodeAgenticResponseSafely handles unquoted words in array`() {
        val raw = """
            {
              "action": "RE_QUERY",
              "refined_search_keyword": [هوديز, تيشيرتات],
              "order_action": null,
              "final_reply_text": "جاري البحث"
            }
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertEquals("هوديز تيشيرتات", response.refinedSearchKeyword)
    }

    @Test
    fun `decodeAgenticResponseSafely parses null and missing refined_search_keyword`() {
        val rawNull = """{"action":"REPLY","refined_search_keyword":null,"final_reply_text":"أهلاً بك"}"""
        val responseNull = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawNull)
        assertEquals("REPLY", responseNull.action)
        assertNull(responseNull.refinedSearchKeyword)

        val rawMissing = """{"action":"REPLY","final_reply_text":"أهلاً بك"}"""
        val responseMissing = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawMissing)
        assertEquals("REPLY", responseMissing.action)
        assertNull(responseMissing.refinedSearchKeyword)
    }

    @Test
    fun `decodeAgenticResponseSafely repairs unescaped quotes with array keywords and markdown fences`() {
        val raw = """
            ```json
            {
              "action": "RE_QUERY",
              "refined_search_keyword": ["منتجات سادة", "أسعار"],
              "final_reply_text": "سعر "الهودي" متاح في الكتالوج"
            }
            ```
        """.trimIndent()

        val response = ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)
        assertEquals("RE_QUERY", response.action)
        assertEquals("منتجات سادة أسعار", response.refinedSearchKeyword)
        assertNotNull(response.finalReplyText)
    }

    @Test
    fun `decodeAgenticResponseSafely normalizes malformed final_reply_text keys with spaces and missing underscores`() {
        val rawWithSpace = """{"action":"REPLY","final_reply text":"أهلاً بك يا فندم"}"""
        val resp1 = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawWithSpace)
        assertEquals("REPLY", resp1.action)
        assertEquals("أهلاً بك يا فندم", resp1.finalReplyText)

        val rawMissingUnderscore = """{"action":"REPLY","final_replytext":"منورنا يا باشا"}"""
        val resp2 = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawMissingUnderscore)
        assertEquals("REPLY", resp2.action)
        assertEquals("منورنا يا باشا", resp2.finalReplyText)

        val rawFullSpaces = """{"action":"REPLY","final reply text":"تحت أمرك"}"""
        val resp3 = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawFullSpaces)
        assertEquals("REPLY", resp3.action)
        assertEquals("تحت أمرك", resp3.finalReplyText)

        val rawComplex = """
            {
              "action": "REPLY",
              "final reply text": "تمام يا غالي",
              "order action": "CREATE",
              "extracted order": {
                "customer name": "أحمد",
                "delivery address": "المعادي",
                "order details": "2 هودي أسود",
                "custom notes": "مستعجل"
              }
            }
        """.trimIndent()
        val resp4 = ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawComplex)
        assertEquals("REPLY", resp4.action)
        assertEquals("تمام يا غالي", resp4.finalReplyText)
        assertEquals("CREATE", resp4.orderAction)
        assertEquals("أحمد", resp4.extractedOrder?.customerName)
        assertEquals("المعادي", resp4.extractedOrder?.deliveryAddress)
        assertEquals("2 هودي أسود", resp4.extractedOrder?.orderDetails)
        assertEquals("مستعجل", resp4.extractedOrder?.customNotes)
    }

    @Test
    fun `PromptContextBuilder system prompt includes tightened schema and array prohibition`() {
        val prompt = PromptContextBuilder.buildFirstPassSystemPrompt()
        assertTrue(
            "Prompt must enforce flat string for refined_search_keyword",
            prompt.contains("A SINGLE flat string with 2-4 space-separated keywords")
        )
        assertTrue(
            "Prompt must emphasize NEVER a JSON array []",
            prompt.contains("NEVER a JSON array [] or list!")
        )
        assertTrue(
            "Prompt must emphasize plain text in RE_QUERY action instructions",
            prompt.contains("refined_search_keyword MUST always be a single plain text string, never an array of strings.")
        )
        assertTrue(
            "Prompt must emphasize exact key for final_reply_text",
            prompt.contains("MUST be exact key 'final_reply_text', no spaces")
        )
    }
}
