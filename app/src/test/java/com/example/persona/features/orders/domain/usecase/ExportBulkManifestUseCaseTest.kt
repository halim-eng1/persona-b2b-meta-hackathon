package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBulkManifestUseCaseTest {

    private val formatCourierManifestUseCase = FormatCourierManifestUseCase()
    private val useCase = ExportBulkManifestUseCase(formatCourierManifestUseCase)

    @Test
    fun `formats empty orders list with friendly notice`() {
        val result = useCase(emptyList())
        assertTrue(result.contains("لا توجد طلبات محددة للتصدير"))
    }

    @Test
    fun `formats multiple orders with summary and total COD`() {
        val order1 = OrderEntity(
            id = 101L,
            contactName = "01011112222",
            customerNameFromLlm = "أحمد فؤاد",
            totalAmount = 200.0,
            shippingFee = 50.0,
            status = OrderStatus.PENDING.name
        )
        val order2 = OrderEntity(
            id = 102L,
            contactName = "01122223333",
            customerNameFromLlm = "منى محمود",
            totalAmount = 300.0,
            shippingFee = 50.0,
            status = OrderStatus.IN_PREPARATION.name
        )

        val result = useCase(listOf(order1, order2))

        assertTrue(result.contains("كشف تسليم الشحنات المجمع"))
        assertTrue(result.contains("إجمالي عدد الطرود: 2"))
        assertTrue(result.contains("إجمالي مبالغ التحصيل المطلوبة (COD): 600 ج.م"))
        assertTrue(result.contains("طرد رقم (1/2)"))
        assertTrue(result.contains("أحمد فؤاد"))
        assertTrue(result.contains("طرد رقم (2/2)"))
        assertTrue(result.contains("منى محمود"))
        assertTrue(result.contains("توقيع المستلم / المندوب"))
    }
}
