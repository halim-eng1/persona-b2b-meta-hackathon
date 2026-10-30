package com.example.persona.features.contacts.data.repository

import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactRepositoryImplTest {

    private val contactDao = mockk<ContactDao>(relaxed = true)
    private lateinit var repository: ContactRepositoryImpl

    private val c1 = ContactEntity(
        id = 1L,
        contactName = "محمود حسن",
        phoneNumber = "01011112222",
        tag = ContactTag.VIP.name,
        totalOrders = 5,
        totalSpent = 1500.0
    )

    private val c2 = ContactEntity(
        id = 2L,
        contactName = "علي سامي",
        phoneNumber = "01122223333",
        tag = ContactTag.REGULAR.name,
        totalOrders = 1,
        totalSpent = 300.0
    )

    private val c3 = ContactEntity(
        id = 3L,
        contactName = "سالم كمال",
        phoneNumber = "01233334444",
        tag = ContactTag.PROBLEMATIC.name,
        totalOrders = 3,
        totalSpent = 800.0
    )

    private val c4 = ContactEntity(
        id = 4L,
        contactName = "حسام عماد",
        phoneNumber = "01544445555",
        tag = ContactTag.BLOCKED.name,
        totalOrders = 0,
        totalSpent = 0.0
    )

    @Before
    fun setUp() {
        repository = ContactRepositoryImpl(contactDao)
    }

    @Test
    fun `observeStatsSummary calculates totals and tag counts accurately`() = runTest {
        every { contactDao.observeAllContacts() } returns flowOf(listOf(c1, c2, c3, c4))

        val summary = repository.observeStatsSummary().first()

        assertEquals(4, summary.totalContacts)
        assertEquals(1, summary.vipCount)
        assertEquals(1, summary.regularCount)
        assertEquals(1, summary.problematicCount)
        assertEquals(1, summary.blockedCount)
        assertEquals(2600.0, summary.totalRevenue, 0.01)
    }

    @Test
    fun `observeContacts without filters delegates to observeAllContacts`() = runTest {
        every { contactDao.observeAllContacts() } returns flowOf(listOf(c1, c2))

        val list = repository.observeContacts().first()

        assertEquals(2, list.size)
        coVerify(exactly = 1) { contactDao.observeAllContacts() }
    }

    @Test
    fun `observeContacts with query and tag delegates to searchContactsWithTag`() = runTest {
        every { contactDao.searchContactsWithTag("محمود", "VIP") } returns flowOf(listOf(c1))

        val list = repository.observeContacts(query = "محمود", tagFilter = ContactTag.VIP).first()

        assertEquals(1, list.size)
        assertEquals("محمود حسن", list[0].contactName)
        coVerify(exactly = 1) { contactDao.searchContactsWithTag("محمود", "VIP") }
    }

    @Test
    fun `observeContacts with only tag delegates to observeContactsByTag`() = runTest {
        every { contactDao.observeContactsByTag("VIP") } returns flowOf(listOf(c1))

        val list = repository.observeContacts(tagFilter = ContactTag.VIP).first()

        assertEquals(1, list.size)
        coVerify(exactly = 1) { contactDao.observeContactsByTag("VIP") }
    }

    @Test
    fun `updateTag delegates to dao updateTag with enum name`() = runTest {
        repository.updateTag(1L, ContactTag.VIP)

        coVerify(exactly = 1) { contactDao.updateTag(1L, "VIP") }
    }

    @Test
    fun `updateNotes delegates to dao updateNotes`() = runTest {
        repository.updateNotes(2L, "عميل يفضل التوصيل مساءً")

        coVerify(exactly = 1) { contactDao.updateNotes(2L, "عميل يفضل التوصيل مساءً") }
    }

    @Test
    fun `updatePhone delegates to dao updatePhone`() = runTest {
        repository.updatePhone(3L, "01099998888")

        coVerify(exactly = 1) { contactDao.updatePhone(3L, "01099998888") }
    }

    @Test
    fun `deleteContact delegates to dao deleteContact`() = runTest {
        repository.deleteContact(4L)

        coVerify(exactly = 1) { contactDao.deleteContact(4L) }
    }
}
