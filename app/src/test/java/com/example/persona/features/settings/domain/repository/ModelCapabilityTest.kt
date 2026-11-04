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
        val config = ModelConfig.parse("muse-spark-1.3")
        assertEquals("muse-spark-1.3", config.modelId)
        assertEquals(setOf(ModelCapability.TEXT), config.capabilities)
    }

    @Test
    fun `parse new format with pipe separator`() {
        val config = ModelConfig.parse("muse-spark-1.3|TEXT,VISION")
        assertEquals("muse-spark-1.3", config.modelId)
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), config.capabilities)
    }

    @Test
    fun `parse AUDIO only model`() {
        val config = ModelConfig.parse("muse-voice-transcribe-1.0|AUDIO")
        assertEquals("muse-voice-transcribe-1.0", config.modelId)
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
        val original = ModelConfig("muse-voice-transcribe-1.0", setOf(ModelCapability.AUDIO))
        val parsed = ModelConfig.parse(original.serialize())
        assertEquals(original.modelId, parsed.modelId)
        assertEquals(original.capabilities, parsed.capabilities)
    }

    // ── ModelConfig.inferCapabilities ─────────────────────────────────────────

    @Test
    fun `inferCapabilities detects text vision and decision for muse spark`() {
        val caps = ModelConfig.inferCapabilities("muse-spark-1.3")
        assertEquals(
            setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION),
            caps
        )
    }

    @Test
    fun `inferCapabilities detects audio from voice and transcribe keywords`() {
        assertTrue(ModelCapability.AUDIO in ModelConfig.inferCapabilities("muse-voice-transcribe-1.0"))
        assertTrue(ModelCapability.AUDIO in ModelConfig.inferCapabilities("custom-asr-model"))
    }

    @Test
    fun `inferCapabilities detects vision from vl suffix`() {
        val caps = ModelConfig.inferCapabilities("custom-vl-72b")
        assertTrue(ModelCapability.VISION in caps)
        assertTrue(ModelCapability.TEXT in caps)
    }

    @Test
    fun `inferCapabilities detects vision from omni keyword`() {
        val caps = ModelConfig.inferCapabilities("omni-flash")
        assertTrue(ModelCapability.VISION in caps)
        assertTrue(ModelCapability.TEXT in caps)
    }

    @Test
    fun `inferCapabilities defaults to TEXT for plain text model`() {
        val caps = ModelConfig.inferCapabilities("plain-text-model")
        assertEquals(setOf(ModelCapability.TEXT), caps)
    }

    @Test
    fun `inferCapabilities is case insensitive`() {
        val caps = ModelConfig.inferCapabilities("MUSE-VOICE-TRANSCRIBE-1.0")
        assertTrue(ModelCapability.AUDIO in caps)
    }

    @Test
    fun `inferCapabilities detects DECISION for decision and gatekeeper models`() {
        assertEquals(setOf(ModelCapability.DECISION), ModelConfig.inferCapabilities("decision-router"))
        assertEquals(setOf(ModelCapability.DECISION), ModelConfig.inferCapabilities("custom-gatekeeper-v1"))
        assertEquals(setOf(ModelCapability.DECISION), ModelConfig.inferCapabilities("intent-decision-router"))
    }

    @Test
    fun `inferCapabilities does not falsely match unrelated numeric suffixes`() {
        assertEquals(setOf(ModelCapability.TEXT), ModelConfig.inferCapabilities("stabilityai/sd1.5"))
        assertEquals(setOf(ModelCapability.TEXT), ModelConfig.inferCapabilities("feed1"))
        assertEquals(setOf(ModelCapability.TEXT), ModelConfig.inferCapabilities("model-prod1"))
    }

    @Test
    fun `setFromString handles all four capabilities including DECISION`() {
        val result = ModelCapability.setFromString("TEXT,VISION,AUDIO,DECISION")
        assertEquals(
            setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO, ModelCapability.DECISION),
            result
        )
    }
}
