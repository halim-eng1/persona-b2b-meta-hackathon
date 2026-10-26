package com.example.persona.features.chat_import.data.parser

import com.example.persona.core.common.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a 3-tier recursive chunking strategy for Markdown and plain-text
 * business knowledge documents.
 *
 * **Algorithm overview:**
 *
 * 1. **Header splitting**: The document is split on Markdown headers (`#`, `##`, `###`).
 *    Each section inherits its full header ancestry as [KnowledgeChunk.headerContext].
 *
 * 2. **Size enforcement**: Sections exceeding [Constants.EMBEDDING_MAX_CHUNK_CHARS] are
 *    split recursively by paragraph (`\n\n`), then by sentence (`. `), then hard-split
 *    character-wise as a last resort.
 *
 * 3. **Overlap injection**: Adjacent sub-chunks share a trailing suffix / leading prefix
 *    equal to [Constants.CHUNK_OVERLAP_PERCENT] of [Constants.EMBEDDING_MAX_CHUNK_CHARS].
 *    This preserves cross-boundary semantic context during retrieval.
 */
@Singleton
class MarkdownChunker @Inject constructor() {

    companion object {
        /** Regex to detect Markdown header lines at any depth (# through ######). */
        private val HEADER_REGEX = Regex("^(#{1,6})\\s+(.+)$")

        /** Regex to detect Markdown horizontal rule lines (---, ***, ___). */
        private val HORIZONTAL_RULE_REGEX = Regex("^\\s*[-*_]{3,}\\s*$")

        /**
         * Detects media-only messages (images, videos, audio, documents) sent through WhatsApp.
         * These contain no retrievable text and should not be chunked.
         */
        private val MEDIA_OMITTED_REGEX = Regex(
            "(\\[?image omitted]?|\\[?video omitted]?|\\[?audio omitted]?|\\[?document omitted]?|" +
                "\\[?sticker omitted]?|\\[?GIF omitted]?)",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Chunks [documentText] into a flat [List<KnowledgeChunk>] ready for embedding.
     *
     * @param documentText  Raw file content (Markdown or plain text).
     * @param sourceFileName Original filename, forwarded to each chunk for provenance.
     * @param sourceType    The [BusinessKnowledgeEntity.sourceType] string.
     */
    fun chunk(
        documentText: String,
        sourceFileName: String? = null,
        sourceType: String = "CORE_DOCUMENT"
    ): List<KnowledgeChunk> {
        if (documentText.isBlank()) return emptyList()

        val cleaned = documentText.trim()
        val sections = splitByHeaders(cleaned)

        val result = mutableListOf<KnowledgeChunk>()
        for ((header, body) in sections) {
            if (body.isBlank()) continue
            val headerCtx = header?.trim()?.ifBlank { null }
            val subChunks = enforceSize(body.trim(), headerCtx)
            subChunks.forEach { text ->
                result += KnowledgeChunk(
                    chunkId = java.util.UUID.randomUUID().toString(),
                    headerContext = headerCtx,
                    chunkText = text,
                    sourceType = sourceType,
                    sourceFileName = sourceFileName
                )
            }
        }
        return result
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Header splitting
    // ──────────────────────────────────────────────────────────────────────────

    private data class HeadingBreadcrumb(val level: Int, val title: String)

    /**
     * Splits [text] into (headerBreadcrumb?, body) pairs.
     * Maintains a breadcrumb stack (e.g., "H1 - H2 - H3") and strips stray
     * horizontal rules (---) from chunk bodies.
     */
    private fun splitByHeaders(text: String): List<Pair<String?, String>> {
        val lines = text.lines()
        val sections = mutableListOf<Pair<String?, String>>()
        val headerStack = mutableListOf<HeadingBreadcrumb>()
        val currentBody = StringBuilder()

        for (line in lines) {
            val headerMatch = HEADER_REGEX.find(line.trim())
            if (headerMatch != null) {
                // Flush current section only if body is non-blank
                val body = currentBody.toString().trim()
                if (body.isNotEmpty()) {
                    val breadcrumb = if (headerStack.isEmpty()) null else headerStack.joinToString(" - ") { it.title }
                    sections += Pair(breadcrumb, body)
                }
                currentBody.clear()

                val level = headerMatch.groupValues[1].length
                val title = headerMatch.groupValues[2].trim()

                // Pop headings at or below the current depth
                while (headerStack.isNotEmpty() && headerStack.last().level >= level) {
                    headerStack.removeAt(headerStack.lastIndex)
                }
                headerStack.add(HeadingBreadcrumb(level, title))
            } else if (!HORIZONTAL_RULE_REGEX.matches(line)) {
                currentBody.appendLine(line)
            }
        }

        // Flush the last section only if body is non-blank
        val remaining = currentBody.toString().trim()
        if (remaining.isNotEmpty()) {
            val breadcrumb = if (headerStack.isEmpty()) null else headerStack.joinToString(" - ") { it.title }
            sections += Pair(breadcrumb, remaining)
        }

        return sections
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Recursive size enforcement
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Recursively splits [text] so that no resulting chunk exceeds
     * [Constants.EMBEDDING_MAX_CHUNK_CHARS], applying overlap between adjacent chunks.
     */
    private fun enforceSize(text: String, headerContext: String?): List<String> {
        val maxChars = Constants.EMBEDDING_MAX_CHUNK_CHARS
        val overlapChars = (maxChars * Constants.CHUNK_OVERLAP_PERCENT).toInt()

        // Base case: fits in one chunk
        if (text.length <= maxChars) return listOf(applyHeader(text, headerContext))

        // Tier 1: split by paragraph boundaries
        val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
        if (paragraphs.size > 1) {
            return mergeAndSplit(paragraphs, maxChars, overlapChars, headerContext)
        }

        // Tier 2: split by sentence boundaries (Arabic: "." / "؟" / "!")
        val sentences = text.split(Regex("(?<=[.؟!])\\s+")).filter { it.isNotBlank() }
        if (sentences.size > 1) {
            return mergeAndSplit(sentences, maxChars, overlapChars, headerContext)
        }

        // Tier 3: hard split by character limit with overlap
        return hardSplit(text, maxChars, overlapChars, headerContext)
    }

    /**
     * Greedily merges [units] into chunks up to [maxChars], injecting [overlapChars]
     * of trailing content from the previous chunk into the start of the next one.
     */
    private fun mergeAndSplit(
        units: List<String>,
        maxChars: Int,
        overlapChars: Int,
        headerContext: String?
    ): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (unit in units) {
            val candidate = if (current.isEmpty()) unit
                            else "${current}\n\n${unit}"

            if (candidate.length <= maxChars) {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(unit)
            } else {
                // Flush current chunk
                if (current.isNotEmpty()) {
                    chunks += applyHeader(current.toString().trim(), headerContext)
                    // Carry overlap into the next chunk
                    val overlap = current.toString().takeLast(overlapChars).trimStart()
                    current.clear()
                    if (overlap.isNotBlank()) {
                        current.append(overlap)
                        current.append("\n\n")
                    }
                }
                current.append(unit)
            }
        }
        if (current.isNotEmpty()) {
            val text = current.toString().trim()
            if (text.isNotBlank()) chunks += applyHeader(text, headerContext)
        }
        return chunks
    }

    /** Hard-splits [text] at [maxChars] boundaries with [overlapChars] overlap. */
    private fun hardSplit(
        text: String,
        maxChars: Int,
        overlapChars: Int,
        headerContext: String?
    ): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            chunks += applyHeader(text.substring(start, end).trim(), headerContext)
            start = maxOf(start + maxChars - overlapChars, start + 1)
        }
        return chunks
    }

    /**
     * If the text doesn't already begin with the header, prepends it as
     * "HeaderText: chunkText" for richer embedding context.
     */
    private fun applyHeader(text: String, headerContext: String?): String {
        if (headerContext.isNullOrBlank()) return text
        return if (text.startsWith(headerContext)) text
               else "$headerContext: $text"
    }
}

/**
 * Intermediate data class produced by [MarkdownChunker] before DB persistence.
 * Maps cleanly to [BusinessKnowledgeEntity] fields.
 */
data class KnowledgeChunk(
    val chunkId: String,
    val headerContext: String?,
    val chunkText: String,
    val sourceType: String,
    val sourceFileName: String?
)
