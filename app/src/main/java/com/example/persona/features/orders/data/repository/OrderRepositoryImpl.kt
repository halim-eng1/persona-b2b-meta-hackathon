package com.example.persona.features.orders.data.repository

import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.features.orders.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Single-source-of-truth implementation of [OrderRepository] backed by Room.
 *
 * Write operations mutate the underlying table — the reactive
 * `observeAllOrders` Flow owned by Room re-emits, so the dashboard UI updates
 * itself without any manual refresh signals.
 */
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao
) : OrderRepository {

    override fun getAllOrders(): Flow<List<OrderEntity>> =
        orderDao.observeAllOrders()

    override suspend fun createOrder(order: OrderEntity): Long {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: creating manual order contact=${order.contactName} amount=${order.totalAmount}"
        )
        return orderDao.insert(order)
    }

    override suspend fun updateOrder(
        orderId: Long,
        name: String?,
        phone: String,
        address: String?,
        details: String?,
        total: Double,
        shipping: Double,
        governorate: String?
    ) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: updating full details for order#$orderId"
        )
        orderDao.updateFullOrderDetails(
            id = orderId,
            name = name,
            phone = phone,
            address = address,
            details = details,
            total = total,
            shipping = shipping,
            gov = governorate
        )
    }

    override suspend fun updateOrderStatus(orderId: Long, status: OrderStatus) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: updating order#$orderId status=$status"
        )
        orderDao.updateStatus(orderId, status.name)
    }

    override suspend fun cancelOrder(orderId: Long, reason: String) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: cancelling order#$orderId reason=$reason"
        )
        orderDao.updateStatusWithReason(orderId, OrderStatus.CANCELLED.name, reason)
    }

    override suspend fun updateMerchantNote(orderId: Long, note: String?) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: saving merchant note for order#$orderId (len=${note?.length ?: 0}, blank=clear)"
        )
        orderDao.updateMerchantNote(orderId, note)
    }

    override suspend fun updateOrderStatusBulk(orderIds: List<Long>, status: OrderStatus) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: bulk updating ${orderIds.size} orders to status=$status"
        )
        if (orderIds.isNotEmpty()) {
            orderDao.updateStatusBulk(orderIds, status.name)
        }
    }

    override suspend fun deleteOrder(orderId: Long) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: deleting order#$orderId"
        )
        orderDao.deleteOrder(orderId)
    }
}
