package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Immutable audit record for every reply that was sent or queued.
 *
 * B2B migration note: the old FK on `linked_contacts.id` has been removed.
 * Contact identity is now stored directly as a display name string, matching
 * how [ReplyQueueEntity] and the notification system work.
 */
@Entity(
    tableName = "reply_log",
    indices = [
        Index(value = ["contact_display_name"]),
        Index(value = ["timestamp"]),
        Index(value = ["confidence_score"])
    ]
)
data class ReplyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_display_name") val contactDisplayName: String,
    @ColumnInfo(name = "incoming_message") val incomingMessage: String,
    @ColumnInfo(name = "generated_reply") val generatedReply: String,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float,
    @ColumnInfo(name = "similarity_score") val similarityScore: Float,
    @ColumnInfo(name = "context_confidence") val contextConfidence: Float,
    @ColumnInfo(name = "was_auto_sent") val wasAutoSent: Boolean,
    @ColumnInfo(name = "was_approved") val wasApproved: Boolean?,
    @ColumnInfo(name = "timestamp") val timestamp: Instant,
    /** Chunk IDs that contributed to generating this reply. */
    @ColumnInfo(name = "retrieved_chunk_ids") val retrievedChunkIds: List<String>
)
