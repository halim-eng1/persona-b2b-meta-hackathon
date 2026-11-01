package com.example.persona.features.settings.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityTest {

    // ── ModelCapability.setFromString / setToString ────────────────────────────

    @Test
    fun `setFromString parses single capability`() {
        val result = ModelCapability.setFromString("TEXT")
        assertEquals(setOf(ModelCapability.TEXT), result)
    }

    @Test
    fun `setFromString parses multiple capabilities`() {
        val result = ModelCapability.setFromString("TEXT,VISION")
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), result)
    }

    @Test
    fun `setFromString handles all three capabilities`() {
        val result = ModelCapability.setFromString("TEXT,VISION,AUDIO")
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO), result)
    }

    @Test
    fun `setFromString defaults to TEXT on empty or unknown input`() {
        assertEquals(setOf(ModelCapability.TEXT), ModelCapability.setFromString(""))
        assertEquals(setOf(ModelCapability.TEXT), ModelCapability.setFromString("UNKNOWN"))
    }

    @Test
    fun `setToString produces comma separated keys`() {
        val caps = setOf(ModelCapability.TEXT, ModelCapability.VISION)
        val str = ModelCapability.setToString(caps)
        // Order may vary — just check both present
        assertTrue(str.contains("TEXT"))
        assertTrue(str.contains("VISION"))
        assertTrue(str.contains(","))
    }

    @Test
    fun `setFromString and setToString roundtrip`() {
        val original = setOf(ModelCapability.TEXT, ModelCapability.AUDIO)
        val roundtripped = ModelCapability.setFromString(ModelCapability.setToString(original))
        assertEquals(original, roundtripped)
    }

    // ── ModelConfig.parse ──────────────────────────────────────────────────────

    @Test
    fun `parse legacy format with no pipe defaults to TEXT`() {
        val config = ModelConfig.parse("qwen3.8-max")
        assertEquals("qwen3.8-max", config.modelId)
        assertEquals(setOf(ModelCapability.TEXT), config.capabilities)
    }

    @Test
    fun `parse new format with pipe separator`() {
        val config = ModelConfig.parse("qwen3-vl|TEXT,VISION")
        assertEquals("qwen3-vl", config.modelId)
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), config.capabilities)
    }

    @Test
    fun `parse AUDIO only model`() {
        val config = ModelConfig.parse("fun-asr-flash-2026-06-15|AUDIO")
        assertEquals("fun-asr-flash-2026-06-15", config.modelId)
        assertEquals(setOf(ModelCapability.AUDIO), config.capabilities)
    }

    @Test
    fun `parse trims whitespace in modelId and capabilities`() {
        val config = ModelConfig.parse("  my-model | TEXT , VISION  ")
        assertEquals("my-model", config.modelId)
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), config.capabilities)
    }

    // ── ModelConfig.serialize ──────────────────────────────────────────────────

    @Test
    fun `serialize and parse roundtrip preserves modelId and capabilities`() {
        val original = ModelConfig("whisper-large-v3", setOf(ModelCapability.AUDIO))
        val parsed = ModelConfig.parse(original.serialize())
        assertEquals(original.modelId, parsed.modelId)
        assertEquals(original.capabilities, parsed.capabilities)
    }

    // ── ModelConfig.inferCapabilities ─────────────────────────────────────────

    @Test
    fun `inferCapabilities detects audio from whisper`() {
        val caps = ModelConfig.inferCapabilities("whisper-large-v3")
        assertTrue(ModelCapability.AUDIO in caps)
    }

    @Test
    fun `inferCapabilities detects audio from asr keyword`() {
        val caps = ModelConfig.inferCapabilities("fun-asr-flash-2026-06-15")
        assertTrue(ModelCapability.AUDIO in caps)
    }

    @Test
    fun `inferCapabilities detects vision from vl suffix`() {
        val caps = ModelConfig.inferCapabilities("qwen3-vl-72b")
        assertTrue(ModelCapability.VISION in caps)
        assertTrue(ModelCapability.TEXT in caps)
    }

    @Test
    fun `inferCapabilities detects vision from omni keyword`() {
        val caps = ModelConfig.inferCapabilities("qwen3.8-omni-flash")
        assertTrue(ModelCapability.VISION in caps)
        assertTrue(ModelCapability.TEXT in caps)
    }

    @Test
    fun `inferCapabilities defaults to TEXT for plain text model`() {
        val caps = ModelConfig.inferCapabilities("qwen3.8-max")
        assertEquals(setOf(ModelCapability.TEXT), caps)
    }

    @Test
    fun `inferCapabilities is case insensitive`() {
        val caps = ModelConfig.inferCapabilities("WHISPER-LARGE")
        assertTrue(ModelCapability.AUDIO in caps)
    }
}

