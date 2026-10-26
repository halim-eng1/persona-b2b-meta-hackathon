package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.ChatHistoryEntity

/**
 * Data access object for the [chat_history] table.
 *
 * Designed for three distinct access patterns:
 *  1. **Conversational context**: [getRecentMessages] fetches the last N chronological
 *     turns to inject into the LLM prompt as the CHAT HISTORY block.
 *  2. **Personal vector sweep**: [getEmbeddedMessages] returns messages with completed
 *     embeddings for in-memory cosine similarity search (Source C of the context aggregator).
 *  3. **Background embedding queue**: [getPendingEmbeddingRows] + [claimForProcessing]
 *     implement a check-before-compute state machine to prevent duplicate ONNX executions
 *     under notification burst backpressure.
 *
 * Pagination via [limit]/[offset] parameters prevents memory degradation over long
 * conversational histories. All ordering is chronological (timestamp ASC).
 */
@Dao
interface ChatHistoryDao {

    /**
     * Inserts a new chat history row and returns its auto-generated row ID.
     * Called immediately on notification arrival with [embedding] = null and
     * [embeddingState] = "PENDING", before the background embedding job is launched.
     *
     * REPLACE conflict strategy handles the edge case of a duplicate timestamp insert
     * gracefully without crashing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatHistoryEntity): Long

    /**
     * Returns the [limit] most recent messages for [contactName], ordered chronologically
     * (oldest first within the result set, newest fetched first by DESC then reversed
     * via the subquery pattern below).
     *
     * Paginated via [offset] to allow loading earlier history if needed.
     * Leverages the composite (contact_name, timestamp) index for O(log n) retrieval.
     *
     * Result is ordered ASC so that the LLM receives messages in natural conversation order.
     */
    @Query("""
        SELECT * FROM chat_history
        WHERE contact_name = :contactName
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecentMessages(
        contactName: String,
        limit: Int = 6,
        offset: Int = 0
    ): List<ChatHistoryEntity>

    /**
     * Returns the [limit] most recent messages for [contactName] excluding [excludeId].
     * Used to avoid duplicating the current inbound message in the LLM context.
     */
    @Query("""
        SELECT * FROM chat_history
        WHERE contact_name = :contactName AND id != :excludeId
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecentMessagesExcept(
        contactName: String,
        excludeId: Long,
        limit: Int = 6,
        offset: Int = 0
    ): List<ChatHistoryEntity>

    /**
     * Backfills the [embedding] BLOB for a specific row after the background ONNX job completes.
     * Called only after a successful [claimForProcessing] and successful inference result.
     */
    @Query("UPDATE chat_history SET embedding = :embeddingBlob, embedding_state = 'DONE' WHERE id = :id")
    suspend fun updateEmbeddingRaw(id: Long, embeddingBlob: ByteArray)

    /**
     * Transitions the [embeddingState] column for a specific row.
     * Used to move a row from PROCESSING → DONE or PROCESSING → FAILED.
     */
    @Query("UPDATE chat_history SET embedding_state = :state WHERE id = :id")
    suspend fun updateEmbeddingState(id: Long, state: String)

    /**
     * Returns up to [limit] messages for [contactName] that have a completed embedding
     * ([embeddingState] = "DONE") and a non-null [embedding] BLOB.
     *
     * Used by the context aggregator's personal vector sweep (Source C) to perform
     * in-memory cosine similarity scoring against the current query embedding.
     *
     * Ordered DESC by timestamp so the most recent personal history is prioritised
     * when the result set is limited by the context budget.
     */
    @Query("""
        SELECT * FROM chat_history
        WHERE contact_name = :contactName
          AND embedding_state = 'DONE'
          AND embedding IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getEmbeddedMessages(
        contactName: String,
        limit: Int = 20
    ): List<ChatHistoryEntity>

    /**
     * Returns up to [limit] rows globally where [embeddingState] = "PENDING".
     * Used by a batch queue draining worker if one is added in future phases.
     * Ordered by timestamp ASC (oldest first) to maintain FIFO processing order.
     */
    @Query("""
        SELECT * FROM chat_history
        WHERE embedding_state = 'PENDING'
        ORDER BY timestamp ASC
        LIMIT :limit
    """)
    suspend fun getPendingEmbeddingRows(limit: Int = 10): List<ChatHistoryEntity>

    /**
     * **Atomic check-before-compute claim operation.**
     *
     * Attempts to transition a row from PENDING → PROCESSING in a single SQL UPDATE.
     * The WHERE clause guards guarantee atomicity without needing a separate SELECT:
     * - `id = :id` — targets the specific row
     * - `embedding_state = 'PENDING'` — only claims rows that haven't been taken yet
     *
     * Returns the number of rows updated:
     * - **1** → claim succeeded; caller MUST proceed with ONNX inference
     * - **0** → row was already claimed or completed; caller MUST skip to prevent duplicate computation
     *
     * This prevents duplicate vector calculations under rapid-fire notification bursts
     * without requiring a distributed lock or mutex beyond Room's SQLite transaction guarantees.
     */
    @Query("""
        UPDATE chat_history
        SET embedding_state = 'PROCESSING'
        WHERE id = :id AND embedding_state = 'PENDING'
    """)
    suspend fun claimForProcessing(id: Long): Int

    /** Total row count — useful for diagnostics and UI. */
    @Query("SELECT COUNT(*) FROM chat_history")
    suspend fun getTotalCount(): Int

    /** Row count per contact — useful for pagination limit calculations. */
    @Query("SELECT COUNT(*) FROM chat_history WHERE contact_name = :contactName")
    suspend fun getCountForContact(contactName: String): Int
}
