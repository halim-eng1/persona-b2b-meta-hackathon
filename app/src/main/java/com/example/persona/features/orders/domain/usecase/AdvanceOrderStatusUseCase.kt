package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import javax.inject.Inject

/**
 * Encapsulates tactile Kanban lifecycle progression for orders:
 * - [OrderStatus.PENDING] -> [OrderStatus.IN_PREPARATION]
 * - [OrderStatus.IN_PREPARATION] -> [OrderStatus.SHIPPED]
 * - [OrderStatus.SHIPPING_IN_PROGRESS] -> [OrderStatus.SHIPPED]
 */
class AdvanceOrderStatusUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(order: OrderEntity): OrderStatus? {
        val nextStatus = when (OrderStatus.fromString(order.status)) {
            OrderStatus.PENDING -> OrderStatus.IN_PREPARATION
            OrderStatus.IN_PREPARATION -> OrderStatus.SHIPPED
            OrderStatus.SHIPPING_IN_PROGRESS -> OrderStatus.SHIPPED
            else -> null
        }
        if (nextStatus != null) {
            orderRepository.updateOrderStatus(order.id, nextStatus)
        }
        return nextStatus
    }
}
