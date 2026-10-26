package com.example.persona.features.chat_import.domain.usecase

import android.net.Uri
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Resource
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case that validates and delegates a knowledge base import.
 *
 * B2B migration: removed owner-name validation and contact-limit gating.
 * The only guard is that [fileUri] must be non-null and resolvable by the
 * system content resolver — the repository itself will fail gracefully if not.
 */
class StartChatImportUseCase @Inject constructor(
    private val repository: ChatImportRepository
) {
    suspend operator fun invoke(fileUri: Uri): Resource<Flow<WorkInfo?>> {
        return try {
            Resource.Success(repository.startImport(fileUri))
        } catch (e: Exception) {
            Resource.Error(AppException.DatabaseError("Start knowledge import", e))
        }
    }
}
