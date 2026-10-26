package com.example.persona.features.orders.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.orders.presentation.components.MerchantNoteSheet
import com.example.persona.features.orders.presentation.components.OrderCard
import com.example.persona.features.orders.presentation.components.OrderMetricsRow

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
                OrderMetricsRow(
                    pendingCount = state.pendingCount,
                    inPreparationCount = state.inPreparationCount,
                    completedCount = state.completedCount
                )

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
