package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.core.database.entity.ChatHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntiEchoGuardTest {

    private lateinit var guard: AntiEchoGuard

    @Before
    fun setUp() {
        guard = AntiEchoGuard()
    }

    @Test
    fun `rolling capacity retains 100 replies per contact and evicts oldest on 101st`() {
        val contact = "customer_vip_1"

        // Insert 100 replies
        for (i in 1..100) {
            guard.addReplyToCache("Reply message number $i", contact)
        }

        var activeHashes = guard.getActiveReplyHashes(contact)
        assertEquals(100, activeHashes.size)
        assertTrue(activeHashes.contains("Reply message number 1".hashCode()))
        assertTrue(activeHashes.contains("Reply message number 100".hashCode()))

        // Insert 101st reply -> message 1 should be evicted
        guard.addReplyToCache("Reply message number 101", contact)

        activeHashes = guard.getActiveReplyHashes(contact)
        assertEquals(100, activeHashes.size)
        assertFalse(activeHashes.contains("Reply message number 1".hashCode()))
        assertTrue(activeHashes.contains("Reply message number 2".hashCode()))
        assertTrue(activeHashes.contains("Reply message number 101".hashCode()))
    }

    @Test
    fun `replies for Contact A do not evict or affect Contact B`() {
        val contactA = "contact_a"
        val contactB = "contact_b"

        guard.addReplyToCache("Hello Contact A", contactA)
        guard.addReplyToCache("Hello Contact B", contactB)

        val hashesA = guard.getActiveReplyHashes(contactA)
        val hashesB = guard.getActiveReplyHashes(contactB)

        assertEquals(1, hashesA.size)
        assertEquals(1, hashesB.size)
        assertTrue(hashesA.contains("Hello Contact A".hashCode()))
        assertFalse(hashesA.contains("Hello Contact B".hashCode()))
        assertTrue(hashesB.contains("Hello Contact B".hashCode()))
        assertFalse(hashesB.contains("Hello Contact A".hashCode()))
    }

    @Test
    fun `backward compatible global cache works when contactKey is omitted`() {
        guard.addReplyToCache("Global reply without contact key")
        val activeGlobal = guard.getActiveReplyHashes()
        assertTrue(activeGlobal.contains("Global reply without contact key".hashCode()))
    }

    @Test
    fun `isEchoRepetition detects identical assistant reply within 2 hours`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            ChatHistoryEntity(
                id = 2L,
                contactName = "user_1",
                messageText = "صباح الخير يا فندم! أقدر أساعدك إزاي؟",
                timestamp = now - 60_000L, // 1 min ago
                isFromUser = false,
                sourceClass = "BUSINESS"
            ),
            ChatHistoryEntity(
                id = 1L,
                contactName = "user_1",
                messageText = "صباح الخير",
                timestamp = now - 120_000L,
                isFromUser = true,
                sourceClass = "BUSINESS"
            )
        )

        val isEcho = AntiEchoGuard.isEchoRepetition(
            candidateReply = "صباح الخير يا فندم! أقدر أساعدك إزاي؟",
            currentUserText = "ممكن الأسعار؟",
            chatHistory = history,
            nowMs = now
        )

        assertTrue(isEcho)
    }

    @Test
    fun `isEchoRepetition allows identical reply if user repeated their own message verbatim`() {
        val now = System.currentTimeMillis()
        val history = listOf(
            ChatHistoryEntity(
                id = 2L,
                contactName = "user_1",
                messageText = "السعر 250 جنيه",
                timestamp = now - 60_000L,
                isFromUser = false,
                sourceClass = "BUSINESS"
            ),
            ChatHistoryEntity(
                id = 1L,
                contactName = "user_1",
                messageText = "بكام الهودي؟",
                timestamp = now - 120_000L,
                isFromUser = true,
                sourceClass = "BUSINESS"
            )
        )

        // Customer repeated "بكام الهودي؟" verbatim
        val isEcho = AntiEchoGuard.isEchoRepetition(
            candidateReply = "السعر 250 جنيه",
            currentUserText = "بكام الهودي؟",
            chatHistory = history,
            nowMs = now
        )

        // Must not be flagged as echo so customer gets the answer again
        assertFalse(isEcho)
    }
}
