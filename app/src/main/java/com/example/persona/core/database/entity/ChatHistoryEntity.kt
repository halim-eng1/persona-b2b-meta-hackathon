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
        Index(value = ["contact_name"]),
        Index(value = ["contact_name", "source_class", "timestamp"]),
        Index(value = ["contact_name", "source_class"])
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
     * False if it was generated and sent by the bot or merchant (outbound).
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
    val embeddingState: String = EmbeddingJobState.PENDING.name,

    /** WhatsApp source package classification: MAIN_APP, BUSINESS, CLONE. */
    @ColumnInfo(name = "source_class", defaultValue = "BUSINESS")
    val sourceClass: String = "BUSINESS",

    /** Sender origin: CUSTOMER, AI_AGENT, MERCHANT_MANUAL. */
    @ColumnInfo(name = "sender_type", defaultValue = "CUSTOMER")
    val senderType: String = "CUSTOMER",

    /** Message delivery state: SENDING, SENT, FAILED, QUEUED. */
    @ColumnInfo(name = "delivery_status", defaultValue = "SENT")
    val deliveryStatus: String = "SENT",

    /** Read receipt flag for merchant CRM view. 1 = read, 0 = unread. */
    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Boolean = false
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
            embeddingState == other.embeddingState &&
            sourceClass == other.sourceClass &&
            senderType == other.senderType &&
            deliveryStatus == other.deliveryStatus &&
            isRead == other.isRead
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contactName.hashCode()
        result = 31 * result + messageText.hashCode()
        result = 31 * result + isFromUser.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + embeddingState.hashCode()
        result = 31 * result + sourceClass.hashCode()
        result = 31 * result + senderType.hashCode()
        result = 31 * result + deliveryStatus.hashCode()
        result = 31 * result + isRead.hashCode()
        return result
    }
}
