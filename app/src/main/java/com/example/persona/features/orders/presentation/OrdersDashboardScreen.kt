package com.example.persona.features.orders.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.orders.presentation.components.*
import kotlinx.coroutines.launch

/**
 * Dedicated Merchant Orders Management & Fulfillment Dashboard.
 *
 * Provides live WhatsApp/manual orders, 1-tap Kanban state transitions,
 * bulk manifest clipboard export, order creation/editing/cancellation,
 * and direct chat jumping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
    onNavigateToChat: (contactName: String, sourceClass: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var noteEditingOrder by remember { mutableStateOf<OrderEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isMultiSelectMode) {
                // Contextual TopAppBar for bulk actions
                TopAppBar(
                    title = {
                        Text(
                            text = "تم تحديد ${state.selectedOrderIds.size} طلبات",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = viewModel::clearSelection,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::selectAllOrders,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "تحديد الكل")
                        }
                        IconButton(
                            onClick = {
                                val manifest = viewModel.exportSelectedBulkManifest()
                                clipboardManager.setText(AnnotatedString(manifest))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("تم نسخ كشف الشحنات المجمع")
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "تصدير المانيفست المجمع")
                        }
                        IconButton(
                            onClick = viewModel::bulkAdvanceStatus,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = "تقديم حالة المحددين")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "الطلبات والتنفيذ والشحن", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (state.totalCount > 0)
                                    "${state.totalCount} طلبات مسجلة • مزامنة حية"
                                else
                                    "إدارة ومتابعة طلبات المتجر",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Bulk export for all current orders
                        IconButton(
                            onClick = {
                                val manifest = viewModel.exportSelectedBulkManifest()
                                clipboardManager.setText(AnnotatedString(manifest))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("تم نسخ كشف شحنات اليوم (${state.ordersList.size} طلبات)")
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "كشف الشحنات المجمع")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreateOrderDialog,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("طلب جديد", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(min = 48.dp)
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
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // ── Interactive metrics chips ────────────────────────────────
                OrderMetricsRow(
                    pendingCount = state.pendingCount,
                    inPreparationCount = state.inPreparationCount,
                    shippingCount = state.shippingCount,
                    completedCount = state.completedCount,
                    totalCodAmount = state.totalCodAmount,
                    selectedFilter = state.selectedTab,
                    onFilterClick = viewModel::onFilterSelected
                )

                // ── Date range filter chips ──────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DateFilter.entries.forEach { dateFilter ->
                        FilterChip(
                            selected = state.selectedDateFilter == dateFilter,
                            onClick = { viewModel.onDateFilterSelected(dateFilter) },
                            label = { Text(dateFilter.label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // ── Search field ─────────────────────────────────────────────
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("بحث باسم العميل، الهاتف، العنوان، أو الأصناف…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChanged("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح البحث",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // ── Error banner ─────────────────────────────────────────────
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
                                    contentDescription = "إغلاق التنبيه",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ── Status Filter Tabs ────────────────────────────────────────
                val filters = OrderFilter.entries
                val selectedTabIndex = filters.indexOf(state.selectedTab)
                ScrollableTabRow(
                    selectedTabIndex = if (selectedTabIndex >= 0) selectedTabIndex else 0,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                        val tabText = if (count > 0) "${filter.label} ($count)" else filter.label
                        Tab(
                            selected = filter == state.selectedTab,
                            onClick = { viewModel.onFilterSelected(filter) },
                            text = {
                                Text(
                                    text = tabText,
                                    fontWeight = if (filter == state.selectedTab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // ── Orders list / empty state ────────────────────────────────
                when {
                    state.isLoading -> Unit

                    state.ordersList.isEmpty() -> EmptyOrdersState(
                        isSearching = state.searchQuery.isNotBlank() || state.selectedDateFilter != DateFilter.ALL,
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
                                onAdvanceStatus = viewModel::advanceOrderStatus,
                                onEditNote = { noteEditingOrder = order },
                                onCopyCourierManifest = { targetOrder ->
                                    val manifest = viewModel.formatCourierManifest(targetOrder)
                                    clipboardManager.setText(AnnotatedString(manifest))
                                    coroutineScope.launch {
                                        val customer = targetOrder.customerNameFromLlm?.takeIf { it.isNotBlank() }
                                            ?: targetOrder.contactName
                                        snackbarHostState.showSnackbar("تم نسخ بوليصة الشحن للعميل $customer")
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
                        item { Spacer(modifier = Modifier.height(72.dp)) }
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
                text = if (isSearching) "لا توجد طلبات متطابقة" else "لا توجد طلبات حتى الآن",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (isSearching)
                    "جرب تغيير كلمات البحث أو تغيير تاب الحالة أو الفترة الزمنية."
                else
                    "الطلبات تظهر هنا تلقائياً بمجرد استخراجها من محادثات واتساب، أو يمكنك إدخال طلب يدوياً بالضغط على زر (+).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}
