package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent record of every inbound customer message and outbound bot reply
 * for a given WhatsApp contact.
 *
 * Dual-purpose:
 *  1. Provides the last N chronological turns as conversational context for LLM prompts.
 *  2. Stores semantic embeddings for personal vector sweep — enabling Source C retrieval
 *     (customer-specific historical context) inside the multi-source context aggregator.
 *
 * Embedding Lifecycle:
 *  Rows are inserted immediately with [embedding] = null and [embeddingState] = "PENDING".
 *  A fire-and-forget background coroutine (Dispatchers.Default) claims the row atomically
 *  via the [com.example.persona.core.database.dao.ChatHistoryDao.claimForProcessing] query,
 *  runs ONNX inference, and updates the embedding + state — all without touching the
 *  WhatsApp notification dispatcher thread.
 *
 * Index Strategy:
 *  - Composite (contactName, timestamp): High-speed chronological retrieval per contact.
 *  - Standalone (contactName): Broad contact-scoped queries without timestamp ordering.
 */
@Entity(
    tableName = "chat_history",
    indices = [
        Index(value = ["contact_name", "timestamp"]),
        Index(value = ["contact_name"])
    ]
)
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** WhatsApp display name of the contact. Matches [ProcessedNotification.contactName]. */
    @ColumnInfo(name = "contact_name")
    val contactName: String,

    /** The raw message text — either the customer's inbound message or the bot's reply. */
    @ColumnInfo(name = "message_text")
    val messageText: String,

    /**
     * True if this message was sent by the customer (inbound).
     * False if it was generated and sent by the bot (outbound).
     */
    @ColumnInfo(name = "is_from_user")
    val isFromUser: Boolean,

    /** Unix epoch milliseconds at the time the message was received or sent. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /**
     * 384-dimensional L2-normalized embedding from the MiniLM-L12-v2-int8 model,
     * serialised to BLOB via [com.example.persona.core.database.Converters.fromFloatArray].
     * Null until the background embedding job completes.
     */
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: FloatArray? = null,

    /**
     * Lifecycle state of the background embedding computation job.
     * Stored as TEXT using the [com.example.persona.core.database.Converters] TypeConverter.
     * Transitions: PENDING → PROCESSING → DONE | FAILED.
     *
     * Default: [EmbeddingJobState.PENDING.name] — immediately eligible for background pickup.
     */
    @ColumnInfo(name = "embedding_state")
    val embeddingState: String = EmbeddingJobState.PENDING.name
) {
    // FloatArray breaks structural equality — override explicitly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatHistoryEntity) return false
        return id == other.id &&
            contactName == other.contactName &&
            messageText == other.messageText &&
            isFromUser == other.isFromUser &&
            timestamp == other.timestamp &&
            (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null)) &&
            embeddingState == other.embeddingState
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contactName.hashCode()
        result = 31 * result + messageText.hashCode()
        result = 31 * result + isFromUser.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + embeddingState.hashCode()
        return result
    }
}
