package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCourierManifestUseCaseTest {

    private val useCase = FormatCourierManifestUseCase()

    @Test
    fun `formats manifest with all fields present including COD and governorate`() {
        val order = OrderEntity(
            id = 1L,
            contactName = "أحمد كوتشيات",
            customerNameFromLlm = "محمد علي",
            customerPhone = "01012345678",
            deliveryAddress = "المعادي، شارع 9",
            governorate = "القاهرة",
            orderDetails = "2x قميص قطن أبيض",
            totalAmount = 500.0,
            shippingFee = 50.0,
            customNotes = "الدور الثالث شقة 5",
            merchantNote = "تغليف هدايا",
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("بيانات الشحن والتوصيل"))
        assertTrue(manifest.contains("اسم العميل: محمد علي"))
        assertTrue(manifest.contains("الهاتف: 01012345678"))
        assertFalse(manifest.contains("الهاتف: أحمد كوتشيات"))
        assertTrue(manifest.contains("العنوان: المعادي، شارع 9 (القاهرة)"))
        assertTrue(manifest.contains("تفاصيل الطلب: 2x قميص قطن أبيض"))
        assertTrue(manifest.contains("المطلوب تحصيله عند الاستلام: 550 ج.م (شامل الشحن)"))
        assertTrue(manifest.contains("ملاحظات العميل: الدور الثالث شقة 5"))
        assertTrue(manifest.contains("ملاحظة التجهيز: تغليف هدايا"))
        assertTrue(manifest.contains("الحالة: طلب جديد"))
    }

    @Test
    fun `extracts phone from customNotes when customerPhone is blank`() {
        val order = OrderEntity(
            id = 2L,
            contactName = "دكتور مصطفى",
            customerPhone = "",
            customNotes = "رقم الاستلام 01155554444 كلموني قبل ما تيجوا",
            status = OrderStatus.IN_PREPARATION.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: 01155554444"))
        assertFalse(manifest.contains("الهاتف: دكتور مصطفى"))
    }

    @Test
    fun `extracts phone from customNotes when customerPhone is corrupt or invalid`() {
        val order = OrderEntity(
            id = 20L,
            contactName = "محل السلام",
            customerPhone = "غير محدد",
            customNotes = "موبايل العميل 01599998888",
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: 01599998888"))
        assertFalse(manifest.contains("الهاتف: غير محدد"))
        assertFalse(manifest.contains("الهاتف: محل السلام"))
    }

    @Test
    fun `normalizes international plus20 phone to 11 digits domestic format`() {
        val order = OrderEntity(
            id = 21L,
            contactName = "خالد",
            customerPhone = "+201012345678",
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: 01012345678"))
    }

    @Test
    fun `normalizes Eastern Arabic numerals phone to standard ASCII digits`() {
        val order = OrderEntity(
            id = 22L,
            contactName = "منى",
            customerPhone = "٠١٢٣٤٥٦٧٨٩٠",
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: 01234567890"))
    }

    @Test
    fun `falls back to valid phone in contactName when customerPhone is blank`() {
        val order = OrderEntity(
            id = 3L,
            contactName = "01234567890",
            customerPhone = "",
            status = OrderStatus.IN_PREPARATION.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: 01234567890"))
    }

    @Test
    fun `shows fallback message when no phone is available anywhere`() {
        val order = OrderEntity(
            id = 4L,
            contactName = "ميدو كولكشن",
            customerPhone = "",
            customNotes = "شقة 4",
            status = OrderStatus.IN_PREPARATION.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("الهاتف: غير مسجل - يرجى مراجعة العميل"))
        assertFalse(manifest.contains("الهاتف: ميدو كولكشن"))
    }

    @Test
    fun `formats prepaid COD when amount is zero`() {
        val order = OrderEntity(
            id = 5L,
            contactName = "01000000000",
            totalAmount = 0.0,
            shippingFee = 0.0,
            status = OrderStatus.SHIPPED.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("المطلوب تحصيله عند الاستلام: مدفوع مسبقاً / 0.00 ج.م"))
    }

    @Test
    fun `formats COD with decimals cleanly when amount has fractions`() {
        val order = OrderEntity(
            id = 50L,
            contactName = "01000000000",
            totalAmount = 299.50,
            shippingFee = 50.0,
            status = OrderStatus.PENDING.name
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("المطلوب تحصيله عند الاستلام: 349.50 ج.م (شامل الشحن)"))
    }

    @Test
    fun `includes cancellation reason for cancelled orders`() {
        val order = OrderEntity(
            id = 6L,
            contactName = "01000000000",
            status = OrderStatus.CANCELLED.name,
            cancellationReason = "العميل غير رأيه"
        )

        val manifest = useCase(order)

        assertTrue(manifest.contains("سبب الإلغاء: العميل غير رأيه"))
        assertTrue(manifest.contains("الحالة: ملغي"))
    }
}
