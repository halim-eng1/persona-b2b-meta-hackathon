package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Customer tag classification for B2B CRM.
 */
enum class ContactTag(
    val labelAr: String,
    @androidx.annotation.StringRes val labelResId: Int
) {
    REGULAR("عميل عادي", com.example.persona.R.string.contact_tag_regular),
    VIP("عميل VIP", com.example.persona.R.string.contact_tag_vip),
    DEBTOR("عميل آجل/مديونية", com.example.persona.R.string.contact_tag_debtor),
    PROBLEMATIC("عميل مرتجعات/مشاكل", com.example.persona.R.string.contact_tag_problematic),
    BLOCKED("محظور", com.example.persona.R.string.contact_tag_blocked)
}

@Entity(
    tableName = "contacts_table",
    indices = [
        Index(value = ["contact_name"], unique = true),
        Index(value = ["phone_number"]),
        Index(value = ["tag"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "contact_name")
    val contactName: String,

    @ColumnInfo(name = "phone_number", defaultValue = "''")
    val phoneNumber: String = "",

    @ColumnInfo(name = "notes", defaultValue = "''")
    val notes: String = "",

    @ColumnInfo(name = "tag", defaultValue = "'REGULAR'")
    val tag: String = ContactTag.REGULAR.name,

    @ColumnInfo(name = "total_orders", defaultValue = "0")
    val totalOrders: Int = 0,

    @ColumnInfo(name = "total_spent", defaultValue = "0.0")
    val totalSpent: Double = 0.0,

    @ColumnInfo(name = "last_active_timestamp", defaultValue = "0")
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)
