package com.example.persona.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterChatMessageSerializerTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `text-only message serializes as flat string content`() {
        val message = OpenRouterChatMessage(role = "user", content = "بكام ده؟")
        val serialized = json.encodeToString(OpenRouterChatMessage.serializer(), message)

        assertTrue(serialized.contains(""""role":"user""""))
        assertTrue(serialized.contains(""""content":"بكام ده؟""""))
        // Must NOT serialize as an array
        org.junit.Assert.assertFalse(serialized.contains(""""image_url""""))
    }

    @Test
    fun `multimodal vision message serializes with text and image_url parts`() {
        val message = OpenRouterChatMessage(
            role = "user",
            content = "بكام ده؟",
            imageUrl = "data:image/jpeg;base64,ABCDEF123456"
        )
        val serialized = json.encodeToString(OpenRouterChatMessage.serializer(), message)

        assertTrue(serialized.contains(""""role":"user""""))
        assertTrue(serialized.contains(""""type":"text""""))
        assertTrue(serialized.contains(""""text":"بكام ده؟""""))
        assertTrue(serialized.contains(""""type":"image_url""""))
        assertTrue(serialized.contains(""""url":"data:image/jpeg;base64,ABCDEF123456""""))
    }

    @Test
    fun `deserializing flat string content produces valid ChatMessage without imageUrl`() {
        val raw = """{"role":"assistant","content":"سعر التيشيرت 350 جنيه"}"""
        val deserialized = json.decodeFromString(OpenRouterChatMessage.serializer(), raw)

        assertEquals("assistant", deserialized.role)
        assertEquals("سعر التيشيرت 350 جنيه", deserialized.content)
        assertNull(deserialized.imageUrl)
    }

    @Test
    fun `deserializing multimodal array content extracts text and imageUrl correctly`() {
        val raw = """
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "هل متوفر هذا المنتج؟"},
                    {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,xyz987"}}
                ]
            }
        """.trimIndent()

        val deserialized = json.decodeFromString(OpenRouterChatMessage.serializer(), raw)

        assertEquals("user", deserialized.role)
        assertEquals("هل متوفر هذا المنتج؟", deserialized.content)
        assertEquals("data:image/jpeg;base64,xyz987", deserialized.imageUrl)
    }
}
