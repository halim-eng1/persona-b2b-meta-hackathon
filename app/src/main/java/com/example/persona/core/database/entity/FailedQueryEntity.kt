package com.example.persona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists customer queries that the RAG pipeline could not answer with
 * sufficient confidence — the "Human-in-the-Loop" unresolved exchange queue.
 *
 * The business owner can view this list via the Knowledge Management UI,
 * use it to identify knowledge gaps, and add missing facts to [BusinessKnowledgeEntity].
 *
 * This table is NEVER auto-purged. The owner must explicitly mark items as reviewed.
 */
@Entity(
    tableName = "failed_unanswered_queries",
    indices = [
        Index(value = ["is_reviewed"]),
        Index(value = ["timestamp"])
    ]
)
data class FailedQueryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** WhatsApp display name of the customer who sent the unanswered query. */
    @ColumnInfo(name = "contact_display_name")
    val contactDisplayName: String,

    /** The original incoming message text, preserved verbatim. */
    @ColumnInfo(name = "query_text")
    val queryText: String,

    /**
     * The best similarity score we achieved before giving up.
     * Useful for the owner to understand how "close" the knowledge base was
     * to answering — a score of 0.30 means totally unknown, 0.68 means almost there.
     */
    @ColumnInfo(name = "best_score")
    val bestScore: Float,

    /** Unix epoch ms when this failure was recorded. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * True once the business owner has viewed and acknowledged this query in the UI.
     * Reviewed items can be shown in a collapsed "history" section or filtered out.
     */
    @ColumnInfo(name = "is_reviewed")
    val isReviewed: Boolean = false
)
