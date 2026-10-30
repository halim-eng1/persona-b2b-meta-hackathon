package com.example.persona.features.orders.domain.repository

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the merchant Orders Dashboard & Fulfillment.
 *
 * Wraps [com.example.persona.core.database.dao.OrderDao] behind a domain-owned
 * interface so the presentation layer never depends on Room types directly.
 */
interface OrderRepository {

    /**
     * Reactive observable of all orders across all contacts, ordered by most
     * recent first. Re-emits automatically after status / note updates.
     */
    fun getAllOrders(): Flow<List<OrderEntity>>

    /**
     * Manually creates a new order in Room (e.g. from phone calls, direct store sales).
     */
    suspend fun createOrder(order: OrderEntity): Long

    /**
     * Updates all editable fields of an existing order.
     */
    suspend fun updateOrder(
        orderId: Long,
        name: String?,
        phone: String,
        address: String?,
        details: String?,
        total: Double,
        shipping: Double,
        governorate: String?
    )

    /** Persists a lifecycle transition for [orderId]. */
    suspend fun updateOrderStatus(orderId: Long, status: OrderStatus)

    /** Cancels an order with a mandatory cancellation reason. */
    suspend fun cancelOrder(orderId: Long, reason: String)

    /**
     * Persists the merchant fulfillment note for [orderId]. The note is injected
     * into the LLM prompt's PERSONAL USER STATE block so the bot relays it to
     * the customer on the next reply. Passing `null` (or a blank string)
     * clears/removes the note so a stale instruction stops being relayed.
     */
    suspend fun updateMerchantNote(orderId: Long, note: String?)

    /**
     * Updates the status of multiple orders in bulk.
     */
    suspend fun updateOrderStatusBulk(orderIds: List<Long>, status: OrderStatus)

    /** Deletes an order row. */
    suspend fun deleteOrder(orderId: Long)
}
