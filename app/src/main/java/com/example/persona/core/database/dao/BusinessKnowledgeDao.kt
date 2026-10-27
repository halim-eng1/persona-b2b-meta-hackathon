package com.example.persona.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lightweight projection for the in-memory dot-product sweep.
 * Only pulls chunk_id and chunk_text — avoids loading header_context
 * and metadata during the hot retrieval path.
 */
data class ChunkTextProjection(
    @ColumnInfo(name = "chunk_id") val chunkId: String,
    @ColumnInfo(name = "chunk_text") val chunkText: String,
    @ColumnInfo(name = "header_context") val headerContext: String?
)

@Dao
interface BusinessKnowledgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<BusinessKnowledgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: BusinessKnowledgeEntity)

    @Query("DELETE FROM business_knowledge WHERE chunk_id = :chunkId")
    suspend fun deleteByChunkId(chunkId: String)

    /**
     * Deletes all chunks from the business_knowledge table.
     */
    @Query("DELETE FROM business_knowledge")
    suspend fun deleteAll()

    /**
     * Bulk-delete by source type — used before a re-import to clear out
     * the existing CORE_DOCUMENT set without touching MANUAL_ENTRY rows.
     */
    @Query("DELETE FROM business_knowledge WHERE source_type = :sourceType")
    suspend fun deleteBySourceType(sourceType: String)

    /** Full observable list for the Knowledge Management UI. */
    @Query("SELECT * FROM business_knowledge ORDER BY timestamp DESC")
    fun observeAllChunks(): Flow<List<BusinessKnowledgeEntity>>

    @Query("SELECT * FROM business_knowledge WHERE source_type = :sourceType ORDER BY timestamp DESC")
    suspend fun getChunksBySourceType(sourceType: String): List<BusinessKnowledgeEntity>

    /** Returns chunk_id + texts without the full entity — used during retrieval metadata resolution. */
    @Query("SELECT chunk_id, chunk_text, header_context FROM business_knowledge WHERE chunk_id IN (:chunkIds)")
    suspend fun getChunkTextsByIds(chunkIds: List<String>): List<ChunkTextProjection>

    @Query("SELECT COUNT(*) FROM business_knowledge")
    suspend fun getChunkCount(): Int

    @Query("SELECT COUNT(*) FROM business_knowledge")
    fun observeChunkCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM business_knowledge WHERE source_type = 'CORE_DOCUMENT'")
    fun observeCoreDocumentCount(): Flow<Int>

    /**
     * Retrieves product catalog chunks directly by keyword matching to prevent catalog eviction.
     */
    @Query("""
        SELECT chunk_id, chunk_text, header_context 
        FROM business_knowledge 
        WHERE chunk_text LIKE '%بيانات المنتج%' 
           OR chunk_text LIKE '%اسم المنتج%' 
           OR header_context LIKE '%بيانات المنتج%' 
           OR header_context LIKE '%اسم المنتج%' 
        LIMIT :limit
    """)
    suspend fun getProductCatalogChunks(limit: Int = 5): List<ChunkTextProjection>
}
