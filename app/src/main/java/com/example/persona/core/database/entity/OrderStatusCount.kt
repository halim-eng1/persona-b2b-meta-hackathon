package com.example.persona.core.database.entity

import androidx.room.ColumnInfo

data class OrderStatusCount(
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "count")
    val count: Int
)
