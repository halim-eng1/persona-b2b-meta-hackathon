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
        UPDATE conversation_metadata
        SET is_ai_paused = :isPaused
        WHERE contact_name = :contactName AND source_class = :sourceClass
    """)
    suspend fun setAiPaused(contactName: String, sourceClass: String, isPaused: Boolean)

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

    @Transaction
    suspend fun recordMessageTurn(
        contactName: String,
        sourceClass: String,
        messageText: String,
        senderType: String,
        timestamp: Long,
        notificationKey: String? = null,
        incrementUnread: Boolean = false,
        isAiPaused: Boolean? = null
    ) {
        val existing = getConversation(contactName, sourceClass)
        if (existing == null) {
            upsert(
                ConversationMetadataEntity(
                    contactName = contactName,
                    sourceClass = sourceClass,
                    isAiPaused = isAiPaused ?: false,
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
                    isAiPaused = isAiPaused ?: existing.isAiPaused
                )
            )
        }
    }
}
