package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.CurrencyFormatter
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import javax.inject.Inject

/**
 * Formats order information into a clean Egyptian commercial courier manifest
 * ready for 1-tap copy to delivery services (Bosta, Aramex, WhatsApp courier chats).
 * Plain typography only — structured, professional, and free of placeholders.
 */
class FormatCourierManifestUseCase @Inject constructor() {

    operator fun invoke(order: OrderEntity, currency: String = "ج.م"): String = buildString {
        appendLine("بيانات الشحن والتوصيل")
        appendLine("━━━━━━━━━━━━━━━━")
        appendLine("اسم العميل: ${order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName}")
        appendLine("الهاتف: ${resolvePhoneNumber(order)}")
        val govText = order.governorate?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        appendLine("العنوان: ${order.deliveryAddress?.takeIf { it.isNotBlank() } ?: "غير محدد"}$govText")
        appendLine("تفاصيل الطلب: ${order.orderDetails?.takeIf { it.isNotBlank() } ?: "غير محدد"}")

        val codBalance = order.totalAmount + order.shippingFee
        if (codBalance > 0.0) {
            appendLine("المطلوب تحصيله عند الاستلام: ${EgyptianOrderUtils.formatMoney(codBalance)} $currency (شامل الشحن)")
        } else {
            appendLine("المطلوب تحصيله عند الاستلام: مدفوع مسبقاً / 0.00 $currency")
        }

        val orderNotes = order.customNotes?.takeIf { it.isNotBlank() }
        if (orderNotes != null) {
            appendLine("ملاحظات العميل: $orderNotes")
        }
        val prepNotes = order.merchantNote?.takeIf { it.isNotBlank() }
        if (prepNotes != null) {
            appendLine("ملاحظة التجهيز: $prepNotes")
        }
        order.cancellationReason?.takeIf { it.isNotBlank() }?.let {
            appendLine("سبب الإلغاء: $it")
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

    private fun resolvePhoneNumber(order: OrderEntity): String {
        val candidates = listOf(
            order.customerPhone,
            order.customNotes,
            order.contactName,
            order.deliveryAddress,
            order.orderDetails
        )

        // 1. Try Egyptian mobile extraction first across candidates
        for (candidate in candidates) {
            val egPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(candidate)
            if (egPhone != null) return egPhone
        }

        // 2. Fallback to international phone extraction preserving leading '+'
        for (candidate in candidates) {
            val intlPhone = EgyptianOrderUtils.extractPhoneNumber(candidate)
            if (intlPhone != null) return intlPhone
        }

        // 3. Fallback for raw foreign number
        val rawAscii = EgyptianOrderUtils.convertArabicDigitsToAscii(order.customerPhone).trim()
        val hasPlus = rawAscii.startsWith("+")
        val rawDigits = rawAscii.filter { it.isDigit() }
        if (rawDigits.length in 7..15) {
            return if (hasPlus) "+$rawDigits" else rawDigits
        }

        return "غير مسجل - يرجى مراجعة العميل"
    }
}
