package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.persona.core.database.entity.ReplyLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface ReplyLogDao {

    @Insert
    suspend fun insert(log: ReplyLogEntity): Long

    @Query("SELECT * FROM reply_log WHERE contact_id = :contactId ORDER BY timestamp DESC LIMIT :limit")
    fun observeLogsForContact(contactId: Long, limit: Int = 50): Flow<List<ReplyLogEntity>>

    @Query("SELECT * FROM reply_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeAllLogs(limit: Int = 100): Flow<List<ReplyLogEntity>>

    @Delete
    suspend fun delete(log: ReplyLogEntity)

    @Query("DELETE FROM reply_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Instant)

    /**
     * Joins reply_log with linked_contacts to resolve contact display names.
     * Returns a raw projection used by [AuditLogRepositoryImpl] to build domain models.
     */
    @Query("""
        SELECT 
            rl.id            AS id,
            lc.whatsapp_display_name AS contactName,
            rl.incoming_message       AS incomingMessage,
            rl.generated_reply        AS generatedReply,
            rl.similarity_score       AS similarityScore,
            rl.was_auto_sent          AS wasAutoSent,
            rl.timestamp              AS timestamp
        FROM reply_log rl
        INNER JOIN linked_contacts lc ON lc.id = rl.contact_id
        ORDER BY rl.timestamp DESC
        LIMIT :limit
    """)
    fun observeLogsWithContact(limit: Int = 100): Flow<List<AuditLogProjection>>

    @Query("SELECT COUNT(*) FROM reply_log WHERE was_auto_sent = 1")
    fun observeAutoSentCount(): Flow<Int>
}
