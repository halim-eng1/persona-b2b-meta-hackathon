package com.example.persona.ui

import androidx.compose.ui.graphics.Color
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.ui.components.containsArabic
import com.example.persona.ui.components.extractMonogram
import com.example.persona.ui.theme.darkStatusColors
import com.example.persona.ui.theme.lightStatusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DesignSystemTest {

    @Test
    fun testContainsArabicDetection() {
        // Pure Arabic
        assertTrue(containsArabic("لوحة التحكم"))
        assertTrue(containsArabic("المحادثات"))
        assertTrue(containsArabic("قاعدة المعرفة"))
        assertTrue(containsArabic("عبد الله"))

        // Mixed Arabic & English
        assertTrue(containsArabic("مؤشرات التشغيل اليومية (DAILY B2B METRICS)"))
        assertTrue(containsArabic("الطلبات (ORDERS)"))

        // Pure Latin / English
        assertFalse(containsArabic("SETTINGS"))
        assertFalse(containsArabic("Audit Log"))
        assertFalse(containsArabic("Persona B2B"))
        assertFalse(containsArabic("12345"))
        assertFalse(containsArabic(""))
    }

    @Test
    fun testExtractMonogramLatinNames() {
        assertEquals("AH", extractMonogram("Ahmed Hassan"))
        assertEquals("JD", extractMonogram("John Doe"))
        assertEquals("AB", extractMonogram("Alice Bob Charlie"))
        assertEquals("ST", extractMonogram("Store"))
        assertEquals("O", extractMonogram("Omar", letterCount = 1))
    }

    @Test
    fun testExtractMonogramCompoundArabicNames() {
        // "عبد الله" -> compound prefix "عبد" + "الله" -> "عل"
        assertEquals("عل", extractMonogram("عبد الله"))
        // "عبد الرحمن" -> compound prefix "عبد" + "الرحمن" (core "رحمن") -> "عر"
        assertEquals("عر", extractMonogram("عبد الرحمن"))
        // "أبو بكر" -> "أب"
        assertEquals("أب", extractMonogram("أبو بكر"))
        // "أم مريم" -> "أم"
        assertEquals("أم", extractMonogram("أم مريم"))
    }

    @Test
    fun testExtractMonogramArabicDefiniteArticle() {
        // "المهندس حازم" -> strips "ال" from "المهندس" -> core "مهندس" -> 'م'
        assertEquals("م", extractMonogram("المهندس حازم", letterCount = 1))
        assertEquals("مح", extractMonogram("المهندس حازم", letterCount = 2))
        assertEquals("ش", extractMonogram("الشرقاوي", letterCount = 1))
    }

    @Test
    fun testExtractMonogramStandardArabicNames() {
        assertEquals("سع", extractMonogram("سارة علي"))
        assertEquals("مح", extractMonogram("محمد حسن"))
        assertEquals("مح", extractMonogram("محمود"))
        assertEquals("م", extractMonogram("محمود", letterCount = 1))
    }

    @Test
    fun testExtractMonogramEdgeCases() {
        assertEquals("?", extractMonogram(""))
        assertEquals("?", extractMonogram("   "))
        assertEquals("OT", extractMonogram("   Omar   Tarek   "))
    }

    @Test
    fun testLightStatusColorsWcagContrastCompliance() {
        val colors = lightStatusColors()

        // 1. Pending (Amber 700 on Amber 50 container)
        val pendingContrast = calculateContrastRatio(colors.pending, colors.pendingContainer)
        assertTrue(
            "Pending contrast $pendingContrast must be >= 4.5:1",
            pendingContrast >= 4.5
        )

        // 2. InPreparation (Blue 700 on Blue 50 container)
        val prepContrast = calculateContrastRatio(colors.inPreparation, colors.inPreparationContainer)
        assertTrue(
            "InPreparation contrast $prepContrast must be >= 4.5:1",
            prepContrast >= 4.5
        )

        // 3. Shipping (Indigo 700 on Indigo 50 container)
        val shippingContrast = calculateContrastRatio(colors.shipping, colors.shippingContainer)
        assertTrue(
            "Shipping contrast $shippingContrast must be >= 4.5:1",
            shippingContrast >= 4.5
        )

        // 4. Completed (Emerald 700 on Emerald 50 container)
        val completedContrast = calculateContrastRatio(colors.completed, colors.completedContainer)
        assertTrue(
            "Completed contrast $completedContrast must be >= 4.5:1",
            completedContrast >= 4.5
        )

        // 5. Cancelled (Red 700 on Red 50 container)
        val cancelledContrast = calculateContrastRatio(colors.cancelled, colors.cancelledContainer)
        assertTrue(
            "Cancelled contrast $cancelledContrast must be >= 4.5:1",
            cancelledContrast >= 4.5
        )

        // 6. Returned (Amber 900 on Amber container)
        val returnedContrast = calculateContrastRatio(colors.returned, colors.returnedContainer)
        assertTrue(
            "Returned contrast $returnedContrast must be >= 4.5:1",
            returnedContrast >= 4.5
        )
    }

    @Test
    fun testDarkStatusColorsInitialization() {
        val colors = darkStatusColors()
        assertNotNull(colors.pending)
        assertNotNull(colors.inPreparation)
        assertNotNull(colors.shipping)
        assertNotNull(colors.completed)
        assertNotNull(colors.cancelled)
        assertNotNull(colors.returned)
    }

    @Test
    fun testOrderStatusStringParsing() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString("PENDING"))
        assertEquals(OrderStatus.IN_PREPARATION, OrderStatus.fromString("IN_PREPARATION"))
        assertEquals(OrderStatus.SHIPPING_IN_PROGRESS, OrderStatus.fromString("SHIPPING_IN_PROGRESS"))
        assertEquals(OrderStatus.SHIPPED, OrderStatus.fromString("SHIPPED"))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromString("CANCELLED"))
        assertEquals(OrderStatus.RETURNED, OrderStatus.fromString("RETURNED"))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString("UNKNOWN_STATUS"))
    }

    private fun assertNotNull(obj: Any?) {
        assertTrue(obj != null)
    }

    /**
     * W3C WCAG 2.1 relative luminance and contrast ratio formula
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val lum1 = calculateRelativeLuminance(foreground)
        val lum2 = calculateRelativeLuminance(background)
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun calculateRelativeLuminance(color: Color): Double {
        fun sRgbToLinear(c: Float): Double {
            return if (c <= 0.04045f) (c / 12.92) else ((c + 0.055) / 1.055).toDouble().pow(2.4)
        }
        val r = sRgbToLinear(color.red)
        val g = sRgbToLinear(color.green)
        val b = sRgbToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
