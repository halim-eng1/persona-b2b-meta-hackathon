package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.persona.core.database.entity.LinkedContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface LinkedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: LinkedContactEntity): Long

    @Update
    suspend fun update(contact: LinkedContactEntity)

    @Query("SELECT * FROM linked_contacts WHERE is_active = 1")
    fun observeActiveContacts(): Flow<List<LinkedContactEntity>>

    @Query("SELECT * FROM linked_contacts WHERE whatsapp_display_name = :name LIMIT 1")
    suspend fun getByDisplayName(name: String): LinkedContactEntity?

    @Query("SELECT COUNT(*) FROM linked_contacts WHERE is_active = 1")
    suspend fun getActiveContactCount(): Int

    @Query("UPDATE linked_contacts SET cooldown_count = :count, cooldown_reset_at = :resetAt WHERE id = :contactId")
    suspend fun updateCooldown(contactId: Long, count: Int, resetAt: Instant?)
}
