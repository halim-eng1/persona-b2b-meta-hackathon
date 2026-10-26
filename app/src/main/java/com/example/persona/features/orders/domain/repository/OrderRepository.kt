package com.example.persona.features.orders.domain.repository

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the merchant Orders Dashboard.
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

    /** Persists a lifecycle transition for [orderId]. */
    suspend fun updateOrderStatus(orderId: Long, status: OrderStatus)

    /**
     * Persists the merchant fulfillment note for [orderId]. The note is injected
     * into the LLM prompt's PERSONAL USER STATE block so the bot relays it to
     * the customer on the next reply. Passing `null` (or a blank string)
     * clears/removes the note so a stale instruction stops being relayed.
     */
    suspend fun updateMerchantNote(orderId: Long, note: String?)
}
