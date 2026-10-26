package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "reply_log",
    foreignKeys = [
        ForeignKey(
            entity = LinkedContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contact_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["confidence_score"])
    ]
)
data class ReplyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    @ColumnInfo(name = "incoming_message") val incomingMessage: String,
    @ColumnInfo(name = "generated_reply") val generatedReply: String,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float,
    @ColumnInfo(name = "was_auto_sent") val wasAutoSent: Boolean,
    @ColumnInfo(name = "was_approved") val wasApproved: Boolean?,
    @ColumnInfo(name = "timestamp") val timestamp: Instant,
    @ColumnInfo(name = "retrieved_message_ids") val retrievedMessageIds: List<String>
)
