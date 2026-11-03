package com.example.persona.features.chat_import.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreWebsiteExtractorTest {

    @Test
    fun `extracts website from explicit store website label`() {
        val markdown = """
            # تعريف النشاط التجاري
            - رقم خدمة العملاء / واتساب: 01012345678.
            - المتجر الإلكتروني الرسمي: https://store.example.com
            - صفحة الفيسبوك الرسمية: https://www.facebook.com/novastyle.apparel
            - حساب التيك توك الرسمي: https://www.tiktok.com/@novastyle.apparel
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertEquals("https://store.example.com", website)
    }

    @Test
    fun `deduplicates and strips product sub-paths to base storefront`() {
        val markdown = """
            # كتالوج المنتجات
            - رابط المنتج: https://store.example.com/product/rebecca-mug
            - منتج آخر: https://store.example.com/products/hoodie-basic
            - صفحة الفيسبوك: https://www.facebook.com/novastyle.apparel
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertEquals("https://store.example.com", website)
    }

    @Test
    fun `excludes social media URLs and returns null when no store website exists`() {
        val markdown = """
            # قنوات التواصل
            - صفحة الفيسبوك الرسمية: https://www.facebook.com/novastyle.apparel
            - حساب التيك توك الرسمي: https://www.tiktok.com/@novastyle.apparel
            - شات الواتساب: https://wa.me/201012345678
            - انستجرام: https://instagram.com/novastyle.apparel
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertNull(website)
    }

    @Test
    fun `extracts website from English explicit store website labels`() {
        val markdown = """
            # Store Information
            - Support: +1 555-0199
            - Official Website: https://store.example.com
            - Instagram: https://instagram.com/novastyle
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertEquals("https://store.example.com", website)
    }

    @Test
    fun `returns null on empty or blank document`() {
        assertNull(StoreWebsiteExtractor.extract(""))
        assertNull(StoreWebsiteExtractor.extract("   \n\t  "))
    }
}
