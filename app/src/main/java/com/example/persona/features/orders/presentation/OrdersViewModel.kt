package com.example.persona.features.orders.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Tab/status filter applied to the orders list.
 *
 * Coverage note: [SHIPPING_IN_PROGRESS] and [RETURNED] have their own tabs so a
 * merchant-set status is always reachable from some tab — neither state may fall
 * into the "only visible under All" gap. */
enum class OrderFilter(val label: String) {
    ALL("All"),
    UNFULFILLED("Unfulfilled"),
    SHIPPING_IN_PROGRESS("Shipping"),
    SHIPPED("Shipped"),
    RETURNED("Returned"),
    CANCELLED("Cancelled");

    /** Returns true when [order] passes this filter. */
    fun matches(order: OrderEntity): Boolean = when (this) {
        ALL -> true
        UNFULFILLED -> order.status in OrderStatus.UNFULFILLED_STATE_NAMES
        SHIPPING_IN_PROGRESS -> order.status == OrderStatus.SHIPPING_IN_PROGRESS.name
        SHIPPED -> order.status == OrderStatus.SHIPPED.name
        RETURNED -> order.status == OrderStatus.RETURNED.name
        CANCELLED -> order.status == OrderStatus.CANCELLED.name
    }
}

data class OrdersUiState(
    /** Currently selected status filter tab. */
    val selectedTab: OrderFilter = OrderFilter.ALL,
    /** Orders filtered by [selectedTab] AND the free-text search query. */
    val ordersList: List<OrderEntity> = emptyList(),
    /** Search query — matches contact name or order details (case-insensitive). */
    val searchQuery: String = "",
    // ── Metric chips ─────────────────────────────────────────────────────────
    val pendingCount: Int = 0,
    val inPreparationCount: Int = 0,
    val completedCount: Int = 0,
    // ── Indicators ───────────────────────────────────────────────────────────
    val isLoading: Boolean = true,
    /** True while any status/note write is in flight (used for progress UI). */
    val isUpdating: Boolean = false,
    /** Transient message for the last failed dashboard write (null = no error). */
    val errorMessage: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private var selectedTab = MutableStateFlow(OrderFilter.ALL)
    private var searchQuery = MutableStateFlow("")
    private var isUpdating = MutableStateFlow(false)
    private var errorMessage = MutableStateFlow<String?>(null)

    /** Tracks concurrent in-flight writes so [isUpdating] clears at the right time. */
    private val inFlightWrites = AtomicInteger(0)

    /**
     * Single reactive pipeline: orders flow combined with the tab filter, search
     * query and updating flag. Room re-emits after every write, so the list and
     * metric chips stay live without any manual refresh.
     */
    val uiState: StateFlow<OrdersUiState> = combine(
        orderRepository.getAllOrders(),
        selectedTab,
        searchQuery,
        isUpdating,
        errorMessage
    ) { allOrders, tab, query, updating, error ->
        val queryTrimmed = query.trim()
        val filtered = allOrders
            .filter { tab.matches(it) }
            .filter { order ->
                if (queryTrimmed.isEmpty()) return@filter true
                // Search by contact name or order details (case-insensitive).
                listOfNotNull(
                    order.contactName,
                    order.customerNameFromLlm,
                    order.orderDetails,
                    order.customNotes,
                    order.deliveryAddress
                ).any { field -> field.contains(queryTrimmed, ignoreCase = true) }
            }
        OrdersUiState(
            selectedTab = tab,
            ordersList = filtered,
            // Keep the RAW query in state so the TextField stays in sync with what
            // the user typed (trailing spaces included). Only filtering trims —
            // emitting the trimmed value back into the field makes multi-word
            // queries ("Ahmed Ali") impossible to type.
            searchQuery = query,
            pendingCount = allOrders.count { it.status == OrderStatus.PENDING.name },
            inPreparationCount = allOrders.count { it.status == OrderStatus.IN_PREPARATION.name },
            completedCount = allOrders.count {
                // "Completed" = terminal success states across both tabs:
                // Shipped counts SHIPPED, Returned counts RETURNED.
                it.status == OrderStatus.SHIPPED.name || it.status == OrderStatus.RETURNED.name
            },
            isLoading = false,
            isUpdating = updating,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OrdersUiState()
    )

    fun onFilterSelected(filter: OrderFilter) {
        selectedTab.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onStatusChanged(orderId: Long, newStatus: OrderStatus) {
        runTrackedWrite {
            orderRepository.updateOrderStatus(orderId, newStatus)
        }
    }

    fun onMerchantNoteSaved(orderId: Long, note: String) {
        runTrackedWrite {
            // Blank == "clear the note" — the DAO column is nullable, so map an
            // empty/whitespace-only save to null instead of persisting "".
            orderRepository.updateMerchantNote(orderId, note.ifBlank { null })
        }
    }

    /**
     * Wraps a repository write with the [isUpdating] progress bookkeeping.
     *
     * Failures are caught and surfaced via [errorMessage] instead of crashing the
     * process (a bare `launch` would let a SQLiteException reach the default
     * uncaught handler). [CancellationException] is always re-thrown.
     */
    private fun runTrackedWrite(write: suspend () -> Unit) {
        viewModelScope.launch {
            inFlightWrites.incrementAndGet()
            errorMessage.value = null
            isUpdating.value = true
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = "Couldn't save changes. Please try again."
            } finally {
                // On cancellation the finally still runs while the VM is alive;
                // clearing the flag then is harmless (state collector is dead).
                if (inFlightWrites.decrementAndGet() == 0) {
                    isUpdating.value = false
                }
            }
        }
    }
}
