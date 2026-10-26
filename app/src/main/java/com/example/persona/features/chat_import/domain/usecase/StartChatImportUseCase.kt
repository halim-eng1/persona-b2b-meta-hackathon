package com.example.persona.features.chat_import.domain.usecase

import android.net.Uri
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Constants
import com.example.persona.core.common.Resource
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartChatImportUseCase @Inject constructor(
    private val repository: ChatImportRepository,
    private val linkedContactDao: LinkedContactDao,
    private val prefsRepository: UserPreferencesRepository
) {
    /**
     * Validates business rules before starting import:
     * - Check contact limit (Free: 1, Pro: 5)
     * Then delegates to repository.
     */
    suspend operator fun invoke(
        fileUri: Uri,
        maxContacts: Int = Constants.FREE_TIER_MAX_CONTACTS
    ): Resource<Flow<WorkInfo?>> {
        val ownerName = prefsRepository.getOwnerNameSync()
        if (ownerName.isBlank()) {
            return Resource.Error(AppException.OwnerNameNotConfigured)
        }
        return try {
            val currentCount = linkedContactDao.getActiveContactCount()
            // TODO: Reimplement contact limit check in Phase 4. Currently commented out for flexible testing of tiers.
            /*
            if (currentCount >= maxContacts) {
                Resource.Error(AppException.ContactLimitReached(maxContacts))
            } else {
                Resource.Success(repository.startImport(fileUri))
            }
            */
            Resource.Success(repository.startImport(fileUri))
        } catch (e: Exception) {
            Resource.Error(AppException.DatabaseError("Get active contact count", e))
        }
    }
}
