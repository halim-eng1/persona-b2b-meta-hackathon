package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.persona.core.database.entity.ConversationMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [ConversationMetadataEntity] (`conversation_metadata` table).
 *
 * All source class filtering queries use raw TEXT comparisons to avoid Room enum binding issues.
 */
@Dao
interface ConversationMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationMetadataEntity)

    @Query("""
        SELECT * FROM conversation_metadata
        ORDER BY last_message_timestamp DESC
    """)
    fun observeAllConversations(): Flow<List<ConversationMetadataEntity>>

    @Query("""
        SELECT * FROM conversation_metadata
        WHERE source_class = :sourceClass
        ORDER BY last_message_timestamp DESC
    """)
    fun observeConversationsBySource(sourceClass: String): Flow<List<ConversationMetadataEntity>>

    @Query("""
        SELECT * FROM conversation_metadata
        WHERE needs_human_attention = 1 OR unread_count > 0
        ORDER BY last_message_timestamp DESC
    """)
    fun observeNeedsAttentionConversations(): Flow<List<ConversationMetadataEntity>>

    @Query("""
        SELECT * FROM conversation_metadata
        WHERE contact_name = :contactName AND source_class = :sourceClass
        LIMIT 1
    """)
    suspend fun getConversation(contactName: String, sourceClass: String): ConversationMetadataEntity?

    @Query("""
        SELECT * FROM conversation_metadata
        WHERE contact_name = :contactName AND source_class = :sourceClass
        LIMIT 1
    """)
    fun observeConversation(contactName: String, sourceClass: String): Flow<ConversationMetadataEntity?>

    @Query("""
        UPDATE conversation_metadata
        SET is_ai_paused = :isPaused
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun setAiPaused(contactName: String, sourceClass: String, isPaused: Boolean)

    @Query("""
        UPDATE conversation_metadata
        SET is_ai_override_enabled = :enabled
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun setAiOverrideEnabled(contactName: String, sourceClass: String, enabled: Boolean)

    @Query("""
        UPDATE conversation_metadata
        SET needs_human_attention = :needsAttention
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun setNeedsHumanAttention(contactName: String, sourceClass: String, needsAttention: Boolean)

    @Query("""
        UPDATE conversation_metadata
        SET last_notification_key = :notificationKey
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun updateLastNotificationKey(contactName: String, sourceClass: String, notificationKey: String)

    @Query("""
        UPDATE conversation_metadata
        SET unread_count = 0
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun markConversationRead(contactName: String, sourceClass: String)

    @Query("SELECT COALESCE(SUM(unread_count), 0) FROM conversation_metadata")
    fun observeTotalUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversation_metadata WHERE needs_human_attention = 1")
    fun observeTotalAttentionCount(): Flow<Int>

    @Query("""
        SELECT * FROM conversation_metadata
        WHERE contact_name = :contactName
        ORDER BY last_message_timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestConversationForContact(contactName: String): ConversationMetadataEntity?

    /**
     * Deletes the conversation metadata row for a specific contact and source class.
     */
    @Query("DELETE FROM conversation_metadata WHERE contact_name = :contactName AND source_class = :sourceClass")
    suspend fun deleteConversation(contactName: String, sourceClass: String)

    /**
     * Deletes all conversation metadata rows for a specific contact across all sources.
     */
    @Query("DELETE FROM conversation_metadata WHERE contact_name = :contactName")
    suspend fun deleteAllConversationsForContact(contactName: String): Int

    /**
     * Updates the last message details in conversation metadata.
     */
    @Query("""
        UPDATE conversation_metadata
        SET last_message_text = :lastMessageText,
            last_message_timestamp = :lastMessageTimestamp,
            last_message_sender_type = :lastMessageSenderType
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun updateLastMessage(
        contactName: String,
        sourceClass: String,
        lastMessageText: String?,
        lastMessageTimestamp: Long,
        lastMessageSenderType: String
    )

    @Transaction
    suspend fun recordMessageTurn(
        contactName: String,
        sourceClass: String,
        messageText: String,
        senderType: String,
        timestamp: Long,
        notificationKey: String? = null,
        incrementUnread: Boolean = false,
        isAiPaused: Boolean? = null,
        isAiOverrideEnabled: Boolean? = null
    ) {
        val existing = getConversation(contactName, sourceClass)
        if (existing == null) {
            upsert(
                ConversationMetadataEntity(
                    contactName = contactName,
                    sourceClass = sourceClass,
                    isAiPaused = isAiPaused ?: false,
                    isAiOverrideEnabled = isAiOverrideEnabled ?: false,
                    needsHumanAttention = false,
                    lastNotificationKey = notificationKey,
                    lastMessageText = messageText,
                    lastMessageSenderType = senderType,
                    lastMessageTimestamp = timestamp,
                    unreadCount = if (incrementUnread) 1 else 0
                )
            )
        } else {
            upsert(
                existing.copy(
                    lastMessageText = messageText,
                    lastMessageSenderType = senderType,
                    lastMessageTimestamp = timestamp,
                    lastNotificationKey = notificationKey ?: existing.lastNotificationKey,
                    unreadCount = if (incrementUnread) existing.unreadCount + 1 else existing.unreadCount,
                    isAiPaused = isAiPaused ?: existing.isAiPaused,
                    isAiOverrideEnabled = isAiOverrideEnabled ?: existing.isAiOverrideEnabled
                )
            )
        }
    }
}
