package com.example.persona.features.contacts.presentation

import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.features.contacts.domain.model.ContactStatsSummary
import com.example.persona.features.contacts.domain.repository.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val c1 = ContactEntity(
        id = 1L,
        contactName = "أحمد خالد",
        phoneNumber = "01011112222",
        tag = ContactTag.VIP.name,
        totalOrders = 3,
        totalSpent = 900.0
    )

    private val c2 = ContactEntity(
        id = 2L,
        contactName = "ياسر فؤاد",
        phoneNumber = "01233334444",
        tag = ContactTag.REGULAR.name,
        totalOrders = 1,
        totalSpent = 250.0
    )

    private val summary = ContactStatsSummary(
        totalContacts = 2,
        vipCount = 1,
        regularCount = 1,
        problematicCount = 0,
        blockedCount = 0,
        totalRevenue = 1150.0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { contactRepository.observeContacts(any(), any()) } returns flowOf(listOf(c1, c2))
        every { contactRepository.observeStatsSummary() } returns flowOf(summary)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state emits contacts and stats summary from repository`() = runTest(testDispatcher) {
        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.contacts.size)
        assertEquals(2, state.statsSummary.totalContacts)
        assertEquals(1, state.statsSummary.vipCount)
        assertEquals(1150.0, state.statsSummary.totalRevenue, 0.01)
    }

    @Test
    fun `search query update updates state and filters contacts`() = runTest(testDispatcher) {
        every { contactRepository.observeContacts(query = "أحمد", tagFilter = null) } returns flowOf(listOf(c1))

        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("أحمد")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("أحمد", state.searchQuery)
        assertEquals(1, state.contacts.size)
        assertEquals("أحمد خالد", state.contacts[0].contactName)
    }

    @Test
    fun `tag filter selection updates state and filters contacts`() = runTest(testDispatcher) {
        every { contactRepository.observeContacts(query = "", tagFilter = ContactTag.VIP) } returns flowOf(listOf(c1))

        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onTagFilterSelected(ContactTag.VIP)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ContactTag.VIP, state.selectedTagFilter)
        assertEquals(1, state.contacts.size)
        assertEquals(ContactTag.VIP.name, state.contacts[0].tag)
    }

    @Test
    fun `onUpdateContact calls repository to update tag, notes, and phone`() = runTest(testDispatcher) {
        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)
        viewModel.onSelectContactForEdit(c1)

        viewModel.onUpdateContact(
            id = 1L,
            newTag = ContactTag.PROBLEMATIC,
            newNotes = "مرتجعات متكررة",
            newPhone = "01099998888"
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { contactRepository.updateTag(1L, ContactTag.PROBLEMATIC) }
        coVerify(exactly = 1) { contactRepository.updateNotes(1L, "مرتجعات متكررة") }
        coVerify(exactly = 1) { contactRepository.updatePhone(1L, "01099998888") }
        assertNull(viewModel.uiState.value.selectedContactForEdit)
    }

    @Test
    fun `onUpdateContact supports DEBTOR and BLOCKED tag updates`() = runTest(testDispatcher) {
        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)

        viewModel.onUpdateContact(id = 2L, newTag = ContactTag.DEBTOR, newNotes = "مديونية 500 ج", newPhone = "01234")
        advanceUntilIdle()
        coVerify(exactly = 1) { contactRepository.updateTag(2L, ContactTag.DEBTOR) }

        viewModel.onUpdateContact(id = 2L, newTag = ContactTag.BLOCKED, newNotes = "محظور", newPhone = "01234")
        advanceUntilIdle()
        coVerify(exactly = 1) { contactRepository.updateTag(2L, ContactTag.BLOCKED) }
    }

    @Test
    fun `onSaveNewContact upserts new contact entity with trimmed values`() = runTest(testDispatcher) {
        val viewModel = ContactsViewModel(contactRepository, conversationMetadataDao)

        viewModel.onSaveNewContact(
            name = " كريم سعيد ",
            phone = " 01555556666 ",
            tag = ContactTag.VIP,
            notes = " عميل خاص "
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            contactRepository.upsertContact(
                match {
                    it.contactName == "كريم سعيد" &&
                    it.phoneNumber == "01555556666" &&
                    it.tag == ContactTag.VIP.name &&
                    it.notes == "عميل خاص"
                }
            )
        }
    }

    @Test
    fun `syncFromConversations imports conversations not yet in CRM`() = runTest(testDispatcher) {
        val conv = ConversationMetadataEntity(
            contactName = "نادر لطفي",
            sourceClass = "BUSINESS",
            lastMessageTimestamp = 12345L
        )
        every { conversationMetadataDao.observeAllConversations() } returns flowOf(listOf(conv))
        coEvery { contactRepository.getContactByName("نادر لطفي") } returns null

        ContactsViewModel(contactRepository, conversationMetadataDao)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            contactRepository.upsertContact(
                match { it.contactName == "نادر لطفي" }
            )
        }
    }
}
