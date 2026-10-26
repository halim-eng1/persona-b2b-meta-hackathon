package com.example.persona.features.knowledge.domain.usecase

import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import javax.inject.Inject

class AddManualKnowledgeUseCase @Inject constructor(
    private val repository: KnowledgeRepository
) {
    suspend operator fun invoke(text: String, isTemporaryOffer: Boolean = false): Boolean {
        if (text.isBlank()) return false
        val sourceType = if (isTemporaryOffer) "TEMPORARY_OFFER" else "MANUAL_ENTRY"
        return repository.addManualKnowledge(text, sourceType)
    }
}
