package com.example.persona.features.contacts.data.repository

import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.contacts.domain.model.ContactStatsSummary
import com.example.persona.features.contacts.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun observeContacts(query: String, tagFilter: ContactTag?): Flow<List<ContactEntity>> {
        val trimmedQuery = query.trim()
        return if (tagFilter != null && trimmedQuery.isNotBlank()) {
            contactDao.searchContactsWithTag(trimmedQuery, tagFilter.name)
        } else if (tagFilter != null) {
            contactDao.observeContactsByTag(tagFilter.name)
        } else if (trimmedQuery.isNotBlank()) {
            contactDao.searchContacts(trimmedQuery)
        } else {
            contactDao.observeAllContacts()
        }
    }

    override fun observeStatsSummary(): Flow<ContactStatsSummary> {
        return contactDao.observeAllContacts().map { contacts ->
            var vip = 0
            var regular = 0
            var debtor = 0
            var problematic = 0
            var blocked = 0
            var totalSpent = 0.0

            contacts.forEach { contact ->
                totalSpent += contact.totalSpent
                when (contact.tag) {
                    ContactTag.VIP.name -> vip++
                    ContactTag.DEBTOR.name -> debtor++
                    ContactTag.PROBLEMATIC.name -> problematic++
                    ContactTag.BLOCKED.name -> blocked++
                    else -> regular++
                }
            }

            ContactStatsSummary(
                totalContacts = contacts.size,
                vipCount = vip,
                regularCount = regular,
                debtorCount = debtor,
                problematicCount = problematic,
                blockedCount = blocked,
                totalRevenue = totalSpent
            )
        }
    }

    override suspend fun getContactByName(contactName: String): ContactEntity? =
        contactDao.getContactByName(contactName)

    override suspend fun getContactById(id: Long): ContactEntity? =
        contactDao.getContactById(id)

    override suspend fun upsertContact(contact: ContactEntity): Long =
        contactDao.upsert(contact)

    override suspend fun updateTag(id: Long, tag: ContactTag) =
        contactDao.updateTag(id, tag.name)

    override suspend fun updateNotes(id: Long, notes: String) =
        contactDao.updateNotes(id, notes)

    override suspend fun updatePhone(id: Long, phone: String) =
        contactDao.updatePhone(id, phone)

    override suspend fun deleteContact(id: Long) =
        contactDao.deleteContact(id)

    override suspend fun incrementOrderStats(contactName: String, amount: Double) =
        contactDao.incrementOrderStats(contactName, amount)
}
