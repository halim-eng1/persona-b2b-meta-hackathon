package com.example.persona.features.orders.domain.usecase

import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvanceOrderStatusUseCaseTest {

    private val orderRepository = mockk<OrderRepository>(relaxed = true)
    private val useCase = AdvanceOrderStatusUseCase(orderRepository)

    @Test
    fun `advances PENDING to IN_PREPARATION`() = runTest {
        val order = OrderEntity(id = 10L, contactName = "01000", status = OrderStatus.PENDING.name)
        val result = useCase(order)

        assertEquals(OrderStatus.IN_PREPARATION, result)
        coVerify(exactly = 1) { orderRepository.updateOrderStatus(10L, OrderStatus.IN_PREPARATION) }
    }

    @Test
    fun `advances IN_PREPARATION to SHIPPED`() = runTest {
        val order = OrderEntity(id = 11L, contactName = "01000", status = OrderStatus.IN_PREPARATION.name)
        val result = useCase(order)

        assertEquals(OrderStatus.SHIPPED, result)
        coVerify(exactly = 1) { orderRepository.updateOrderStatus(11L, OrderStatus.SHIPPED) }
    }

    @Test
    fun `advances SHIPPING_IN_PROGRESS to SHIPPED`() = runTest {
        val order = OrderEntity(id = 12L, contactName = "01000", status = OrderStatus.SHIPPING_IN_PROGRESS.name)
        val result = useCase(order)

        assertEquals(OrderStatus.SHIPPED, result)
        coVerify(exactly = 1) { orderRepository.updateOrderStatus(12L, OrderStatus.SHIPPED) }
    }

    @Test
    fun `returns null and does not update for terminal statuses`() = runTest {
        val cancelledOrder = OrderEntity(id = 13L, contactName = "01000", status = OrderStatus.CANCELLED.name)
        val shippedOrder = OrderEntity(id = 14L, contactName = "01000", status = OrderStatus.SHIPPED.name)
        val returnedOrder = OrderEntity(id = 15L, contactName = "01000", status = OrderStatus.RETURNED.name)

        assertNull(useCase(cancelledOrder))
        assertNull(useCase(shippedOrder))
        assertNull(useCase(returnedOrder))

        coVerify(exactly = 0) { orderRepository.updateOrderStatus(any(), any()) }
    }
}
