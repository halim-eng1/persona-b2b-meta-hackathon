package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import javax.inject.Inject

/**
 * Formats order information into a clean Egyptian commercial courier manifest
 * ready for 1-tap copy to delivery services (Bosta, Aramex, WhatsApp courier chats).
 * Plain typography only — structured, professional, and free of placeholders.
 */
class FormatCourierManifestUseCase @Inject constructor() {

    operator fun invoke(order: OrderEntity): String = buildString {
        appendLine("بيانات الشحن والتوصيل")
        appendLine("━━━━━━━━━━━━━━━━")
        appendLine("اسم العميل: ${order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName}")
        appendLine("الهاتف: ${resolvePhoneNumber(order)}")
        val govText = order.governorate?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        appendLine("العنوان: ${order.deliveryAddress?.takeIf { it.isNotBlank() } ?: "غير محدد"}$govText")
        appendLine("تفاصيل الطلب: ${order.orderDetails?.takeIf { it.isNotBlank() } ?: "غير محدد"}")

        val codBalance = order.totalAmount + order.shippingFee
        if (codBalance > 0.0) {
            appendLine("المطلوب تحصيله عند الاستلام: ${EgyptianOrderUtils.formatMoney(codBalance)} ج.م (شامل الشحن)")
        } else {
            appendLine("المطلوب تحصيله عند الاستلام: مدفوع مسبقاً / 0.00 ج.م")
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
        // 1. Prioritize customerPhone if it contains a valid Egyptian mobile
        val phoneFromCustomer = EgyptianOrderUtils.extractEgyptianMobilePhone(order.customerPhone)
        if (phoneFromCustomer != null) {
            return phoneFromCustomer
        }

        // 2. Extract from customNotes
        val phoneInNotes = EgyptianOrderUtils.extractEgyptianMobilePhone(order.customNotes)
        if (phoneInNotes != null) {
            return phoneInNotes
        }

        // 3. Extract from contactName if contactName contains a valid Egyptian mobile
        val phoneInContact = EgyptianOrderUtils.extractEgyptianMobilePhone(order.contactName)
        if (phoneInContact != null) {
            return phoneInContact
        }

        // 4. Extract from deliveryAddress
        val phoneInAddress = EgyptianOrderUtils.extractEgyptianMobilePhone(order.deliveryAddress)
        if (phoneInAddress != null) {
            return phoneInAddress
        }

        // 5. Extract from orderDetails
        val phoneInDetails = EgyptianOrderUtils.extractEgyptianMobilePhone(order.orderDetails)
        if (phoneInDetails != null) {
            return phoneInDetails
        }

        // 6. If customerPhone had raw digits that might be landline or foreign number (7-15 digits)
        val rawDigits = EgyptianOrderUtils.convertArabicDigitsToAscii(order.customerPhone).filter { it.isDigit() }
        if (rawDigits.length in 7..15) {
            return rawDigits
        }

        return "غير مسجل - يرجى مراجعة العميل"
    }
}
