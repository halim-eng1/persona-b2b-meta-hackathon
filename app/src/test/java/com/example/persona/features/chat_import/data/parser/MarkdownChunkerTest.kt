package com.example.persona.features.chat_import.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarkdownChunkerTest {

    private lateinit var chunker: MarkdownChunker

    @Before
    fun setUp() {
        chunker = MarkdownChunker()
    }

    @Test
    fun `hierarchical headers maintain breadcrumb stack`() {
        val markdown = """
            # سياسة المتجر
            ## سياسة الاسترجاع
            ### الشروط
            يمكن استرجاع المنتج خلال 14 يوماً من تاريخ الاستلام.
        """.trimIndent()

        val chunks = chunker.chunk(markdown)

        assertEquals(1, chunks.size)
        val chunk = chunks[0]
        assertEquals("سياسة المتجر - سياسة الاسترجاع - الشروط", chunk.headerContext)
        assertTrue(chunk.chunkText.startsWith("سياسة المتجر - سياسة الاسترجاع - الشروط: "))
        assertTrue(chunk.chunkText.contains("يمكن استرجاع المنتج خلال 14 يوماً"))
    }

    @Test
    fun `shallower heading pops deeper headings from breadcrumb stack`() {
        val markdown = """
            # المتجر
            ## الشحن
            ### أسعار الشحن
            سعر الشحن 50 جنيه للقاهرة.
            ## الاسترجاع
            ### فترة الاسترجاع
            الفترة 14 يوم.
        """.trimIndent()

        val chunks = chunker.chunk(markdown)

        assertEquals(2, chunks.size)
        assertEquals("المتجر - الشحن - أسعار الشحن", chunks[0].headerContext)
        assertEquals("المتجر - الاسترجاع - فترة الاسترجاع", chunks[1].headerContext)
        assertTrue(chunks[0].chunkText.contains("50 جنيه"))
        assertTrue(chunks[1].chunkText.contains("14 يوم"))
    }

    @Test
    fun `stray markdown horizontal rules are stripped from chunk bodies`() {
        val markdown = """
            # الأسئلة الشائعة
            ## مواعيد العمل
            مواعيدنا يومياً من 10 صباحاً إلى 10 مساءً.
            ---
            الجمعة إجازة رسمية.
            ***
            السبت من 1 ظهراً.
        """.trimIndent()

        val chunks = chunker.chunk(markdown)

        assertEquals(1, chunks.size)
        val chunk = chunks[0]
        assertFalse(chunk.chunkText.contains("---"))
        assertFalse(chunk.chunkText.contains("***"))
        assertTrue(chunk.chunkText.contains("مواعيدنا يومياً"))
        assertTrue(chunk.chunkText.contains("الجمعة إجازة رسمية"))
        assertTrue(chunk.chunkText.contains("السبت من 1 ظهراً"))
    }

    @Test
    fun `chunking does not duplicate header if text already starts with header`() {
        val markdown = """
            # بيانات المنتج
            بيانات المنتج: حذاء جلدي أصلي صناعة يدوية.
        """.trimIndent()

        val chunks = chunker.chunk(markdown)

        assertEquals(1, chunks.size)
        val chunk = chunks[0]
        assertEquals("بيانات المنتج", chunk.headerContext)
        // Verify it doesn't do "بيانات المنتج: بيانات المنتج: ..."
        assertFalse(chunk.chunkText.startsWith("بيانات المنتج: بيانات المنتج"))
        assertTrue(chunk.chunkText.startsWith("بيانات المنتج:"))
    }
}
