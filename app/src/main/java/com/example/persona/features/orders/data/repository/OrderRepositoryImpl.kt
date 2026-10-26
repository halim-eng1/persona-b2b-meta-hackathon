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
 * Write operations simply mutate the underlying table — the reactive
 * `observeAllOrders` Flow owned by Room re-emits, so the dashboard UI updates
 * itself without any manual refresh signals.
 */
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao
) : OrderRepository {

    override fun getAllOrders(): Flow<List<OrderEntity>> =
        orderDao.observeAllOrders()

    override suspend fun updateOrderStatus(orderId: Long, status: OrderStatus) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: updating order#$orderId status=$status"
        )
        orderDao.updateStatus(orderId, status.name)
    }

    override suspend fun updateMerchantNote(orderId: Long, note: String?) {
        PersonaLogger.d(
            PersonaLogTags.DATABASE,
            "Orders: saving merchant note for order#$orderId (len=${note?.length ?: 0}, blank=clear)"
        )
        orderDao.updateMerchantNote(orderId, note)
    }
}
