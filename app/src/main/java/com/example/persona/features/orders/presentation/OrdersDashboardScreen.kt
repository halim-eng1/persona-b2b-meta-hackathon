package com.example.persona.features.orders.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.orders.presentation.components.*
import kotlinx.coroutines.launch

/**
 * Dedicated Merchant Orders Management & Fulfillment Dashboard.
 *
 * Provides live WhatsApp/manual orders, 1-tap Kanban state transitions,
 * bulk manifest clipboard export, order creation/editing/cancellation,
 * sticky collapsible search & status filters, and direct chat jumping.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OrdersDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
    onNavigateToChat: (contactName: String, sourceClass: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var noteEditingOrder by remember { mutableStateOf<OrderEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val isFabExpanded = remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isMultiSelectMode) {
                // Contextual TopAppBar for bulk actions
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.orders_selected_count, state.selectedOrderIds.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = viewModel::clearSelection,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.orders_clear_selection_desc))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::selectAllOrders,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.orders_select_all_desc))
                        }
                        IconButton(
                            onClick = {
                                val manifest = viewModel.exportSelectedBulkManifest()
                                clipboardManager.setText(AnnotatedString(manifest))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.orders_manifest_copied_snackbar))
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.orders_export_manifest_desc))
                        }
                        IconButton(
                            onClick = viewModel::bulkAdvanceStatus,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.orders_dashboard_advance_selected_desc))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !state.isMultiSelectMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openCreateOrderDialog,
                    expanded = isFabExpanded.value,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.orders_dashboard_fab_new_order), fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // ── Top Header Item: Dashboard Title & Manifest Export ────────
                item(key = "orders_header_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.orders_dashboard_header_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.totalCount > 0)
                                    stringResource(R.string.orders_dashboard_header_subtitle_count, state.totalCount)
                                else
                                    stringResource(R.string.orders_dashboard_header_subtitle_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                val manifest = viewModel.exportSelectedBulkManifest()
                                clipboardManager.setText(AnnotatedString(manifest))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.orders_dashboard_copied_manifest_snackbar,
                                            state.ordersList.size
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = stringResource(R.string.orders_dashboard_bulk_manifest_desc),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ── Progress indicator when updating ─────────────────────────
                if (state.isUpdating) {
                    item(key = "orders_updating_indicator") {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── KPI Summary Cards (جديد / قيد التجهيز / تم التسليم) ──────
                item(key = "orders_kpi_metrics") {
                    OrderMetricsRow(
                        pendingCount = state.pendingCount,
                        inPreparationCount = state.inPreparationCount,
                        shippingCount = state.shippingCount,
                        completedCount = state.completedCount,
                        totalCodAmount = state.totalCodAmount,
                        selectedFilter = state.selectedTab,
                        onFilterClick = viewModel::onFilterSelected
                    )
                }

                // ── Date range filter chips (الكل / اليوم / أمس...) ───────────
                item(key = "orders_date_filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DateFilter.entries.forEach { dateFilter ->
                            FilterChip(
                                selected = state.selectedDateFilter == dateFilter,
                                onClick = { viewModel.onDateFilterSelected(dateFilter) },
                                label = {
                                    Text(
                                        text = stringResource(dateFilter.labelResId),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // ── Sticky Header: Compact Search Bar + Status Tab Row ────────
                stickyHeader(key = "orders_sticky_controls") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            // Slim, compact search bar with maxLines = 1, singleLine = true
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.orders_dashboard_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_clear_content_desc),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            // Error banner if any
                            state.errorMessage?.let { message ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                        IconButton(
                                            onClick = viewModel::clearErrorMessage,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_close_alert),
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Status Filter Tabs
                            val filters = OrderFilter.entries
                            val selectedTabIndex = filters.indexOf(state.selectedTab)
                            ScrollableTabRow(
                                selectedTabIndex = if (selectedTabIndex >= 0) selectedTabIndex else 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                edgePadding = 4.dp,
                                containerColor = Color.Transparent,
                                divider = {}
                            ) {
                                filters.forEach { filter ->
                                    val count = when (filter) {
                                        OrderFilter.ALL -> state.totalCount
                                        OrderFilter.PENDING -> state.pendingCount
                                        OrderFilter.IN_PREPARATION -> state.inPreparationCount
                                        OrderFilter.SHIPPING_IN_PROGRESS -> state.shippingCount
                                        OrderFilter.COMPLETED -> state.completedCount
                                        OrderFilter.CANCELLED -> state.cancelledCount
                                        OrderFilter.RETURNED -> state.returnedCount
                                    }
                                    val filterName = stringResource(filter.labelResId)
                                    val tabText = if (count > 0) "$filterName ($count)" else filterName
                                    Tab(
                                        selected = filter == state.selectedTab,
                                        onClick = { viewModel.onFilterSelected(filter) },
                                        text = {
                                            Text(
                                                text = tabText,
                                                fontWeight = if (filter == state.selectedTab) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // ── Orders list / empty state / loading state ────────────────
                when {
                    state.isLoading && state.ordersList.isEmpty() -> {
                        item(key = "orders_loading_indicator") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        }
                    }

                    state.ordersList.isEmpty() -> {
                        item(key = "orders_empty_state") {
                            EmptyOrdersState(
                                isSearching = state.searchQuery.isNotBlank() || state.selectedDateFilter != DateFilter.ALL,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp)
                            )
                        }
                    }

                    else -> {
                        items(items = state.ordersList, key = { it.id }) { order ->
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                OrderCard(
                                    order = order,
                                    onStatusChanged = viewModel::onStatusChanged,
                                    onAdvanceStatus = viewModel::advanceOrderStatus,
                                    onEditNote = { noteEditingOrder = order },
                                    onCopyCourierManifest = { targetOrder ->
                                        val manifest = viewModel.formatCourierManifest(targetOrder)
                                        clipboardManager.setText(AnnotatedString(manifest))
                                        coroutineScope.launch {
                                            val customer = targetOrder.customerNameFromLlm?.takeIf { it.isNotBlank() }
                                                ?: targetOrder.contactName
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.orders_dashboard_copied_waybill_snackbar,
                                                    customer
                                                )
                                            )
                                        }
                                    },
                                    onOpenChat = { contactName ->
                                        coroutineScope.launch {
                                            val resolvedSourceClass =
                                                viewModel.getChatSourceClassForContact(contactName)
                                            onNavigateToChat(contactName, resolvedSourceClass)
                                        }
                                    },
                                    onEditOrder = viewModel::openEditOrderDialog,
                                    onCancelOrder = viewModel::openCancelOrderDialog,
                                    isSelected = order.id in state.selectedOrderIds,
                                    isSelectionMode = state.isMultiSelectMode,
                                    onToggleSelect = viewModel::toggleOrderSelection
                                )
                            }
                        }
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

            // ── Manual Create Order Dialog ───────────────────────────────────
            if (state.isCreateOrderDialogOpen) {
                CreateOrderDialog(
                    onDismiss = viewModel::closeCreateOrderDialog,
                    onCreate = { name, phone, address, gov, details, total, shipping, notes ->
                        viewModel.createOrder(
                            customerName = name,
                            customerPhone = phone,
                            deliveryAddress = address,
                            governorate = gov,
                            orderDetails = details,
                            totalAmount = total,
                            shippingFee = shipping,
                            customNotes = notes
                        )
                    }
                )
            }

            // ── Edit Order Dialog ────────────────────────────────────────────
            state.editingOrder?.let { orderToEdit ->
                EditOrderDialog(
                    order = orderToEdit,
                    onDismiss = viewModel::closeEditOrderDialog,
                    onSave = { id, name, phone, address, gov, details, total, shipping ->
                        viewModel.updateOrder(
                            orderId = id,
                            customerName = name,
                            customerPhone = phone,
                            deliveryAddress = address,
                            governorate = gov,
                            orderDetails = details,
                            totalAmount = total,
                            shippingFee = shipping
                        )
                    }
                )
            }

            // ── Cancel Order Dialog ──────────────────────────────────────────
            state.cancellingOrder?.let { orderToCancel ->
                CancelOrderDialog(
                    order = orderToCancel,
                    onDismiss = viewModel::closeCancelOrderDialog,
                    onConfirmCancel = { reason ->
                        viewModel.cancelOrder(orderToCancel.id, reason)
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
                text = if (isSearching) stringResource(R.string.orders_dashboard_empty_title_search) else stringResource(R.string.orders_dashboard_empty_title_general),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isSearching)
                    stringResource(R.string.orders_dashboard_empty_desc_search)
                else
                    stringResource(R.string.orders_dashboard_empty_desc_general),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}
