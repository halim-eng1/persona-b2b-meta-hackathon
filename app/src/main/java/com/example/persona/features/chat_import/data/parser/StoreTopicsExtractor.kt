package com.example.persona.features.chat_import.data.parser

/**
 * Extracts the **Store Topics Index** from an imported knowledge document: the list
 * of Level-1 (`#`) markdown headings, in document order, de-duplicated.
 *
 * The resulting list is persisted in [com.example.persona.core.data.repository.UserPreferencesRepository]
 * and injected into the system prompt as the `<store_topics>` block, letting the LLM
 * reason about what the business covers without a retrieval round-trip.
 */
object StoreTopicsExtractor {

    /**
     * Returns all Level-1 headings (`# `) of [documentText], trimmed and de-duplicated.
     * Deeper levels (`##`, `###`, ...) and `#HashtagLike` tokens without a trailing
     * space are ignored. Safe on empty/blank input (returns an empty list).
     */
    fun extract(documentText: String): List<String> =
        documentText.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("# ") && !it.startsWith("##") }
            .map { it.removePrefix("# ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}
