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
            - المتجر الإلكتروني الرسمي: https://velo.ordersapps.com/ar
            - صفحة الفيسبوك الرسمية: https://www.facebook.com/velo.eg
            - حساب التيك توك الرسمي: https://www.tiktok.com/@velo.egypt
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertEquals("https://velo.ordersapps.com/ar", website)
    }

    @Test
    fun `deduplicates and strips product sub-paths to base storefront`() {
        val markdown = """
            # كتالوج المنتجات
            - رابط المنتج: https://velo.ordersapps.com/ar/product/rebecca-mug
            - منتج آخر: https://velo.ordersapps.com/ar/products/hoodie-basic
            - صفحة الفيسبوك: https://www.facebook.com/velo.eg
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertEquals("https://velo.ordersapps.com/ar", website)
    }

    @Test
    fun `excludes social media URLs and returns null when no store website exists`() {
        val markdown = """
            # قنوات التواصل
            - صفحة الفيسبوك الرسمية: https://www.facebook.com/velo.eg
            - حساب التيك توك الرسمي: https://www.tiktok.com/@velo.egypt
            - شات الواتساب: https://wa.me/201012345678
            - انستجرام: https://instagram.com/velo.eg
        """.trimIndent()

        val website = StoreWebsiteExtractor.extract(markdown)
        assertNull(website)
    }

    @Test
    fun `returns null on empty or blank document`() {
        assertNull(StoreWebsiteExtractor.extract(""))
        assertNull(StoreWebsiteExtractor.extract("   \n\t  "))
    }
}
