package com.example.persona.features.contacts.domain.repository

import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.contacts.domain.model.ContactStatsSummary
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(query: String = "", tagFilter: ContactTag? = null): Flow<List<ContactEntity>>
    fun observeStatsSummary(): Flow<ContactStatsSummary>
    suspend fun getContactByName(contactName: String): ContactEntity?
    suspend fun getContactById(id: Long): ContactEntity?
    suspend fun upsertContact(contact: ContactEntity): Long
    suspend fun updateTag(id: Long, tag: ContactTag)
    suspend fun updateNotes(id: Long, notes: String)
    suspend fun updatePhone(id: Long, phone: String)
    suspend fun deleteContact(id: Long)
    suspend fun incrementOrderStats(contactName: String, amount: Double)
}
