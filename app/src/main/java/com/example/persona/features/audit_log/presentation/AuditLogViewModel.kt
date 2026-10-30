package com.example.persona.features.audit_log.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(
    val items: List<AuditLogItem> = emptyList(),
    val filteredItems: List<AuditLogItem> = emptyList(),
    val searchQuery: String = "",
    val selectedTier: String = "ALL", // ALL, RULE, HEURISTIC, MODEL, FALLBACK
    val selectedProvider: String = "ALL", // ALL, Gemini, Qwen, Nvidia, Groq
    val selectedStatus: String = "ALL", // ALL, SUCCESS, ESCALATED_HITL, ERROR
    val expandedItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val exportPayload: String? = null
)

private data class FilterState(
    val query: String,
    val tier: String,
    val provider: String,
    val status: String
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _rawItems = MutableStateFlow<List<AuditLogItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTier = MutableStateFlow("ALL")
    private val _selectedProvider = MutableStateFlow("ALL")
    private val _selectedStatus = MutableStateFlow("ALL")
    private val _expandedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _exportPayload = MutableStateFlow<String?>(null)

    private val filterStateFlow = combine(
        _searchQuery,
        _selectedTier,
        _selectedProvider,
        _selectedStatus
    ) { query, tier, provider, status ->
        FilterState(query, tier, provider, status)
    }

    val uiState: StateFlow<AuditLogUiState> = combine(
        _rawItems,
        _isLoading,
        filterStateFlow,
        _expandedItemIds,
        _exportPayload
    ) { rawItems, loading, filter, expandedIds, exportData ->
        val filtered = filterLogs(
            items = rawItems,
            query = filter.query,
            tier = filter.tier,
            provider = filter.provider,
            status = filter.status
        )
        AuditLogUiState(
            items = rawItems,
            filteredItems = filtered,
            searchQuery = filter.query,
            selectedTier = filter.tier,
            selectedProvider = filter.provider,
            selectedStatus = filter.status,
            expandedItemIds = expandedIds,
            isLoading = loading,
            exportPayload = exportData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuditLogUiState()
    )

    init {
        viewModelScope.launch {
            auditLogRepository.observeLogs(limit = 200).collect { items ->
                _rawItems.value = items
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onTierSelected(tier: String) {
        _selectedTier.value = tier
    }

    fun onProviderSelected(provider: String) {
        _selectedProvider.value = provider
    }

    fun onStatusSelected(status: String) {
        _selectedStatus.value = status
    }

    fun toggleExpand(id: Long) {
        _expandedItemIds.update { set ->
            if (set.contains(id)) set - id else set + id
        }
    }

    fun exportAuditTrail(format: String = "CSV"): String {
        val logs = uiState.value.filteredItems
        val output = if (format.equals("JSON", ignoreCase = true)) {
            buildJsonExport(logs)
        } else {
            buildCsvExport(logs)
        }
        _exportPayload.value = output
        return output
    }

    fun clearExportPayload() {
        _exportPayload.value = null
    }

    private fun filterLogs(
        items: List<AuditLogItem>,
        query: String,
        tier: String,
        provider: String,
        status: String
    ): List<AuditLogItem> {
        val cleanQuery = query.trim().lowercase()
        val normQuery = normalizeSearchText(cleanQuery)
        return items.filter { item ->
            val matchesQuery = cleanQuery.isEmpty() ||
                matchesSearch(item.contactName, cleanQuery, normQuery) ||
                matchesSearch(item.incomingMessage, cleanQuery, normQuery) ||
                matchesSearch(item.generatedReply, cleanQuery, normQuery)

            val matchesTier = tier == "ALL" || item.gatekeeperTier.equals(tier, ignoreCase = true)

            val matchesProvider = provider == "ALL" || item.providerModel.contains(provider, ignoreCase = true)

            val matchesStatus = status == "ALL" || item.status.equals(status, ignoreCase = true)

            matchesQuery && matchesTier && matchesProvider && matchesStatus
        }
    }

    private fun normalizeSearchText(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]"), "") // Tashkeel
            .replace("\u0640", "") // Tatweel
            .replace(Regex("[\u0622\u0623\u0625\u0671]"), "\u0627") // Alef variants (أ, إ, آ, ٱ) -> ا
            .replace('\u0629', '\u0647') // ة -> ه
            .replace('\u0649', '\u064A') // ى -> ي
            .replace('\u0624', '\u0648') // ؤ -> و
            .replace('\u0626', '\u064A') // ئ -> ي
            .replace("عاوز", "عايز")      // Egyptian dialect synonym: عاوز -> عايز
            .trim()
    }

    private fun matchesSearch(target: String, cleanQuery: String, normQuery: String): Boolean {
        if (target.contains(cleanQuery, ignoreCase = true)) return true
        if (normQuery.isEmpty()) return true
        val normTarget = normalizeSearchText(target)
        return normTarget.contains(normQuery)
    }

    private fun escapeCsv(str: String): String {
        return str
            .replace("\"", "\"\"")
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun buildCsvExport(logs: List<AuditLogItem>): String {
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Contact,Status,Tier,Model,LatencyMs,Similarity,IncomingMessage,GeneratedReply\n")
        for (item in logs) {
            val safeContact = escapeCsv(item.contactName)
            val safeIncoming = escapeCsv(item.incomingMessage)
            val safeReply = escapeCsv(item.generatedReply)
            sb.append("${item.id},\"${item.timestamp}\",\"$safeContact\",\"${item.status}\",\"${item.gatekeeperTier}\",\"${item.providerModel}\",${item.latencyMs},${item.similarityScore},\"$safeIncoming\",\"$safeReply\"\n")
        }
        return sb.toString()
    }

    private fun buildJsonExport(logs: List<AuditLogItem>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        logs.forEachIndexed { index, item ->
            val safeContact = escapeJson(item.contactName)
            val safeIncoming = escapeJson(item.incomingMessage)
            val safeReply = escapeJson(item.generatedReply)
            sb.append("  {\n")
            sb.append("    \"id\": ${item.id},\n")
            sb.append("    \"timestamp\": \"${item.timestamp}\",\n")
            sb.append("    \"contactName\": \"$safeContact\",\n")
            sb.append("    \"status\": \"${item.status}\",\n")
            sb.append("    \"gatekeeperTier\": \"${item.gatekeeperTier}\",\n")
            sb.append("    \"providerModel\": \"${item.providerModel}\",\n")
            sb.append("    \"latencyMs\": ${item.latencyMs},\n")
            sb.append("    \"promptTokens\": ${item.promptTokens},\n")
            sb.append("    \"completionTokens\": ${item.completionTokens},\n")
            sb.append("    \"similarityScore\": ${item.similarityScore},\n")
            sb.append("    \"incomingMessage\": \"$safeIncoming\",\n")
            sb.append("    \"generatedReply\": \"$safeReply\"\n")
            sb.append("  }")
            if (index < logs.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }
}
