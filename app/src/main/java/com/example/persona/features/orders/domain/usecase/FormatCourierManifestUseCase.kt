package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import javax.inject.Inject

/**
 * Formats order information into a clean Egyptian commercial courier manifest
 * ready for 1-tap copy to delivery services (Bosta, Aramex, WhatsApp courier chats).
 * Terminal aesthetic: plain typography only — no emojis, no casual decoration.
 */
class FormatCourierManifestUseCase @Inject constructor() {

    operator fun invoke(order: OrderEntity): String = buildString {
        appendLine("بيانات الشحن والتوصيل")
        appendLine("━━━━━━━━━━━━━━━━")
        appendLine("اسم العميل: ${order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName}")
        appendLine("الهاتف: ${order.contactName}")
        appendLine("العنوان: ${order.deliveryAddress?.takeIf { it.isNotBlank() } ?: "غير محدد"}")
        appendLine("تفاصيل الطلب: ${order.orderDetails?.takeIf { it.isNotBlank() } ?: "غير محدد"}")
        val orderNotes = order.customNotes?.takeIf { it.isNotBlank() }
        if (orderNotes != null) {
            appendLine("ملاحظات العميل: $orderNotes")
        }
        val prepNotes = order.merchantNote?.takeIf { it.isNotBlank() }
        if (prepNotes != null) {
            appendLine("ملاحظة التجهيز: $prepNotes")
        }
        append("الحالة: ${courierStatusLabel(order.status)}")
    }

    fun courierStatusLabel(status: String): String = when (OrderStatus.fromString(status)) {
        OrderStatus.PENDING -> "طلب جديد"
        OrderStatus.IN_PREPARATION -> "قيد التجهيز"
        OrderStatus.SHIPPING_IN_PROGRESS -> "جاري التوصيل"
        OrderStatus.SHIPPED -> "تم الشحن / التسليم"
        OrderStatus.CANCELLED -> "ملغي"
        OrderStatus.RETURNED -> "مرتجع"
    }
}
