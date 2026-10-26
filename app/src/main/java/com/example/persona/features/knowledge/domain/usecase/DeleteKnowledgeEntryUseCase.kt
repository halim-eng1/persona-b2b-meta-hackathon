package com.example.persona.features.knowledge.domain.usecase

import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import javax.inject.Inject

class DeleteKnowledgeEntryUseCase @Inject constructor(
    private val repository: KnowledgeRepository
) {
    suspend operator fun invoke(chunkId: String) {
        repository.deleteKnowledge(chunkId)
    }
}
