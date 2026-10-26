package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent record of a customer order extracted from an LLM-parsed WhatsApp conversation.
 *
 * Orders are ingested automatically when the first-pass LLM response contains a populated
 * [extracted_order] JSON field inside [AgenticLlmResponse]. Before insertion, the orchestrator
 * performs a deduplication check via [com.example.persona.core.database.dao.OrderDao.getActiveMatchingOrder]
 * to avoid creating duplicate records for the same ongoing conversation thread.
 *
 * Status Lifecycle:
 *  New orders are always inserted with [status] = [OrderStatus.PENDING.name].
 *  The shop owner updates [status] and [merchantNote] externally via the dashboard UI.
 *  [merchantNote] is injected into the LLM prompt's PERSONAL USER STATE block so the
 *  bot can relay real-time fulfilment notes to the customer.
 *
 * Index Strategy:
 *  - Composite (contactName, status): Rapid verification of customer ticket states.
 *    Enables the deduplication check and active order queries in a single indexed scan.
 *  - Standalone (contactName): Broad contact-scoped queries without status filtering.
 */
@Entity(
    tableName = "orders_table",
    indices = [
        Index(value = ["contact_name", "status"]),
        Index(value = ["contact_name"])
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** WhatsApp display name of the contact. Matches [ProcessedNotification.contactName]. */
    @ColumnInfo(name = "contact_name")
    val contactName: String,

    /** Customer's real name as extracted by the LLM from the conversation. May be null. */
    @ColumnInfo(name = "customer_name_from_llm")
    val customerNameFromLlm: String? = null,

    /** Delivery address as extracted by the LLM. May be null if not yet provided. */
    @ColumnInfo(name = "delivery_address")
    val deliveryAddress: String? = null,

    /** Structured order details (items, quantities, variants) as extracted by the LLM. */
    @ColumnInfo(name = "order_details")
    val orderDetails: String? = null,

    /**
     * Catch-all field for unstructured parameters captured by the LLM that don't fit
     * into the standard fields. Also used as the merge target during the deduplication
     * verification pivot (appended with new details when the customer confirms modification
     * of an existing order instead of creating a new one).
     */
    @ColumnInfo(name = "custom_notes")
    val customNotes: String? = null,

    /**
     * Mutable field updated externally by the shop owner via the dashboard UI.
     * Injected into the PERSONAL USER STATE block so the LLM can relay real-time
     * fulfilment notes (e.g., "المندوب راح والعنوان مكانش موجود") to the customer.
     */
    @ColumnInfo(name = "merchant_note")
    val merchantNote: String? = null,

    /** Unix epoch milliseconds at order creation time. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Order lifecycle status stored as TEXT.
     * Allowed values are enforced by [OrderStatus] enum.
     * Converted via [com.example.persona.core.database.Converters] TypeConverter.
     * Unknown/corrupted values fall back safely to [OrderStatus.PENDING].
     * Default: [OrderStatus.PENDING.name] — all new orders start as PENDING.
     */
    @ColumnInfo(name = "status")
    val status: String = OrderStatus.PENDING.name
)
