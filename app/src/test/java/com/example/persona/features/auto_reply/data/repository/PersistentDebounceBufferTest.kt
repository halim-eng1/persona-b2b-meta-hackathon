package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PersistentDebounceBufferTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var buffer: PersistentDebounceBuffer

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("debounce_test").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
        buffer = PersistentDebounceBuffer(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `persistTurn writes turn to disk and recoverPendingTurns restores it after restart`() {
        val notification1 = WhatsAppNotification(
            title = "Kareem",
            text = "السلام عليكم",
            postTime = 1000L,
            notificationKey = "key_kareem_1",
            sourceClass = NotificationSourceClass.BUSINESS
        )
        val notification2 = WhatsAppNotification(
            title = "Kareem",
            text = "عايز اسأل عن الهودي",
            postTime = 1500L,
            notificationKey = "key_kareem_2",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        buffer.persistTurn(
            contactKey = "BUSINESS_kareem",
            notifications = listOf(notification1, notification2),
            insertedIds = listOf(101L, 102L),
            targetDeadlineMs = 5000L
        )

        // Simulate new app launch reading from disk
        val recoveredBuffer = PersistentDebounceBuffer(context)
        val recovered = recoveredBuffer.recoverPendingTurns()

        assertEquals(1, recovered.size)
        assertTrue(recovered.containsKey("BUSINESS_kareem"))

        val coalesced = recovered["BUSINESS_kareem"]!!
        assertEquals("Kareem", coalesced.title)
        assertTrue(coalesced.text.contains("السلام عليكم"))
        assertTrue(coalesced.text.contains("عايز اسأل عن الهودي"))
        assertTrue(coalesced.isPrePersisted)
        assertEquals(listOf(101L, 102L), coalesced.burstMessageIds)
    }

    @Test
    fun `removeTurn removes persisted turn from disk upon debounce flush`() {
        val notification = WhatsAppNotification(
            title = "Sara",
            text = "شكراً جزيلاً",
            postTime = 2000L,
            notificationKey = "key_sara_1",
            sourceClass = NotificationSourceClass.MAIN_APP
        )

        buffer.persistTurn(
            contactKey = "MAIN_APP_sara",
            notifications = listOf(notification),
            insertedIds = listOf(201L),
            targetDeadlineMs = 6000L
        )

        // Flush turn -> remove from disk
        buffer.removeTurn("MAIN_APP_sara")

        val recovered = buffer.recoverPendingTurns()
        assertTrue(recovered.isEmpty())
    }
}
