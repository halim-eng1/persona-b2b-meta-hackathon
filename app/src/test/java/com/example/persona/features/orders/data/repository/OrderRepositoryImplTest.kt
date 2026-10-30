package com.example.persona.features.orders.data.repository

import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OrderRepositoryImplTest {

    private val orderDao = mockk<OrderDao>(relaxed = true)
    private lateinit var repository: OrderRepositoryImpl

    @Before
    fun setUp() {
        repository = OrderRepositoryImpl(orderDao)
    }

    @Test
    fun `createOrder assigns 99101 as initial 5-digit ID when database is empty`() = runTest {
        coEvery { orderDao.getMaxOrderId() } returns null
        val slot = slot<OrderEntity>()
        coEvery { orderDao.insert(capture(slot)) } returns 99101L

        val inputOrder = OrderEntity(
            id = 0L,
            contactName = "01012345678",
            orderDetails = "قميص قطن",
            totalAmount = 250.0,
            status = OrderStatus.PENDING.name
        )

        val resultId = repository.createOrder(inputOrder)

        assertEquals(99101L, resultId)
        coVerify(exactly = 1) { orderDao.getMaxOrderId() }
        assertEquals(99101L, slot.captured.id)
    }

    @Test
    fun `createOrder increments 5-digit ID based on existing maxId in database`() = runTest {
        coEvery { orderDao.getMaxOrderId() } returns 99105L
        val slot = slot<OrderEntity>()
        coEvery { orderDao.insert(capture(slot)) } returns 99106L

        val inputOrder = OrderEntity(
            id = 0L,
            contactName = "01012345678",
            orderDetails = "قميص قطن",
            totalAmount = 250.0,
            status = OrderStatus.PENDING.name
        )

        val resultId = repository.createOrder(inputOrder)

        assertEquals(99106L, resultId)
        coVerify(exactly = 1) { orderDao.getMaxOrderId() }
        assertEquals(99106L, slot.captured.id)
    }

    @Test
    fun `createOrder guarantees at least 99101 if legacy IDs in database were smaller than 99100`() = runTest {
        // e.g. Legacy order IDs 1, 2, 3
        coEvery { orderDao.getMaxOrderId() } returns 42L
        val slot = slot<OrderEntity>()
        coEvery { orderDao.insert(capture(slot)) } returns 99101L

        val inputOrder = OrderEntity(
            id = 0L,
            contactName = "01012345678",
            orderDetails = "قميص قطن",
            totalAmount = 250.0,
            status = OrderStatus.PENDING.name
        )

        val resultId = repository.createOrder(inputOrder)

        assertEquals(99101L, resultId)
        coVerify(exactly = 1) { orderDao.getMaxOrderId() }
        assertEquals(99101L, slot.captured.id)
    }

    @Test
    fun `createOrder preserves explicit non-zero ID without querying maxId`() = runTest {
        val slot = slot<OrderEntity>()
        coEvery { orderDao.insert(capture(slot)) } returns 55555L

        val inputOrder = OrderEntity(
            id = 55555L,
            contactName = "01012345678",
            orderDetails = "قميص قطن",
            totalAmount = 250.0,
            status = OrderStatus.PENDING.name
        )

        val resultId = repository.createOrder(inputOrder)

        assertEquals(55555L, resultId)
        coVerify(exactly = 0) { orderDao.getMaxOrderId() }
        assertEquals(55555L, slot.captured.id)
    }
}
