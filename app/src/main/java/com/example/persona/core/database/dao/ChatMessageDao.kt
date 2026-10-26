package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE contact_name = :contactName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesForContact(contactName: String, limit: Int = 100): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE is_user_message = 1 AND contact_name = :contactName")
    suspend fun getUserMessagesForContact(contactName: String): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE contact_name = :contactName")
    suspend fun getMessageCountForContact(contactName: String): Int

    @Query("DELETE FROM chat_messages WHERE contact_name = :contactName")
    suspend fun deleteMessagesForContact(contactName: String)

    @Query("SELECT DISTINCT contact_name FROM chat_messages")
    fun observeDistinctContacts(): Flow<List<String>>
}
