package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.persona.core.database.entity.ReplyQueueEntity
import kotlinx.datetime.Instant

@Dao
interface ReplyQueueDao {
    @Insert
    suspend fun insert(item: ReplyQueueEntity): Long

    @Query("SELECT * FROM reply_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingReplies(): List<ReplyQueueEntity>

    @Query("UPDATE reply_queue SET status = :status, attempt_count = attempt_count + 1, last_error = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("DELETE FROM reply_queue WHERE status = 'SENT'")
    suspend fun clearSentReplies()

    @Query("UPDATE reply_queue SET status = 'EXPIRED' WHERE status = 'PENDING' AND created_at < :cutoff")
    suspend fun expireOldReplies(cutoff: Instant)

    @Query("DELETE FROM reply_queue WHERE contact_display_name = :contactName AND status = 'PENDING'")
    suspend fun deletePendingRepliesForContact(contactName: String)
}
