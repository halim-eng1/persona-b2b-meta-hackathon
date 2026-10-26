package com.example.persona.core.database

import androidx.room.TypeConverter
import com.example.persona.core.database.entity.EmbeddingJobState
import com.example.persona.core.database.entity.OrderStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val buffer = ByteBuffer.allocate(value.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val buffer = ByteBuffer.wrap(value)
            .order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(value.size / 4) { buffer.getFloat() }
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        return Json.decodeFromString(value)
    }

    // ── OrderStatus ↔ String ──────────────────────────────────────────────────

    /**
     * Serialises [OrderStatus] to its [Enum.name] string for TEXT column storage.
     * Null input → null output (column stores SQL NULL).
     */
    @TypeConverter
    fun fromOrderStatus(value: OrderStatus?): String? = value?.name

    /**
     * Deserialises a TEXT column value back to [OrderStatus].
     * Unknown or corrupted values fall back safely to [OrderStatus.PENDING] via
     * [OrderStatus.fromString] — never throws.
     */
    @TypeConverter
    fun toOrderStatus(value: String?): OrderStatus = OrderStatus.fromString(value)

    // ── EmbeddingJobState ↔ String ────────────────────────────────────────────

    /**
     * Serialises [EmbeddingJobState] to its [Enum.name] string for TEXT column storage.
     * Null input → null output (column stores SQL NULL).
     */
    @TypeConverter
    fun fromEmbeddingJobState(value: EmbeddingJobState?): String? = value?.name

    /**
     * Deserialises a TEXT column value back to [EmbeddingJobState].
     * Unknown or corrupted values fall back safely to [EmbeddingJobState.PENDING] via
     * [EmbeddingJobState.fromString] — never throws.
     */
    @TypeConverter
    fun toEmbeddingJobState(value: String?): EmbeddingJobState = EmbeddingJobState.fromString(value)
}
