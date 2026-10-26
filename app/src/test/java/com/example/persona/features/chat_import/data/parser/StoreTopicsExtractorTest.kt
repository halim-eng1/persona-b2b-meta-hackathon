package com.example.persona.features.chat_import.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTopicsExtractorTest {

    @Test
    fun `extracts only level-1 headings in document order`() {
        val document = """
            # تعريف النشاط التجاري
            # كتالوج المنتجات والأسعار الرسمية
            ## أولاً: تشكيلة الهوديز الشتوية
            ### هودي جرافيك مطبوع (Graphic Hoodies)
            # مواصفات الخامات والجودة
            ## خامة الهوديز
            #### هل الهودي مناسب للشتاء؟
            # سياسات الشحن والتوصيل
            ## نطاق القاهرة الكبرى
        """.trimIndent()

        val topics = StoreTopicsExtractor.extract(document)

        assertEquals(
            listOf(
                "تعريف النشاط التجاري",
                "كتالوج المنتجات والأسعار الرسمية",
                "مواصفات الخامات والجودة",
                "سياسات الشحن والتوصيل"
            ),
            topics
        )
    }

    @Test
    fun `trims indentation and deduplicates repeated headings`() {
        val document = """
            مقدمة غير مسنادة بلا هيدلاين

              # تعريف النشاط التجاري

            نص المحتوى...

            # تعريف النشاط التجاري
            # #tag within heading
            #Hashtag no-space
            ## فرعي مش بيتحسب
        """.trimIndent()

        val topics = StoreTopicsExtractor.extract(document)

        // Indented "  # ..." is trimmed into a valid Level-1 heading; duplicates removable;
        // "##" and "#X" tokens (no "# " prefix) are dropped; "# #tag..." keeps its hash content.
        assertEquals(listOf("تعريف النشاط التجاري", "#tag within heading"), topics)
    }

    @Test
    fun `returns empty list for blank or heading-free documents`() {
        assertTrue(StoreTopicsExtractor.extract("").isEmpty())
        assertTrue(StoreTopicsExtractor.extract("   \n\t\n  ").isEmpty())
        assertTrue(StoreTopicsExtractor.extract("نص عادي بدون هيدلاين\nسطر تاني").isEmpty())
        assertTrue(
            StoreTopicsExtractor.extract("## فرعي فقط\n### تفصيلي فقط\n#### سؤال فقط").isEmpty()
        )
    }
}
