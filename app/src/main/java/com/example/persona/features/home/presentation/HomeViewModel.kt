package com.example.persona.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.domain.model.AuditLogItem
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isAutoReplyEnabled: Boolean = false,
    val totalRepliesSent: Int = 0,
    val knowledgeChunkCount: Int = 0,
    val recentActivity: List<AuditLogItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val auditLogRepository: AuditLogRepository,
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val conversationMetadataDao: ConversationMetadataDao
) : ViewModel() {

    /**
     * Targeted log-to-contact resolution: resolves sourceClass only for the
     * at-most-3 recent audit logs instead of observing all database conversations.
     */
    private val recentLogsFlow = auditLogRepository.observeLogs(limit = 3).map { recentLogs ->
        if (recentLogs.isEmpty()) {
            emptyList()
        } else {
            val contactToSource = mutableMapOf<String, String>()
            for (log in recentLogs) {
                if (!contactToSource.containsKey(log.contactName)) {
                    val resolvedSource = conversationMetadataDao.getLatestConversationForContact(log.contactName)?.sourceClass
                        ?: log.sourceClass
                    contactToSource[log.contactName] = resolvedSource
                }
            }
            recentLogs.map { log ->
                val resolved = contactToSource[log.contactName] ?: log.sourceClass
                log.copy(sourceClass = resolved)
            }
        }
    }

    /**
     * Unified reactive dashboard state with lifecycle-aware subscription policy.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        userPreferencesRepository.isAutoReplyEnabled,
        auditLogRepository.observeReplyCount(),
        recentLogsFlow,
        businessKnowledgeDao.observeChunkCount()
    ) { isEnabled, replyCount, recentLogs, chunkCount ->
        HomeUiState(
            isAutoReplyEnabled = isEnabled,
            totalRepliesSent = replyCount,
            knowledgeChunkCount = chunkCount,
            recentActivity = recentLogs,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun setAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoReplyEnabled(enabled)
        }
    }
}
