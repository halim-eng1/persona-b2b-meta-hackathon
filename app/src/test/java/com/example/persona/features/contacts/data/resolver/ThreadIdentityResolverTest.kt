package com.example.persona.features.contacts.data.resolver

import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThreadIdentityResolverTest {

    private lateinit var contactDao: ContactDao
    private lateinit var conversationMetadataDao: ConversationMetadataDao
    private lateinit var chatHistoryDao: ChatHistoryDao
    private lateinit var orderDao: OrderDao
    private lateinit var resolver: ThreadIdentityResolver

    @Before
    fun setUp() {
        contactDao = mockk(relaxed = true)
        conversationMetadataDao = mockk(relaxed = true)
        chatHistoryDao = mockk(relaxed = true)
        orderDao = mockk(relaxed = true)

        resolver = ThreadIdentityResolver(
            contactDao = contactDao,
            conversationMetadataDao = conversationMetadataDao,
            chatHistoryDao = chatHistoryDao,
            orderDao = orderDao
        )
    }

    @Test
    fun `Tier 1 phone match merges raw phone contact into saved contact name`() = runBlocking {
        // Given existing contact saved previously under raw phone "+20 10 09251246"
        coEvery { contactDao.getContactByPhone("01009251246") } returns ContactEntity(
            id = 1,
            contactName = "+20 10 09251246",
            phoneNumber = "01009251246"
        )
        coEvery { chatHistoryDao.reassignContactMessages(any(), any()) } returns 5
        coEvery { orderDao.reassignContactOrders(any(), any()) } returns 1

        val notification = WhatsAppNotification(
            title = "My Number 2•",
            text = "ولا حاجة تسلم",
            postTime = 1000L,
            notificationKey = "0|com.whatsapp.w4b|1|tag123|10822",
            sourceClass = NotificationSourceClass.BUSINESS,
            sbnTag = "tag123",
            resolvedPhoneNumber = "01009251246"
        )

        val result = resolver.resolveAndMerge(notification)

        // Then unified contact name is the saved name "My Number 2•"
        assertEquals("My Number 2•", result.unifiedContactName)
        assertTrue(result.wasMerged)
        assertEquals("+20 10 09251246", result.previousContactName)

        // And chat history and orders are reassigned
        coVerify(exactly = 1) { chatHistoryDao.reassignContactMessages("+20 10 09251246", "My Number 2•") }
        coVerify(exactly = 1) { orderDao.reassignContactOrders("+20 10 09251246", "My Number 2•") }
        coVerify(exactly = 1) { conversationMetadataDao.mergeConversationContacts("+20 10 09251246", "My Number 2•", "BUSINESS") }
    }

    @Test
    fun `Tier 2 sbn tag match merges thread when phone is absent`() = runBlocking {
        // Given existing conversation with the exact same sbn.tag
        coEvery { contactDao.getContactByPhone(any()) } returns null
        coEvery { contactDao.getContactByPhoneContaining(any()) } returns null
        coEvery { conversationMetadataDao.getConversationByNotificationTag("VCVTNrBftCbRRDl") } returns ConversationMetadataEntity(
            contactName = "0100 925 1246",
            sourceClass = "BUSINESS",
            lastNotificationKey = "0|com.whatsapp.w4b|1|VCVTNrBftCbRRDl|10822"
        )

        val notification = WhatsAppNotification(
            title = "My Number 2•",
            text = "السلام عليكم",
            postTime = 2000L,
            notificationKey = "0|com.whatsapp.w4b|1|VCVTNrBftCbRRDl|10822",
            sourceClass = NotificationSourceClass.BUSINESS,
            sbnTag = "VCVTNrBftCbRRDl",
            resolvedPhoneNumber = null
        )

        val result = resolver.resolveAndMerge(notification)

        assertEquals("My Number 2•", result.unifiedContactName)
        assertTrue(result.wasMerged)
        assertEquals("0100 925 1246", result.previousContactName)

        coVerify(exactly = 1) { chatHistoryDao.reassignContactMessages("0100 925 1246", "My Number 2•") }
        coVerify(exactly = 1) { orderDao.reassignContactOrders("0100 925 1246", "My Number 2•") }
        coVerify(exactly = 1) { conversationMetadataDao.mergeConversationContacts("0100 925 1246", "My Number 2•", "BUSINESS") }
    }
}
