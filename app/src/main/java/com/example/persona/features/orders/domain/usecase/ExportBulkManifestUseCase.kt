package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.orders.domain.CurrencyFormatter
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Exports multiple orders into a structured courier dispatch manifest
 * suitable for sharing with delivery companies (Bosta, Aramex, couriers)
 * via WhatsApp or printing.
 */
class ExportBulkManifestUseCase @Inject constructor(
    private val formatCourierManifestUseCase: FormatCourierManifestUseCase
) {
    operator fun invoke(orders: List<OrderEntity>, currency: String = "ج.م"): String = buildString {
        if (orders.isEmpty()) {
            append("لا توجد طلبات محددة للتصدير.")
            return@buildString
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dateFormat.format(Date())

        appendLine("📋 كشف تسليم الشحنات المجمع — تاريخ: $today")
        appendLine("إجمالي عدد الطرود: ${orders.size}")
        val totalCod = orders.sumOf { it.totalAmount + it.shippingFee }
        val formattedCod = EgyptianOrderUtils.formatMoney(totalCod)
        appendLine("إجمالي مبالغ التحصيل المطلوبة عند الاستلام: $formattedCod $currency")
        appendLine("════════════════════════════════")
        appendLine()

        orders.forEachIndexed { index, order ->
            appendLine(" طرد رقم (${index + 1}/${orders.size}):")
            appendLine(formatCourierManifestUseCase(order, currency))
            appendLine("────────────────────────────────")
        }

        appendLine()
        append("نهاية كشف التسليم — توقيع المستلم / المندوب: _______________")
    }
}
