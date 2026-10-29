package com.example.persona.features.auto_reply.domain.orchestrator

import android.util.Log
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OrderIngestionResult {
    data class Created(val orderId: Long) : OrderIngestionResult
    data class Merged(val orderId: Long) : OrderIngestionResult
    data class DuplicatePivot(val pivotReplyText: String) : OrderIngestionResult
    data object Ignored : OrderIngestionResult
}

/**
 * Manages order creation, cancellation, status updates, deduplication verification pivots,
 * and notes appending.
 */
@Singleton
class OrderIngestionManager @Inject constructor(
    private val orderDao: OrderDao,
    private val chatHistoryDao: ChatHistoryDao
) {
    companion object {
        private const val TAG = "OrderIngestionManager"

        const val PIVOT_MARKER = "أنا شايف إنك لسه عامل أوردر مشابه"

        const val NO_ACTIVE_ORDER_REPLY =
            "يا فندم، ما لقيناش أوردر نشط باسم حضرتك في النظام حالياً، لو في أوردر حضرتك شايفه ينتظر إلغاء، ابعتلنا تفاصيله وهنراجع حالاً."

        fun normalizeField(value: String?): String? {
            return value?.trim()?.replace(Regex("\\s+"), " ")
        }

        fun isPivotStillOpen(
            latestPivotTimestamp: Long?,
            consumedPivotTimestamps: Set<Long>,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (latestPivotTimestamp == null) return false
            val isExpired = latestPivotTimestamp > 100_000_000L && (nowMs - latestPivotTimestamp > 3600_000L)
            return !isExpired && !consumedPivotTimestamps.contains(latestPivotTimestamp)
        }

        fun filterDuplicateNoteLines(
            existingNotes: String?,
            newNotes: String,
            existingDeliveryAddress: String? = null,
            existingCustomerName: String? = null
        ): String {
            if (newNotes.isBlank()) return ""
            val existingLines = existingNotes
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()

            existingDeliveryAddress?.trim()?.takeIf { it.isNotEmpty() }?.let {
                existingLines.add("العنوان: $it")
            }
            existingCustomerName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                existingLines.add("الاسم: $it")
            }

            return newNotes.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !existingLines.contains(it) }
                .joinToString("\n")
                .trim()
        }

        fun buildNotesFromExtractedOrder(extracted: ExtractedOrder?): String {
            if (extracted == null) return ""
            val normalizedCustomerName = normalizeField(extracted.customerName)?.removePrefix("الاسم:")?.trim()
            val normalizedDeliveryAddress = normalizeField(extracted.deliveryAddress)?.removePrefix("العنوان:")?.trim()
            val normalizedOrderDetails = normalizeField(extracted.orderDetails)?.removePrefix("التفاصيل:")?.trim()
            val normalizedCustomNotes = normalizeField(extracted.customNotes)?.removePrefix("ملاحظات:")?.trim()

            return buildString {
                normalizedCustomerName?.takeIf { it.isNotEmpty() }?.let { append("الاسم: $it\n") }
                normalizedDeliveryAddress?.takeIf { it.isNotEmpty() }?.let { append("العنوان: $it\n") }
                normalizedOrderDetails?.takeIf { it.isNotEmpty() }?.let { append("التفاصيل: $it\n") }
                normalizedCustomNotes?.takeIf { it.isNotEmpty() }?.let { append("ملاحظات: $it\n") }
            }.trim()
        }
    }

    private val consumedPivotTimestamps = ConcurrentHashMap<String, MutableSet<Long>>()

    suspend fun executeCancelOrderAction(processed: ProcessedNotification): Boolean {
        val activeOrder = orderDao.getActiveMatchingOrder(processed.contactName)
        if (activeOrder == null) {
            Log.i(TAG, "order_action=CANCEL but no active order for ${processed.contactName} — no state mutation")
            return false
        }

        orderDao.updateStatus(activeOrder.id, OrderStatus.CANCELLED.name)
        Log.i(TAG, "🚫 Agentic CANCEL: order id=${activeOrder.id} (${activeOrder.status} → ${OrderStatus.CANCELLED.name}) for ${processed.contactName}")
        return true
    }

    suspend fun applyOrderUpdates(existingOrder: OrderEntity, extracted: ExtractedOrder?) {
        if (extracted == null) return

        val normalizedCustomerName = normalizeField(extracted.customerName)?.removePrefix("الاسم:")?.trim()
        if (!normalizedCustomerName.isNullOrBlank() && normalizedCustomerName != existingOrder.customerNameFromLlm) {
            orderDao.updateCustomerName(existingOrder.id, normalizedCustomerName)
            Log.i(TAG, "Updated customer name for order ${existingOrder.id}: $normalizedCustomerName")
        }

        val normalizedDeliveryAddress = normalizeField(extracted.deliveryAddress)?.removePrefix("العنوان:")?.trim()
        if (!normalizedDeliveryAddress.isNullOrBlank() && normalizedDeliveryAddress != existingOrder.deliveryAddress) {
            orderDao.updateDeliveryAddress(existingOrder.id, normalizedDeliveryAddress)
            Log.i(TAG, "Updated delivery address for order ${existingOrder.id}: $normalizedDeliveryAddress")
        }

        val normalizedOrderDetails = normalizeField(extracted.orderDetails)?.removePrefix("التفاصيل:")?.trim()
        if (!normalizedOrderDetails.isNullOrBlank() && normalizedOrderDetails != existingOrder.orderDetails) {
            orderDao.updateOrderDetails(existingOrder.id, normalizedOrderDetails)
            Log.i(TAG, "Updated order details for order ${existingOrder.id}: $normalizedOrderDetails")
        }

        val rawNewNotes = buildNotesFromExtractedOrder(extracted)
        val deduplicatedNotes = filterDuplicateNoteLines(
            existingNotes = existingOrder.customNotes,
            newNotes = rawNewNotes,
            existingDeliveryAddress = normalizedDeliveryAddress ?: existingOrder.deliveryAddress,
            existingCustomerName = normalizedCustomerName ?: existingOrder.customerNameFromLlm
        )

        if (deduplicatedNotes.isNotEmpty()) {
            orderDao.appendCustomNotes(existingOrder.id, deduplicatedNotes)
            Log.i(TAG, "🔁 Deduplicated notes appended to order id=${existingOrder.id}: '$deduplicatedNotes'")
        }
    }

    suspend fun handleOrderCreation(
        extractedOrder: ExtractedOrder?,
        contactName: String,
        customerText: String = "",
        orderAction: String? = "",
        sourceClass: String = "BUSINESS"
    ): OrderIngestionResult {
        if (extractedOrder == null) return OrderIngestionResult.Ignored

        val normalizedCustomerName = normalizeField(extractedOrder.customerName)
        val normalizedDeliveryAddress = normalizeField(extractedOrder.deliveryAddress)
        val normalizedOrderDetails = normalizeField(extractedOrder.orderDetails)
        val normalizedCustomNotes = normalizeField(extractedOrder.customNotes)

        val existingOrder = orderDao.getActiveMatchingOrder(contactName)
        return if (existingOrder != null) {
            val recentChatTurns = chatHistoryDao.getRecentMessagesForSource(contactName, sourceClass, limit = 6)
            val latestPivotMsg = recentChatTurns.firstOrNull { !it.isFromUser && it.messageText.contains(PIVOT_MARKER) }

            val pivotSeen = isPivotStillOpen(
                latestPivotTimestamp = latestPivotMsg?.timestamp,
                consumedPivotTimestamps = consumedPivotTimestamps[contactName] ?: emptySet()
            )

            if (pivotSeen && latestPivotMsg != null) {
                val lowerCustomerText = customerText.lowercase()
                val isNewOrder = lowerCustomerText.contains("جديد") ||
                        lowerCustomerText.contains("منفصل") ||
                        lowerCustomerText.contains("اوردر جديد") ||
                        lowerCustomerText.contains("أوردر جديد") ||
                        lowerCustomerText.contains("طلب تاني") ||
                        lowerCustomerText.contains("new") ||
                        orderAction?.equals("CREATE", ignoreCase = true) == true

                if (isNewOrder) {
                    val newOrder = OrderEntity(
                        contactName = contactName,
                        customerNameFromLlm = normalizedCustomerName,
                        deliveryAddress = normalizedDeliveryAddress,
                        orderDetails = normalizedOrderDetails,
                        customNotes = normalizedCustomNotes,
                        status = OrderStatus.PENDING.name
                    )
                    val insertedOrderId = orderDao.insert(newOrder)
                    PersonaLogger.i(PersonaLogTags.ORCHESTRATOR, "Customer chose NEW separate order following pivot. Inserted new order id=$insertedOrderId")
                    consumedPivotTimestamps
                        .computeIfAbsent(contactName) { ConcurrentHashMap.newKeySet() }
                        .add(latestPivotMsg.timestamp)
                    OrderIngestionResult.Created(insertedOrderId)
                } else {
                    applyOrderUpdates(existingOrder, extractedOrder)
                    consumedPivotTimestamps
                        .computeIfAbsent(contactName) { ConcurrentHashMap.newKeySet() }
                        .add(latestPivotMsg.timestamp)
                    OrderIngestionResult.Merged(existingOrder.id)
                }
            } else {
                Log.i(TAG, "🔄 Duplicate order detected for $contactName. Sending verification pivot.")
                val pivotText = "أنا شايف إنك لسه عامل أوردر مشابه من شوية، تحب نعدل على الأوردر القديم ونضيف عليه التفاصيل الجديدة دي، ولا ده أوردر جديد خالص يا فندم؟"
                OrderIngestionResult.DuplicatePivot(pivotText)
            }
        } else {
            val newOrder = OrderEntity(
                contactName = contactName,
                customerNameFromLlm = normalizedCustomerName,
                deliveryAddress = normalizedDeliveryAddress,
                orderDetails = normalizedOrderDetails,
                customNotes = normalizedCustomNotes,
                status = OrderStatus.PENDING.name
            )
            val insertedOrderId = orderDao.insert(newOrder)
            Log.i(TAG, "📦 New order ingested for $contactName: orderId=$insertedOrderId")
            OrderIngestionResult.Created(insertedOrderId)
        }
    }

    suspend fun handleFinalPassOrderActions(
        finalParsed: AgenticLlmResponse,
        processed: ProcessedNotification
    ): String? {
        var finalReplyOverride: String? = null
        val finalOrderAction = finalParsed.orderAction?.trim()?.uppercase()
            ?.takeIf { it == "CREATE" || it == "CANCEL" || it == "UPDATE" }

        Log.i("PersonaMetrics", "metric=final_pass_order_action value=${finalOrderAction ?: "NONE"} contact=${processed.contactName.hashCode()}")

        if (finalParsed.extractedOrder != null) {
            val extracted = finalParsed.extractedOrder
            val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)

            when {
                finalOrderAction == "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
                        finalReplyOverride = NO_ACTIVE_ORDER_REPLY
                    }
                }
                finalOrderAction == "UPDATE" && existingOrder != null -> {
                    applyOrderUpdates(existingOrder, extracted)
                    if (existingOrder.status == OrderStatus.PENDING.name) {
                        orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                        Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION (final-pass UPDATE)")
                    }
                }
                else -> {
                    val result = handleOrderCreation(
                        extractedOrder = finalParsed.extractedOrder,
                        contactName = processed.contactName,
                        customerText = processed.originalText,
                        orderAction = finalOrderAction ?: "",
                        sourceClass = processed.sourceClass.name
                    )
                    if (result is OrderIngestionResult.DuplicatePivot) {
                        finalReplyOverride = result.pivotReplyText
                    }
                }
            }
        } else {
            when (finalOrderAction) {
                "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
                        finalReplyOverride = NO_ACTIVE_ORDER_REPLY
                    }
                }
                "UPDATE" -> {
                    val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)
                    if (existingOrder == null) {
                        Log.i(TAG, "final pass order_action=UPDATE but no active order for ${processed.contactName}")
                    } else if (existingOrder.status == OrderStatus.PENDING.name) {
                        orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                        Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION (final-pass confirmation)")
                    }
                }
                else -> Unit
            }
        }
        return finalReplyOverride
    }
}
