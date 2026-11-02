package com.example.persona.features.knowledge.presentation.components

import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.knowledge.presentation.model.ExecutionTrace
import com.example.persona.features.knowledge.presentation.model.SimulatorMediaType
import com.example.persona.features.knowledge.presentation.model.SimulatorMessage
import com.example.persona.features.knowledge.presentation.model.SimulatorSender
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class BotSimulatorComponentTest {

    @Test
    fun `canSendSimulatorTurn returns false when simulation is in progress`() {
        assertFalse(canSendSimulatorTurn(inputText = "كم سعر المنتج؟", mediaUri = null, isSimulating = true))
        assertFalse(canSendSimulatorTurn(inputText = "", mediaUri = "content://media/img.jpg", isSimulating = true))
        assertFalse(canSendSimulatorTurn(inputText = "سؤال", mediaUri = "content://media/img.jpg", isSimulating = true))
    }

    @Test
    fun `canSendSimulatorTurn returns false when both text and media are blank or null`() {
        assertFalse(canSendSimulatorTurn(inputText = "", mediaUri = null, isSimulating = false))
        assertFalse(canSendSimulatorTurn(inputText = "   ", mediaUri = null, isSimulating = false))
        assertFalse(canSendSimulatorTurn(inputText = "   ", mediaUri = "   ", isSimulating = false))
    }

    @Test
    fun `canSendSimulatorTurn returns true when text is non-blank even if media is null`() {
        assertTrue(canSendSimulatorTurn(inputText = "متاح مقاس L؟", mediaUri = null, isSimulating = false))
        assertTrue(canSendSimulatorTurn(inputText = " أ ", mediaUri = null, isSimulating = false))
    }

    @Test
    fun `canSendSimulatorTurn returns true when media is present even if text is empty`() {
        assertTrue(canSendSimulatorTurn(inputText = "", mediaUri = "content://media/photo.jpg", isSimulating = false))
        assertTrue(canSendSimulatorTurn(inputText = "  ", mediaUri = "content://media/voice.m4a", isSimulating = false))
    }

    @Test
    fun `formatSimulatorTime formats timestamp with am pm indicators correctly`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = formatSimulatorTime(calendar.timeInMillis, Locale.ENGLISH)
        assertTrue(formatted.contains("02:30") || formatted.contains("2:30"))
        assertTrue(formatted.lowercase().contains("pm"))
    }

    @Test
    fun `SimulatorMessage correctly models user query with media`() {
        val userMsg = SimulatorMessage(
            sender = SimulatorSender.USER,
            text = "هل ده متاح عندكم؟",
            mediaUri = "content://media/pic1.jpg",
            mediaType = SimulatorMediaType.IMAGE
        )

        assertEquals(SimulatorSender.USER, userMsg.sender)
        assertEquals("هل ده متاح عندكم؟", userMsg.text)
        assertEquals("content://media/pic1.jpg", userMsg.mediaUri)
        assertEquals(SimulatorMediaType.IMAGE, userMsg.mediaType)
        assertNull(userMsg.trace)
    }

    @Test
    fun `SimulatorMessage correctly models bot response with ExecutionTrace`() {
        val candidate = RetrievalCandidate(
            chunkId = "chunk_101",
            headerContext = "الملابس الشتوية",
            chunkText = "[منتج]: تيشرت أوفر سايز | [السعر]: 220 ج.م",
            similarityScore = 0.88f
        )
        val trace = ExecutionTrace(
            totalDurationMs = 120L,
            intent = InboundIntent.CATALOG_INQUIRY,
            intentSourceTier = "On-Device Gatekeeper",
            intentConfidence = 0.94f,
            ragCandidates = listOf(candidate),
            ragBestScore = 0.88f,
            llmProvider = "Local-Edge-Model"
        )
        val botMsg = SimulatorMessage(
            sender = SimulatorSender.BOT,
            text = "نعم متوفر بسعر 220 ج.م",
            trace = trace
        )

        assertEquals(SimulatorSender.BOT, botMsg.sender)
        assertNotNull(botMsg.trace)
        assertEquals(InboundIntent.CATALOG_INQUIRY, botMsg.trace?.intent)
        assertEquals(0.88f, botMsg.trace?.ragBestScore)
        assertEquals(1, botMsg.trace?.ragCandidates?.size)
    }
}
