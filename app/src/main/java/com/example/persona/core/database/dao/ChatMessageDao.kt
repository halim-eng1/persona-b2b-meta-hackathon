package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Embedded
import androidx.room.ColumnInfo

data class UserMessageWithTrigger(
    @Embedded val message: ChatMessageEntity,
    @ColumnInfo(name = "trigger_text") val triggerText: String?
)

data class ShortMessageReplyCandidate(
    @ColumnInfo(name = "message_text") val messageText: String,
    @ColumnInfo(name = "timestamp") val timestamp: kotlinx.datetime.Instant
)

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE contact_name = :contactName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesForContact(contactName: String, limit: Int = 100): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE is_user_message = 1 AND contact_name = :contactName")
    suspend fun getUserMessagesForContact(contactName: String): List<ChatMessageEntity>

    @Query("""
        SELECT m.*,
               (SELECT m2.message_text 
                FROM chat_messages m2 
                WHERE m2.contact_name = m.contact_name 
                  AND m2.is_user_message = 0 
                  AND m2.timestamp < m.timestamp 
                ORDER BY m2.timestamp DESC LIMIT 1) AS trigger_text
        FROM chat_messages m
        WHERE m.is_user_message = 1 AND m.contact_name = :contactName
    """)
    suspend fun getUserMessagesWithTriggersForContact(contactName: String): List<UserMessageWithTrigger>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE contact_name = :contactName")
    suspend fun getMessageCountForContact(contactName: String): Int

    @Query("DELETE FROM chat_messages WHERE contact_name = :contactName")
    suspend fun deleteMessagesForContact(contactName: String)

    @Query("SELECT DISTINCT contact_name FROM chat_messages")
    fun observeDistinctContacts(): Flow<List<String>>

    @Query("""
        SELECT m.message_text, m.timestamp 
        FROM chat_messages m
        WHERE m.contact_name = :contactName 
          AND m.is_user_message = 1 
          AND (
            SELECT COALESCE(m2.normalized_text, m2.message_text)
            FROM chat_messages m2 
            WHERE m2.contact_name = m.contact_name 
              AND m2.is_user_message = 0 
              AND m2.timestamp < m.timestamp 
            ORDER BY m2.timestamp DESC LIMIT 1
          ) = :shortTrigger
    """)
    suspend fun getExactShortMessageReplies(contactName: String, shortTrigger: String): List<ShortMessageReplyCandidate>

    @Query("""
        SELECT m.message_text, m.timestamp 
        FROM chat_messages m
        WHERE m.contact_name = :contactName 
          AND m.is_user_message = 1 
          AND (
            SELECT COALESCE(m2.normalized_text, m2.message_text)
            FROM chat_messages m2 
            WHERE m2.contact_name = m.contact_name 
              AND m2.is_user_message = 0 
              AND m2.timestamp < m.timestamp 
            ORDER BY m2.timestamp DESC LIMIT 1
          ) LIKE '%' || :shortTrigger || '%'
    """)
    suspend fun getFuzzyShortMessageReplies(contactName: String, shortTrigger: String): List<ShortMessageReplyCandidate>
}
