package com.example.persona.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.core.database.entity.EmbeddingIndexEntity

/**
 * Lightweight projection carrying only the fields needed for the in-memory
 * cosine-similarity sweep — avoids deserialising the full entity on every call.
 */
data class ChunkVectorProjection(
    @ColumnInfo(name = "chunk_id") val chunkId: String,
    @ColumnInfo(name = "vector_blob") val vectorBlob: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkVectorProjection) return false
        return chunkId == other.chunkId && vectorBlob.contentEquals(other.vectorBlob)
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + vectorBlob.contentHashCode()
        return result
    }
}

@Dao
interface EmbeddingIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<EmbeddingIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: EmbeddingIndexEntity)

    @Query("DELETE FROM embeddings_index WHERE chunk_id = :chunkId")
    suspend fun deleteByChunkId(chunkId: String)

    /** Full table wipe — used when re-embedding after a model upgrade. */
    @Query("DELETE FROM embeddings_index")
    suspend fun deleteAll()

    /**
     * Returns ALL vectors for the global dot-product sweep.
     * No contact filter — the B2B knowledge base is global.
     * Only pulls chunk_id + vector_blob to minimise deserialization cost.
     */
    @Query("SELECT chunk_id, vector_blob FROM embeddings_index")
    suspend fun getAllVectors(): List<ChunkVectorProjection>

    /**
     * Returns the chunk IDs that exist in [business_knowledge] but have no
     * corresponding row in [embeddings_index] — i.e. chunks awaiting embedding.
     */
    @Query("""
        SELECT bk.chunk_id
        FROM business_knowledge bk
        WHERE NOT EXISTS (
            SELECT 1 FROM embeddings_index ei WHERE ei.chunk_id = bk.chunk_id
        )
    """)
    suspend fun getPendingChunkIds(): List<String>

    @Query("SELECT COUNT(*) FROM embeddings_index")
    suspend fun getTotalEmbeddingCount(): Int
}
