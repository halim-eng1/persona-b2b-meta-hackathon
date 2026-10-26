package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "linked_contacts",
    indices = [Index(value = ["whatsapp_display_name"], unique = true)]
)
data class LinkedContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "whatsapp_display_name") val whatsappDisplayName: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "auto_reply_enabled") val autoReplyEnabled: Boolean = true,
    @ColumnInfo(name = "cooldown_count") val cooldownCount: Int = 0,
    @ColumnInfo(name = "cooldown_reset_at") val cooldownResetAt: Instant?,
    @ColumnInfo(name = "linked_at") val linkedAt: Instant,
    @ColumnInfo(name = "total_messages_parsed") val totalMessagesParsed: Int = 0,
    @ColumnInfo(name = "dnd_start_hour") val dndStartHour: Int? = null,
    @ColumnInfo(name = "dnd_end_hour") val dndEndHour: Int? = null
)
