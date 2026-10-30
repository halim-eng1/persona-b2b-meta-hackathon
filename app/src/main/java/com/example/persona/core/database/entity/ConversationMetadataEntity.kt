package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Aggregated conversation state and routing metadata per WhatsApp contact and source package.
 *
 * Uses the composite primary key `(contact_name, source_class)` so contacts with the same
 * display name across different WhatsApp instances (Main, Business, Clone) do not collide.
 */
@Entity(
    tableName = "conversation_metadata",
    primaryKeys = ["contact_name", "source_class"],
    indices = [
        Index(value = ["last_message_timestamp"]),
        Index(value = ["unread_count"]),
        Index(value = ["needs_human_attention"])
    ]
)
data class ConversationMetadataEntity(
    /** Contact display name in WhatsApp. */
    @ColumnInfo(name = "contact_name")
    val contactName: String,

    /** WhatsApp source package classification: MAIN_APP, BUSINESS, CLONE. */
    @ColumnInfo(name = "source_class", defaultValue = "BUSINESS")
    val sourceClass: String = "BUSINESS",

    /** When true, the AI bot does not automatically generate or send replies for this contact. */
    @ColumnInfo(name = "is_ai_paused", defaultValue = "0")
    val isAiPaused: Boolean = false,

    /**
     * When true, explicitly overrides global auto-reply pause, allowing AI auto-replies
     * exclusively for this specific contact even if global auto-reply is turned OFF.
     */
    @ColumnInfo(name = "is_ai_override_enabled", defaultValue = "0")
    val isAiOverrideEnabled: Boolean = false,

    /** Flagged when human-in-the-loop (HITL) intervention is needed (e.g. urgent safety alert or low confidence). */
    @ColumnInfo(name = "needs_human_attention", defaultValue = "0")
    val needsHumanAttention: Boolean = false,

    /** Notification key of the most recent active notification, used for reply matching. */
    @ColumnInfo(name = "last_notification_key")
    val lastNotificationKey: String? = null,

    /** Snippet of the most recent message in the thread. */
    @ColumnInfo(name = "last_message_text")
    val lastMessageText: String? = null,

    /** Sender type of the most recent message: CUSTOMER, AI_AGENT, MERCHANT_MANUAL. */
    @ColumnInfo(name = "last_message_sender_type", defaultValue = "CUSTOMER")
    val lastMessageSenderType: String = "CUSTOMER",

    /** Timestamp of the most recent message in Unix epoch milliseconds. */
    @ColumnInfo(name = "last_message_timestamp", defaultValue = "0")
    val lastMessageTimestamp: Long = 0L,

    /** Number of unread customer messages in this thread. */
    @ColumnInfo(name = "unread_count", defaultValue = "0")
    val unreadCount: Int = 0
)
