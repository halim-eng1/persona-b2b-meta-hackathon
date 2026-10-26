package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["contact_name"]),
        Index(value = ["timestamp"]),
        Index(value = ["intent_category"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_name") val contactName: String,
    @ColumnInfo(name = "message_text") val messageText: String,
    @ColumnInfo(name = "is_user_message") val isUserMessage: Boolean,
    @ColumnInfo(name = "timestamp") val timestamp: Instant,
    @ColumnInfo(name = "intent_category") val intentCategory: String,
    @ColumnInfo(name = "is_grouped") val isGrouped: Boolean = false,
    @ColumnInfo(name = "source_format") val sourceFormat: String,
    @ColumnInfo(name = "normalized_text") val normalizedText: String?
)
