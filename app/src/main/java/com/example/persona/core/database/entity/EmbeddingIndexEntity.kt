package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the embedding vector for a single [BusinessKnowledgeEntity] chunk.
 *
 * Key design differences from the old [EmbeddingEntity]:
 *  - 1:1 relationship: the PK here *is* the FK (chunkId). No auto-increment.
 *  - ON DELETE CASCADE: deleting a knowledge chunk automatically purges its vector.
 *  - [modelVersion] allows safe cache-busting if we ever upgrade the ONNX model
 *    to a different architecture without a full destructive migration.
 */
@Entity(
    tableName = "embeddings_index",
    foreignKeys = [
        ForeignKey(
            entity = BusinessKnowledgeEntity::class,
            parentColumns = ["chunk_id"],
            childColumns = ["chunk_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chunk_id"], unique = true)]
)
data class EmbeddingIndexEntity(
    /** Matches [BusinessKnowledgeEntity.chunkId] — no separate auto-increment ID. */
    @PrimaryKey
    @ColumnInfo(name = "chunk_id")
    val chunkId: String,

    /**
     * 384-dimensional float embedding from MiniLM-L12-v2-int8 serialised to BLOB
     * via the existing [com.example.persona.core.database.Converters] functions.
     */
    @ColumnInfo(name = "vector_blob", typeAffinity = ColumnInfo.BLOB)
    val vectorBlob: FloatArray,

    /**
     * Identifies the model that produced this embedding (e.g. "minilm-l12-v2-int8-1.0").
     * Stored so we can selectively invalidate vectors on model upgrade.
     */
    @ColumnInfo(name = "model_version")
    val modelVersion: String = "minilm-l12-v2-int8-1.0"
) {
    // FloatArray breaks structural equality by default — override explicitly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingIndexEntity) return false
        return chunkId == other.chunkId &&
            vectorBlob.contentEquals(other.vectorBlob) &&
            modelVersion == other.modelVersion
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        result = 31 * result + modelVersion.hashCode()
        return result
    }
}
