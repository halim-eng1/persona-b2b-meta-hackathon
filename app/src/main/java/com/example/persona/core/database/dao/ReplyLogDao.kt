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

    @Query("SELECT * FROM reply_log WHERE contact_display_name = :contactName ORDER BY timestamp DESC LIMIT :limit")
    fun observeLogsForContact(contactName: String, limit: Int = 50): Flow<List<ReplyLogEntity>>

    @Query("SELECT * FROM reply_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeAllLogs(limit: Int = 100): Flow<List<ReplyLogEntity>>

    @Delete
    suspend fun delete(log: ReplyLogEntity)

    @Query("DELETE FROM reply_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Instant)

    /**
     * Reads the audit log without any JOIN — contact name is stored directly
     * in the row since the B2B migration dropped the linked_contacts FK.
     */
    @Query("""
        SELECT
            id                    AS id,
            contact_display_name  AS contact_display_name,
            incoming_message      AS incoming_message,
            generated_reply       AS generated_reply,
            similarity_score      AS similarity_score,
            was_auto_sent         AS was_auto_sent,
            timestamp             AS timestamp,
            gatekeeper_tier       AS gatekeeper_tier,
            provider_model        AS provider_model,
            latency_ms            AS latency_ms,
            prompt_tokens         AS prompt_tokens,
            completion_tokens     AS completion_tokens,
            decision_reason       AS decision_reason
        FROM reply_log
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun observeLogsWithContact(limit: Int = 100): Flow<List<AuditLogProjection>>

    @Query("SELECT COUNT(*) FROM reply_log WHERE was_auto_sent = 1")
    fun observeAutoSentCount(): Flow<Int>
}
