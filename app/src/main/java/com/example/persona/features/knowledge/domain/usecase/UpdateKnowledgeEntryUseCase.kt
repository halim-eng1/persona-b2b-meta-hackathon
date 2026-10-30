package com.example.persona.features.knowledge.domain.usecase

import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import javax.inject.Inject

class UpdateKnowledgeEntryUseCase @Inject constructor(
    private val repository: KnowledgeRepository
) {
    suspend operator fun invoke(
        chunkId: String,
        newText: String,
        sourceType: String = "MANUAL_ENTRY",
        sourceFileName: String? = null,
        headerContext: String? = null
    ): Boolean {
        if (newText.isBlank()) return false
        return repository.updateKnowledge(
            chunkId = chunkId,
            newText = newText,
            sourceType = sourceType,
            sourceFileName = sourceFileName,
            headerContext = headerContext
        )
    }
}
