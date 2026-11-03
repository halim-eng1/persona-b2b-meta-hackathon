package com.example.persona.features.home.presentation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.database.entity.TodayOrdersAggregate
import com.example.persona.core.di.IoDispatcher
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.auto_reply.domain.orchestrator.HitlNotificationHandler
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface HomeUiEvent {
    data class ShowSnackbar(val message: String) : HomeUiEvent
}

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

enum class SetupCopilotStep { NEEDS_API_KEY, NEEDS_IDENTITY }

data class HomeUiState(
    val isAutoReplyEnabled: Boolean = false,
    val isServiceRunning: Boolean = false,
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
    val isAlwaysShowOnboardingEnabled: Boolean = false,
    val isAutoPauseOnHitl: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val setupCopilotStep: SetupCopilotStep? = null
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
    private val permissionChecker: PermissionChecker,
    private val hitlNotificationHandler: HitlNotificationHandler? = null,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // Overridable clock and service checker for testing
    var clockEpochMillis: () -> Long = { System.currentTimeMillis() }
    var serviceAliveChecker: () -> Boolean = {
        WhatsAppNotificationListener.instance?.get() != null
    }

    private val _healthRefreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private val _uiEvents = Channel<HomeUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun refreshHealth() {
        _healthRefreshTrigger.value = System.currentTimeMillis()
    }

    private val autoReplyHealthFlow = combine(
        userPreferencesRepository.isAutoReplyEnabled,
        WhatsAppNotificationListener.isConnectedFlow,
        _healthRefreshTrigger
    ) { isEnabled, isConnected, _ ->
        Pair(isEnabled, isConnected)
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
    }.flowOn(ioDispatcher)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ordersMetricFlow = _healthRefreshTrigger.flatMapLatest {
        val startOfToday = getStartOfTodayEpochMillis()
        orderDao.observeTodayOrdersAggregate(startOfToday)
    }.map { aggregate ->
        Pair(aggregate.orderCount, aggregate.pendingRevenue)
    }.flowOn(ioDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val repliesMetricFlow = _healthRefreshTrigger.flatMapLatest {
        val startOfToday = getStartOfTodayEpochMillis()
        val startOfTodayInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(startOfToday)
        combine(
            auditLogRepository.observeReplyCount(),
            auditLogRepository.observeAutoSentCountSince(startOfTodayInstant)
        ) { totalReplies, todayReplies ->
            Pair(totalReplies, todayReplies)
        }
    }.flowOn(ioDispatcher)

    private val metricsFlow = combine(ordersMetricFlow, repliesMetricFlow) { (orders, rev), (totalReplies, todayReplies) ->
        B2BMetrics(orders, rev, totalReplies, todayReplies)
    }

    /** Emits the current setup step or null when fully configured. */
    private val setupCopilotFlow = combine(
        userPreferencesRepository.botName,
        userPreferencesRepository.storeName
    ) { bot, store ->
        val hasApiKey = userPreferencesRepository.hasConfiguredKeys(
            com.example.persona.features.settings.domain.repository.ModelProviders.META
        )
        val isDefaultBot = bot == UserPreferencesRepository.DEFAULT_BOT_NAME ||
            bot == UserPreferencesRepository.DEFAULT_BOT_NAME_EN
        val isDefaultStore = store == UserPreferencesRepository.DEFAULT_STORE_NAME ||
            store == UserPreferencesRepository.DEFAULT_STORE_NAME_EN
        when {
            !hasApiKey -> SetupCopilotStep.NEEDS_API_KEY
            isDefaultBot && isDefaultStore -> SetupCopilotStep.NEEDS_IDENTITY
            else -> null
        }
    }.flowOn(ioDispatcher)

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(autoReplyHealthFlow, attentionFlow) { health, attention -> Pair(health, attention) },
        combine(metricsFlow, businessKnowledgeDao.observeChunkCount()) { m, c -> Pair(m, c) },
        recentLogsFlow,
        combine(
            userPreferencesRepository.alwaysShowOnboardingFlow,
            userPreferencesRepository.isAutoPauseOnHitlEnabledFlow,
            setupCopilotFlow
        ) { onboarding, autoPause, setup -> Triple(onboarding, autoPause, setup) },
        _isRefreshing
    ) { (healthData, attentionData), (metrics, chunkCount), recentLogs, prefsData, isRefreshing ->
        val (isEnabled, isConnected) = healthData
        val (attentionCount, waitingCustomers) = attentionData
        val running = try { serviceAliveChecker() } catch (_: Throwable) { isConnected }
        val health = computeSystemHealth(isEnabled)
        val (alwaysShowOnboarding, isAutoPauseOnHitl, setupStep) = prefsData
        HomeUiState(
            isAutoReplyEnabled = isEnabled,
            isServiceRunning = running,
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
            isAlwaysShowOnboardingEnabled = alwaysShowOnboarding,
            isAutoPauseOnHitl = isAutoPauseOnHitl,
            isLoading = false,
            isRefreshing = isRefreshing,
            setupCopilotStep = setupStep
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

    fun refreshSystemHealth() {
        refreshHealth()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshSystemHealth()
            kotlinx.coroutines.delay(400)
            _isRefreshing.value = false
        }
    }

    fun sendTestNotification() {
        hitlNotificationHandler?.triggerHitlAlarm(
            contactName = "تجربة النظام (Test)",
            queryText = "هذا إشعار تجريبي لاختبار الصوت والتنبيهات بنجاح 🔔",
            score = 1.0f,
            sourceClass = "BUSINESS"
        )
    }

    fun toggleAlwaysShowOnboarding(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAlwaysShowOnboarding(enabled)
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        return try {
            permissionChecker.isNotificationListenerEnabled()
        } catch (_: Throwable) {
            false
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

    fun restartNotificationListenerService(context: Context) {
        if (!isNotificationPermissionGranted()) {
            openNotificationSettings(context)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val componentName = ComponentName(context, WhatsAppNotificationListener::class.java)
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(componentName)
                }
                toggleAutoReplyUseCase(true)
                refreshSystemHealth()
                _uiEvents.send(HomeUiEvent.ShowSnackbar(context.getString(R.string.home_service_restart_success)))
            } catch (e: Exception) {
                _uiEvents.send(HomeUiEvent.ShowSnackbar(context.getString(R.string.home_service_restart_failed, e.message ?: "")))
            }
        }
    }
}
