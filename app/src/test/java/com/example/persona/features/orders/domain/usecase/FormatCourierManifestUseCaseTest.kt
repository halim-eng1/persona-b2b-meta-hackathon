package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCourierManifestUseCaseTest {

    private val useCase = FormatCourierManifestUseCase()

    @Test
    fun `formats manifest with all fields present`() {
        val order = OrderEntity(
            id = 1L,
            contactName = "01012345678",
            customerNameFromLlm = "محمد علي",
            deliveryAddress = "القاهرة، المعادي",
            orderDetails = "2x قميص قطن أبيض",
            customNotes = "الدور الثالث شقة 5",
            merchantNote = "تغليف هدايا",
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("بيانات الشحن والتوصيل"))
        assertTrue(manifest.contains("اسم العميل: محمد علي"))
        assertTrue(manifest.contains("الهاتف: 01012345678"))
        assertTrue(manifest.contains("العنوان: القاهرة، المعادي"))
        assertTrue(manifest.contains("تفاصيل الطلب: 2x قميص قطن أبيض"))
        assertTrue(manifest.contains("ملاحظات العميل: الدور الثالث شقة 5"))
        assertTrue(manifest.contains("ملاحظة التجهيز: تغليف هدايا"))
        assertTrue(manifest.contains("الحالة: طلب جديد"))
    }

    @Test
    fun `falls back to contactName when customerNameFromLlm is blank`() {
        val order = OrderEntity(
            id = 2L,
            contactName = "01234567890",
            customerNameFromLlm = "   ",
            deliveryAddress = null,
            orderDetails = null,
            status = OrderStatus.IN_PREPARATION.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("اسم العميل: 01234567890"))
        assertTrue(manifest.contains("العنوان: غير محدد"))
        assertTrue(manifest.contains("تفاصيل الطلب: غير محدد"))
        assertTrue(manifest.contains("الحالة: قيد التجهيز"))
    }
}
