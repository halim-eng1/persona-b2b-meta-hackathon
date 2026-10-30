package com.example.persona.features.home.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class SystemHealthStatus {
    ACTIVE,             // Enabled, permission granted, service running in OS
    PAUSED,             // Merchant deliberately toggled it off
    PERMISSION_MISSING, // Notification listener permission revoked in Android settings
    SERVICE_KILLED      // Enabled & permitted, but OS killed service in background
}

data class WaitingCustomer(
    val contactName: String,
    val sourceClass: String,
    val lastMessage: String,
    val timestamp: Long
)

data class HomeUiState(
    val isAutoReplyEnabled: Boolean = false,
    val systemHealth: SystemHealthStatus = SystemHealthStatus.PAUSED,
    val pendingAttentionCount: Int = 0,
    val waitingCustomers: List<WaitingCustomer> = emptyList(),
    // Daily B2B Operational Metrics:
    val todayOrdersCount: Int = 0,
    val todayPendingRevenue: Double = 0.0,
    val waitingCustomersCount: Int = 0,
    val todayRepliesCount: Int = 0,
    val totalRepliesSent: Int = 0,
    val knowledgeChunkCount: Int = 0,
    val recentActivity: List<AuditLogItem> = emptyList(),
    val isLoading: Boolean = true
)

private data class B2BMetrics(
    val todayOrders: Int,
    val pendingRevenue: Double,
    val totalReplies: Int,
    val todayReplies: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val toggleAutoReplyUseCase: ToggleAutoReplyUseCase,
    private val auditLogRepository: AuditLogRepository,
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val orderDao: OrderDao,
    private val permissionChecker: PermissionChecker
) : ViewModel() {

    // Overridable clock and service checker for testing
    var clockEpochMillis: () -> Long = { System.currentTimeMillis() }
    var serviceAliveChecker: () -> Boolean = {
        WhatsAppNotificationListener.instance?.get() != null
    }

    private val _healthRefreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun refreshHealth() {
        _healthRefreshTrigger.value = System.currentTimeMillis()
    }

    private val autoReplyHealthFlow = combine(
        userPreferencesRepository.isAutoReplyEnabled,
        _healthRefreshTrigger
    ) { isEnabled, _ ->
        isEnabled
    }

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

    private val attentionFlow = combine(
        conversationMetadataDao.observeTotalAttentionCount(),
        conversationMetadataDao.observeNeedsAttentionConversations()
    ) { count, waitingList ->
        val mappedCustomers = waitingList.take(5).map { entity ->
            WaitingCustomer(
                contactName = entity.contactName,
                sourceClass = entity.sourceClass,
                lastMessage = entity.lastMessageText ?: "",
                timestamp = entity.lastMessageTimestamp
            )
        }
        Pair(count, mappedCustomers)
    }

    private val ordersMetricFlow = orderDao.observeAllOrders().map { allOrders ->
        val startOfToday = getStartOfTodayEpochMillis()
        val todayOrders = allOrders.filter { it.timestamp >= startOfToday }
        val count = todayOrders.size
        val pendingRev = todayOrders
            .filter {
                val s = it.status.trim()
                s.equals("PENDING", ignoreCase = true) || s.equals("IN_PREPARATION", ignoreCase = true)
            }
            .sumOf { it.totalAmount }
        Pair(count, pendingRev)
    }

    private val repliesMetricFlow = combine(
        auditLogRepository.observeReplyCount(),
        auditLogRepository.observeLogs(limit = 200)
    ) { totalReplies, logs ->
        val startOfToday = getStartOfTodayEpochMillis()
        val todayReplies = logs.count {
            it.timestamp.toEpochMilliseconds() >= startOfToday && it.wasAutoSent
        }
        Pair(totalReplies, todayReplies)
    }

    private val metricsFlow = combine(ordersMetricFlow, repliesMetricFlow) { (orders, rev), (totalReplies, todayReplies) ->
        B2BMetrics(orders, rev, totalReplies, todayReplies)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        autoReplyHealthFlow,
        attentionFlow,
        metricsFlow,
        businessKnowledgeDao.observeChunkCount(),
        recentLogsFlow
    ) { isEnabled, (attentionCount, waitingCustomers), metrics, chunkCount, recentLogs ->
        val health = computeSystemHealth(isEnabled)
        HomeUiState(
            isAutoReplyEnabled = isEnabled,
            systemHealth = health,
            pendingAttentionCount = attentionCount.coerceAtLeast(0),
            waitingCustomers = waitingCustomers,
            todayOrdersCount = metrics.todayOrders.coerceAtLeast(0),
            todayPendingRevenue = metrics.pendingRevenue.coerceAtLeast(0.0),
            waitingCustomersCount = attentionCount.coerceAtLeast(0),
            todayRepliesCount = metrics.todayReplies.coerceAtLeast(0),
            totalRepliesSent = metrics.totalReplies.coerceAtLeast(0),
            knowledgeChunkCount = chunkCount.coerceAtLeast(0),
            recentActivity = recentLogs,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun computeSystemHealth(isEnabled: Boolean): SystemHealthStatus {
        if (!isEnabled) {
            return SystemHealthStatus.PAUSED
        }
        val hasPermission = try {
            permissionChecker.isNotificationListenerEnabled()
        } catch (_: Throwable) {
            false
        }
        if (!hasPermission) {
            return SystemHealthStatus.PERMISSION_MISSING
        }
        val isRunning = try {
            serviceAliveChecker()
        } catch (_: Throwable) {
            false
        }
        if (!isRunning) {
            return SystemHealthStatus.SERVICE_KILLED
        }
        return SystemHealthStatus.ACTIVE
    }

    private fun getStartOfTodayEpochMillis(): Long {
        val currentMillis = clockEpochMillis()
        val instant = Instant.ofEpochMilli(currentMillis)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            toggleAutoReplyUseCase(enabled)
        }
    }

    fun emergencyKillSwitch() {
        viewModelScope.launch {
            toggleAutoReplyUseCase(false)
        }
    }

    fun openNotificationSettings(context: Context) {
        permissionChecker.startNotificationListenerSettings(context)
    }

    fun restartService() {
        viewModelScope.launch {
            toggleAutoReplyUseCase(true)
        }
    }
}
