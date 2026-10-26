package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single knowledge chunk in the global B2B knowledge base.
 *
 * This entity replaces the old contact-scoped [ChatMessageEntity]. Every chunk
 * produced by [MarkdownChunker] — as well as manually entered business facts —
 * lives here. There is no concept of "contact" at the knowledge level.
 *
 * [chunkId] is a client-generated UUID so that the Markdown chunker can create
 * a matching [EmbeddingIndexEntity] row in the same transaction without waiting
 * for a Room auto-increment insert to return an ID.
 */
@Entity(
    tableName = "business_knowledge",
    indices = [
        Index(value = ["source_type"]),
        Index(value = ["timestamp"])
    ]
)
data class BusinessKnowledgeEntity(
    /** Client-generated UUID — the primary key. */
    @PrimaryKey
    @ColumnInfo(name = "chunk_id")
    val chunkId: String,

    /**
     * The parent Markdown header chain prepended at retrieval time to preserve
     * semantic context (e.g. "الشحن والتوصيل > مناطق التوصيل").
     * Null for manual entries and flat-text documents with no headers.
     */
    @ColumnInfo(name = "header_context")
    val headerContext: String? = null,

    /** The actual text content of this chunk. Never blank. */
    @ColumnInfo(name = "chunk_text")
    val chunkText: String,

    /**
     * Logical origin of this chunk.
     *
     * Allowed values (not an enum so Room can store them as plain TEXT):
     *  - "CORE_DOCUMENT"   — produced by [MarkdownChunker] from an imported file
     *  - "MANUAL_ENTRY"    — typed directly by the business owner in the UI
     *  - "TEMPORARY_OFFER" — time-limited promotional text, displayed with a badge
     */
    @ColumnInfo(name = "source_type")
    val sourceType: String,

    /** Original filename (e.g. "product_catalog.md"). Null for manual/temp entries. */
    @ColumnInfo(name = "source_file_name")
    val sourceFileName: String? = null,

    /** Unix epoch milliseconds at insertion time. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
