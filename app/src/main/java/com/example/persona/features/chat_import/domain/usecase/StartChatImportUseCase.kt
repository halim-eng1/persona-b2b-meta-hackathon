package com.example.persona.features.chat_import.domain.usecase

import android.net.Uri
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Constants
import com.example.persona.core.common.Resource
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartChatImportUseCase @Inject constructor(
    private val repository: ChatImportRepository,
    private val linkedContactDao: LinkedContactDao
) {
    /**
     * Validates business rules before starting import:
     * - Check contact limit (Free: 1, Pro: 5)
     * Then delegates to repository.
     */
    suspend operator fun invoke(
        fileUri: Uri,
        userName: String? = null,
        maxContacts: Int = Constants.FREE_TIER_MAX_CONTACTS
    ): Resource<Flow<WorkInfo?>> {
        return try {
            val currentCount = linkedContactDao.getActiveContactCount()
            if (currentCount >= maxContacts) {
                Resource.Error(AppException.ContactLimitReached(maxContacts))
            } else {
                Resource.Success(repository.startImport(fileUri, userName))
            }
        } catch (e: Exception) {
            Resource.Error(AppException.DatabaseError("Get active contact count", e))
        }
    }
}
