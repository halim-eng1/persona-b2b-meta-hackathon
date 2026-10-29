package com.example.persona.features.audit_log.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(
    val items: List<AuditLogItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auditLogRepository.observeLogs(limit = 200).collect { items ->
                _uiState.value = AuditLogUiState(items = items, isLoading = false)
            }
        }
    }
}
