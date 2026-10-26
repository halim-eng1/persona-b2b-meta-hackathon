package com.example.persona.features.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Merchant Orders Dashboard — live view of LLM-extracted orders with status
 * transitions and merchant-note editing (notes feed into the LLM context).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var noteEditingOrder by remember { mutableStateOf<OrderEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Orders", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Live LLM-extracted orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                if (state.isUpdating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                }

                // ── Metrics chips ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        count = state.pendingCount,
                        label = "Pending",
                        color = Amber
                    )
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        count = state.inPreparationCount,
                        label = "In Preparation",
                        color = Blue
                    )
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        count = state.completedCount,
                        label = "Completed",
                        color = Green
                    )
                }

                // ── Search ───────────────────────────────────────────────────
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search contact or details…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { viewModel.onSearchQueryChanged("") }
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // ── Transient error banner ───────────────────────────────────
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // ── Filter tabs ──────────────────────────────────────────────
                val filters = OrderFilter.entries
                val selectedTabIndex = filters.indexOf(state.selectedTab)
                ScrollableTabRow(
                    selectedTabIndex = if (selectedTabIndex >= 0) selectedTabIndex else 0,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    edgePadding = 4.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    filters.forEach { filter ->
                        Tab(
                            selected = filter == state.selectedTab,
                            onClick = { viewModel.onFilterSelected(filter) },
                            text = { Text(filter.label, fontWeight = if (filter == state.selectedTab) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // ── Orders list / empty state ────────────────────────────────
                when {
                    state.isLoading -> Unit

                    state.ordersList.isEmpty() -> EmptyOrdersState(
                        isSearching = state.searchQuery.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp, vertical = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.ordersList, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChanged = viewModel::onStatusChanged,
                                onEditNote = { noteEditingOrder = order }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }

            // ── Merchant note bottom sheet ───────────────────────────────────
            noteEditingOrder?.let { target ->
                // Re-resolve the order from the list so the sheet reflects writes.
                val current = state.ordersList.firstOrNull { it.id == target.id } ?: target
                MerchantNoteSheet(
                    order = current,
                    onDismiss = { noteEditingOrder = null },
                    onSave = { note ->
                        viewModel.onMerchantNoteSaved(current.id, note)
                        noteEditingOrder = null
                    }
                )
            }
        }
    }
}

// ── Order card ──────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order: OrderEntity,
    onStatusChanged: (Long, OrderStatus) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: avatar + name + timestamp … status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = order.contactName.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerNameFromLlm ?: order.contactName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatOrderTimestamp(order.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order details summary
            if (!order.orderDetails.isNullOrBlank()) {
                Text(
                    text = order.orderDetails,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!order.deliveryAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = order.deliveryAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick actions: status dropdown + note editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDropdownButton(
                    currentStatus = order.status,
                    onSelected = { onStatusChanged(order.id, it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onEditNote,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (order.merchantNote.isNullOrBlank()) "Note" else "Note ✓",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ── Status UI pieces ────────────────────────────────────────────────────────

/** Color-coded status badge surfaces. */
enum class StatusBadgeStyle(val container: Color, val content: Color, val label: String) {
    PENDING(Amber.copy(alpha = 0.15f), Amber, "Pending"),
    IN_PREPARATION(Blue.copy(alpha = 0.15f), Blue, "Preparing"),
    SHIPPING_IN_PROGRESS(Indigo.copy(alpha = 0.15f), Indigo, "Shipping"),
    SHIPPED(Green.copy(alpha = 0.15f), Green, "Shipped"),
    CANCELLED(Red.copy(alpha = 0.15f), Red, "Cancelled"),
    RETURNED(Brown.copy(alpha = 0.15f), Brown, "Returned");

    companion object {
        fun from(statusName: String): StatusBadgeStyle =
            entries.firstOrNull { it.name == statusName } ?: PENDING
    }
}

@Composable
private fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val style = StatusBadgeStyle.from(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = style.container
    ) {
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = style.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusDropdownButton(
    currentStatus: String,
    onSelected: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = StatusBadgeStyle.from(currentStatus).label,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Change status",
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OrderStatus.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = StatusBadgeStyle.from(candidate.name).label,
                            fontWeight = if (candidate.name == currentStatus) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        if (candidate.name != currentStatus) onSelected(candidate)
                    }
                )
            }
        }
    }
}

// ── Merchant note bottom sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantNoteSheet(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteValue by remember(order.id) { mutableStateOf(order.merchantNote.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Merchant Note — ${order.customerNameFromLlm ?: order.contactName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This note is injected into the bot's live context, so the assistant shares it with the customer automatically. " +
                    "Leave it empty and save to clear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = noteValue,
                onValueChange = { noteValue = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. المندوب حيوصل بكرة الساعة 3") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(noteValue.trim()) },
                // Enabled even when blank: an empty save CLEARS the note (mapped
                // to null in the ViewModel), so a stale note can be removed.
                enabled = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save Note", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// ── Metrics / empty state ───────────────────────────────────────────────────

@Composable
private fun MetricChip(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyOrdersState(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            Text(
                text = if (isSearching) "No matching orders" else "No orders yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isSearching)
                    "Try a different search term or switch tabs."
                else
                    "Orders appear here automatically when the bot extracts them from customer conversations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Month abbreviations for [formatOrderTimestamp] — built once, not per frame. */
private val ORDER_MONTHS = mapOf(
    1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr", 5 to "May", 6 to "Jun",
    7 to "Jul", 8 to "Aug", 9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
)

/** Formats epoch-millis as "25 Oct 14:30" (digits pinned to Latin numerals). */
private fun formatOrderTimestamp(timestamp: Long): String {
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ORDER_MONTHS[dt.monthNumber] ?: "%02d".format(java.util.Locale.US, dt.monthNumber)
    // Locale.US keeps digits Latin (e.g. "14:30", not "١٤:٣٠") regardless of the
    // device locale — an ID-code-like field merchants scan quickly.
    return "%d %s — %02d:%02d".format(
        java.util.Locale.US, dt.dayOfMonth, month, dt.hour, dt.minute
    )
}

// ── Status palette ──────────────────────────────────────────────────────────

private val Amber = Color(0xFFFFB300)
private val Blue = Color(0xFF3B82F6)
private val Indigo = Color(0xFF818CF8)
private val Green = Color(0xFF34D399)
private val Red = Color(0xFFF87171)
private val Brown = Color(0xFFA1887F)
