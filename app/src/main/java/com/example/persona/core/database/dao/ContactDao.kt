package com.example.persona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity): Long

    @Query("SELECT * FROM contacts_table ORDER BY last_active_timestamp DESC")
    fun observeAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts_table WHERE contact_name = :contactName LIMIT 1")
    suspend fun getContactByName(contactName: String): ContactEntity?

    @Query("SELECT * FROM contacts_table WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactEntity?

    @Query("""
        SELECT * FROM contacts_table 
        WHERE contact_name LIKE '%' || :query || '%' OR phone_number LIKE '%' || :query || '%'
        ORDER BY last_active_timestamp DESC
    """)
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Query("""
        SELECT * FROM contacts_table 
        WHERE tag = :tag AND (contact_name LIKE '%' || :query || '%' OR phone_number LIKE '%' || :query || '%')
        ORDER BY last_active_timestamp DESC
    """)
    fun searchContactsWithTag(query: String, tag: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts_table WHERE tag = :tag ORDER BY last_active_timestamp DESC")
    fun observeContactsByTag(tag: String): Flow<List<ContactEntity>>

    @Query("UPDATE contacts_table SET tag = :tag WHERE id = :id")
    suspend fun updateTag(id: Long, tag: String)

    @Query("UPDATE contacts_table SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Query("UPDATE contacts_table SET phone_number = :phone WHERE id = :id")
    suspend fun updatePhone(id: Long, phone: String)

    @Query("""
        UPDATE contacts_table 
        SET total_orders = total_orders + 1, 
            total_spent = total_spent + :amount,
            last_active_timestamp = :timestamp
        WHERE contact_name = :contactName
    """)
    suspend fun incrementOrderStats(contactName: String, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM contacts_table WHERE id = :id")
    suspend fun deleteContact(id: Long)

    @Query("SELECT COUNT(*) FROM contacts_table")
    suspend fun getTotalCount(): Int
}
