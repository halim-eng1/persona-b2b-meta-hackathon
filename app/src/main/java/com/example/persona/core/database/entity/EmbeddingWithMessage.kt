package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import kotlinx.datetime.Instant

data class EmbeddingWithMessage(
    val id: Long,
    @ColumnInfo(name = "message_id") val messageId: Long,
    val vector: FloatArray,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "message_text") val messageText: String,
    @ColumnInfo(name = "intent_category") val intentCategory: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingWithMessage) return false
        return id == other.id &&
                messageId == other.messageId &&
                vector.contentEquals(other.vector) &&
                modelVersion == other.modelVersion &&
                createdAt == other.createdAt &&
                messageText == other.messageText &&
                intentCategory == other.intentCategory
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + messageText.hashCode()
        result = 31 * result + intentCategory.hashCode()
        return result
    }
}
