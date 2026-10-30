package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import javax.inject.Inject

/**
 * Encapsulates sequential B2B shipping lifecycle progression for orders:
 * - [OrderStatus.PENDING] -> [OrderStatus.IN_PREPARATION]
 * - [OrderStatus.IN_PREPARATION] -> [OrderStatus.SHIPPING_IN_PROGRESS]
 * - [OrderStatus.SHIPPING_IN_PROGRESS] -> [OrderStatus.SHIPPED]
 * - Terminal states ([OrderStatus.SHIPPED], [OrderStatus.CANCELLED], [OrderStatus.RETURNED]) -> null
 */
class AdvanceOrderStatusUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(order: OrderEntity): OrderStatus? {
        val nextStatus = when (OrderStatus.fromString(order.status)) {
            OrderStatus.PENDING -> OrderStatus.IN_PREPARATION
            OrderStatus.IN_PREPARATION -> OrderStatus.SHIPPING_IN_PROGRESS
            OrderStatus.SHIPPING_IN_PROGRESS -> OrderStatus.SHIPPED
            OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.RETURNED -> null
        }
        if (nextStatus != null) {
            orderRepository.updateOrderStatus(order.id, nextStatus)
        }
        return nextStatus
    }
}
