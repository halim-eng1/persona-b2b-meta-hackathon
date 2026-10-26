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
}
