package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.persona.core.database.entity.FailedQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FailedQueryDao {

    @Insert
    suspend fun insert(entity: FailedQueryEntity): Long

    /** Observe all UNREVIEWED queries — shown as the active inbox for the business owner. */
    @Query("SELECT * FROM failed_unanswered_queries WHERE is_reviewed = 0 ORDER BY timestamp DESC")
    fun observeUnreviewedQueries(): Flow<List<FailedQueryEntity>>

    /** Observe all queries (for a full history view). */
    @Query("SELECT * FROM failed_unanswered_queries ORDER BY timestamp DESC")
    fun observeAllQueries(): Flow<List<FailedQueryEntity>>

    @Query("UPDATE failed_unanswered_queries SET is_reviewed = 1 WHERE id = :id")
    suspend fun markReviewed(id: Long)

    @Query("UPDATE failed_unanswered_queries SET is_reviewed = 1")
    suspend fun markAllReviewed()

    @Query("SELECT COUNT(*) FROM failed_unanswered_queries WHERE is_reviewed = 0")
    fun observeUnreviewedCount(): Flow<Int>

    @Query("DELETE FROM failed_unanswered_queries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM failed_unanswered_queries WHERE is_reviewed = 1")
    suspend fun clearReviewed()
}
