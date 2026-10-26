package com.example.persona.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.domain.model.AuditLogItem
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine toggle state, reply count, recent 3 logs, and knowledge chunk count into a single coherent state
            combine(
                userPreferencesRepository.isAutoReplyEnabled,
                auditLogRepository.observeReplyCount(),
                auditLogRepository.observeLogs(limit = 3),
                businessKnowledgeDao.observeChunkCount(),
                conversationMetadataDao.observeAllConversations()
            ) { isEnabled, replyCount, recentLogs, chunkCount, conversations ->
                val contactToSource = conversations.associate { it.contactName to it.sourceClass }
                val mappedLogs = recentLogs.map { log ->
                    val resolvedSource = contactToSource[log.contactName] ?: log.sourceClass
                    log.copy(sourceClass = resolvedSource)
                }
                HomeUiState(
                    isAutoReplyEnabled = isEnabled,
                    totalRepliesSent = replyCount,
                    knowledgeChunkCount = chunkCount,
                    recentActivity = mappedLogs,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoReplyEnabled(enabled)
        }
    }
}
