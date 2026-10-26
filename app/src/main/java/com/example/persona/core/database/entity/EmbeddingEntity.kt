package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["message_id"], unique = true)]
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "vector", typeAffinity = ColumnInfo.BLOB) val vector: FloatArray,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant
) {
    companion object {
        const val CURRENT_MODEL_VERSION = "minilm-l12-v2-int8-1.0"
        const val EMBEDDING_DIM = 384
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id &&
                messageId == other.messageId &&
                vector.contentEquals(other.vector) &&
                modelVersion == other.modelVersion &&
                createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
