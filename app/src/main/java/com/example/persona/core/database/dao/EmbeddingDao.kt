package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.EmbeddingEntity
import com.example.persona.core.database.entity.EmbeddingWithMessage

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<EmbeddingEntity>)

    @Query("""
        SELECT e.*, m.message_text, m.intent_type, m.topic_type, m.sentiment_type, m.context_confidence, m.timestamp AS message_timestamp
        FROM embeddings e
        INNER JOIN chat_messages m ON e.message_id = m.id
        WHERE m.contact_name = :contactName AND m.is_user_message = 1
    """)
    suspend fun getUserEmbeddingsForContact(contactName: String): List<EmbeddingWithMessage>

    @Query("DELETE FROM embeddings WHERE message_id IN (SELECT id FROM chat_messages WHERE contact_name = :contactName)")
    suspend fun deleteEmbeddingsForContact(contactName: String)

    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun getTotalEmbeddingCount(): Int
}
