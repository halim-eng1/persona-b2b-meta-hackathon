package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatusCount
import com.example.persona.core.database.entity.TodayOrdersAggregate
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the [orders_table] table.
 *
 * Key access patterns:
 *  1. **Order ingestion**: [insert] adds new LLM-extracted orders with PENDING status.
 *  2. **Deduplication**: [getActiveMatchingOrder] checks for an existing unfulfilled order
 *     before inserting, enabling the conversational verification pivot.
 *  3. **Context aggregation**: [getActiveOrdersByContact] feeds the PERSONAL USER STATE
 *     block injected into the LLM prompt, exposing live order status and merchant notes.
 *  4. **Merge flow**: [appendCustomNotes] concatenates new details into an existing order's
 *     [customNotes] when a customer confirms modification of a duplicate order.
 *  5. **Dashboard UI**: [observeAllOrders] provides a reactive Flow for the order management screen.
 *
 * All queries leverage the composite (contact_name, status) index for efficient filtering.
 */
@Dao
interface OrderDao {

    /**
     * Inserts a new order row and returns its auto-generated row ID.
     * All new orders are inserted with [OrderEntity.status] = [OrderStatus.PENDING.name].
     *
     * ABORT conflict strategy (default) is intentional — duplicate PK inserts should fail
     * loudly rather than silently overwriting existing order data.
     */
    /**
     * Returns the maximum order ID currently in the database, or null if empty.
     * Used to calculate 5-digit order numbers starting at #99101.
     */
    @Query("SELECT MAX(id) FROM orders_table")
    suspend fun getMaxOrderId(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OrderEntity): Long

    /**
     * Returns a paginated list of all orders for [contactName], ordered by most recent first.
     * Leverages the standalone (contact_name) index.
     *
     * Use [limit] and [offset] to implement infinite scroll in the order history UI.
     */
    @Query("""
        SELECT * FROM orders_table
        WHERE contact_name = :contactName
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getOrdersByContact(
        contactName: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<OrderEntity>

    /**
     * Returns all active (non-terminal) orders for [contactName].
     * Terminal states excluded: SHIPPED, CANCELLED, RETURNED.
     *
     * Used to feed the PERSONAL USER STATE context block in the LLM prompt.
     * Leverages the composite (contact_name, status) index.
     */
    @Query("""
        SELECT * FROM orders_table
        WHERE contact_name = :contactName
          AND status NOT IN ('SHIPPED', 'CANCELLED', 'RETURNED')
        ORDER BY timestamp DESC
    """)
    suspend fun getActiveOrdersByContact(contactName: String): List<OrderEntity>

    /**
     * Returns the single most recent order for [contactName], regardless of status.
     * Injected into the prompt's 'Last Inactive/Cancelled Order' block so the LLM
     * has instant context when an order was just cancelled or completed.
     */
    @Query("""
        SELECT * FROM orders_table
        WHERE contact_name = :contactName
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestOrderByContact(contactName: String): OrderEntity?

    /**
     * Returns the aggregate count of orders grouped by status for [contactName].
     * Feeds the 'Orders Summary' line in <customer_order_state>.
     */
    @Query("""
        SELECT status, COUNT(*) as count
        FROM orders_table
        WHERE contact_name = :contactName
        GROUP BY status
    """)
    suspend fun getOrderStatusCounts(contactName: String): List<OrderStatusCount>

    /**
     * **Deduplication verification lookup.**
     *
     * Returns the first active unfulfilled order matching [contactName] where the
     * status is one of the two UNFULFILLED states: [status1] ("PENDING") or [status2]
     * ("IN_PREPARATION"). Returns null if no such order exists.
     *
     * Called by the orchestrator's order ingestion flow BEFORE inserting a new order.
     * If a match is returned, the orchestrator pivots to a conversational confirmation
     * message instead of blindly inserting a duplicate record.
     *
     * Leverages the composite (contact_name, status) index — O(log n) lookup.
     */
    @Query("""
        SELECT * FROM orders_table
        WHERE contact_name = :contactName
          AND status IN (:status1, :status2)
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getActiveMatchingOrder(
        contactName: String,
        status1: String = "PENDING",
        status2: String = "IN_PREPARATION"
    ): OrderEntity?

    /**
     * Reactive observable of all orders across all contacts, ordered by most recent first.
     * Used by the order management dashboard UI.
     */
    @Query("SELECT * FROM orders_table ORDER BY timestamp DESC")
    fun observeAllOrders(): Flow<List<OrderEntity>>

    /**
     * Updates the [merchantNote] field for a specific order row.
     * Called by the shop owner via the dashboard UI to attach fulfillment notes
     * (e.g., "المندوب حيوصل بكرة الساعة 3") that the LLM will relay to the customer.
     */
    @Query("UPDATE orders_table SET merchant_note = :note WHERE id = :id")
    suspend fun updateMerchantNote(id: Long, note: String?)

    /**
     * Updates the [status] field for a specific order row.
     * Status transitions must use valid [OrderStatus] names; invalid values fall
     * back to PENDING via the TypeConverter at read time.
     */
    @Query("UPDATE orders_table SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /**
     * **Order merge operation.**
     *
     * Appends [additionalNotes] to the existing [customNotes] of a specific order row,
     * separated by a newline. Used when a customer confirms modification of an existing
     * order during the deduplication verification pivot instead of creating a new record.
     *
     * Uses SQLite's COALESCE to handle the case where [customNotes] is currently null:
     * `COALESCE(custom_notes, '') || '\n' || :additionalNotes`
     */
    @Query("""
        UPDATE orders_table
        SET custom_notes = COALESCE(custom_notes, '') || char(10) || :additionalNotes
        WHERE id = :id
    """)
    suspend fun appendCustomNotes(id: Long, additionalNotes: String)

    /**
     * Updates the [deliveryAddress] field for a specific order row.
     */
    @Query("UPDATE orders_table SET delivery_address = :address WHERE id = :id")
    suspend fun updateDeliveryAddress(id: Long, address: String)

    /**
     * Updates the [orderDetails] field for a specific order row.
     */
    @Query("UPDATE orders_table SET order_details = :details WHERE id = :id")
    suspend fun updateOrderDetails(id: Long, details: String)

    /**
     * Updates the [customerNameFromLlm] field for a specific order row.
     */
    @Query("UPDATE orders_table SET customer_name_from_llm = :name WHERE id = :id")
    suspend fun updateCustomerName(id: Long, name: String)

    /** Total order count — useful for diagnostics. */
    @Query("SELECT COUNT(*) FROM orders_table")
    suspend fun getTotalCount(): Int

    /**
     * Updates full order details from the merchant edit dialog.
     */
    @Query("""
        UPDATE orders_table
        SET customer_name_from_llm = :name,
            customer_phone = :phone,
            delivery_address = :address,
            order_details = :details,
            total_amount = :total,
            shipping_fee = :shipping,
            governorate = :gov
        WHERE id = :id
    """)
    suspend fun updateFullOrderDetails(
        id: Long,
        name: String?,
        phone: String,
        address: String?,
        details: String?,
        total: Double,
        shipping: Double,
        gov: String?
    )

    /**
     * Cancels or transitions an order with an explicit cancellation/rejection reason.
     */
    @Query("UPDATE orders_table SET status = :status, cancellation_reason = :reason WHERE id = :id")
    suspend fun updateStatusWithReason(id: Long, status: String, reason: String?)

    /**
     * Updates the status of multiple orders in bulk.
     */
    @Query("UPDATE orders_table SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusBulk(ids: List<Long>, status: String)

    /**
     * Permanently deletes an order by its ID.
     */
    @Query("DELETE FROM orders_table WHERE id = :id")
    suspend fun deleteOrder(id: Long)

    /**
     * Reactive observable of orders filtered by status.
     */
    @Query("SELECT * FROM orders_table WHERE status = :status ORDER BY timestamp DESC")
    fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>>

    /**
     * Reassigns all orders from [oldContactName] to [newContactName].
     * Used when merging contacts.
     */
    @Query("UPDATE orders_table SET contact_name = :newContactName WHERE contact_name = :oldContactName")
    suspend fun reassignContactOrders(oldContactName: String, newContactName: String): Int

    /**
     * Reactive observable of orders within a specific timestamp date range.
     */
    @Query("SELECT * FROM orders_table WHERE timestamp BETWEEN :fromTimestamp AND :toTimestamp ORDER BY timestamp DESC")
    fun observeOrdersByDateRange(fromTimestamp: Long, toTimestamp: Long): Flow<List<OrderEntity>>

    /**
     * Efficiently calculates today's total orders and pending Cash On Delivery revenue
     * directly in SQLite, avoiding loading all order records into memory.
     */
    @Query("""
        SELECT 
            COUNT(*) AS order_count,
            COALESCE(SUM(CASE WHEN UPPER(TRIM(status)) IN ('PENDING', 'IN_PREPARATION') THEN total_amount ELSE 0.0 END), 0.0) AS pending_revenue
        FROM orders_table
        WHERE timestamp >= :startOfToday
    """)
    fun observeTodayOrdersAggregate(startOfToday: Long): Flow<TodayOrdersAggregate>
}
