package com.example.persona.core.database.entity

import androidx.room.ColumnInfo

/**
 * Lightweight aggregate projection for today's orders count and pending COD revenue,
 * calculated directly in SQLite to avoid loading full [OrderEntity] collections into memory.
 */
data class TodayOrdersAggregate(
    @ColumnInfo(name = "order_count")
    val orderCount: Int = 0,

    @ColumnInfo(name = "pending_revenue")
    val pendingRevenue: Double = 0.0
)
