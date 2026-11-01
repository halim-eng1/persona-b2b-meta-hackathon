package com.example.persona.features.knowledge.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.features.knowledge.presentation.components.AnswerFailedQueryDialog
import com.example.persona.features.knowledge.presentation.components.BotSimulatorDialog
import com.example.persona.features.knowledge.presentation.components.EditKnowledgeDialog
import com.example.persona.features.knowledge.presentation.components.FailedQueryRow
import com.example.persona.features.knowledge.presentation.components.KnowledgeEntryRow
import com.example.persona.features.knowledge.presentation.components.AddKnowledgeEntryDialog
import androidx.compose.material.icons.filled.Add
import com.example.persona.ui.components.personaScreenBackground
import kotlinx.coroutines.launch

private val KnowledgeFilters = listOf(
    "ALL" to R.string.knowledge_filter_all,
    "PRODUCT" to R.string.knowledge_filter_product,
    "POLICY" to R.string.knowledge_filter_policy,
    "OFFER" to R.string.knowledge_filter_offer
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeManagementScreen(
    modifier: Modifier = Modifier,
    viewModel: KnowledgeManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Clear All Dialog
    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.knowledge_clear_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.knowledge_clear_confirm_message)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmationDialog = false
                        viewModel.clearAllKnowledge()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.common_delete_all))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirmationDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Edit Entry Dialog
    state.editingEntry?.let { entryToEdit ->
        EditKnowledgeDialog(
            entry = entryToEdit,
            onDismiss = { viewModel.onDismissEditDialog() },
            onSave = { newText ->
                viewModel.onSaveEditedEntry(entryToEdit.chunkId, newText)
            }
        )
    }

    // Answer Failed Query Dialog (HITL Resolver)
    state.answeringQuery?.let { queryToAnswer ->
        AnswerFailedQueryDialog(
            query = queryToAnswer,
            onDismiss = { viewModel.onDismissAnswerDialog() },
            onSaveAnswer = { answer, isOffer ->
                viewModel.saveAnswerForFailedQuery(queryToAnswer.id, queryToAnswer.queryText, answer, isOffer)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.knowledge_snackbar_answer_saved),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    // Bot Simulator / Full Agentic Multi-turn Sandbox Dialog
    BotSimulatorDialog(
        isOpen = state.isSimulatorOpen,
        onDismiss = { viewModel.onCloseSimulator() },
        onRunTest = { query -> viewModel.runSimulation(query) },
        onSendTurn = { query, uri, type -> viewModel.runSimulation(query, uri, type) },
        onClearHistory = { viewModel.onClearSimulatorHistory() },
        onMediaSelected = { uri, type -> viewModel.onSimulatorMediaSelected(uri, type) },
        onClearMedia = { viewModel.onClearSelectedMedia() },
        isSimulating = state.isSimulating,
        messages = state.simulatorMessages,
        selectedMediaUri = state.selectedMediaUri,
        selectedMediaType = state.selectedMediaType,
        simulatedResponse = state.simulatedResponse,
        simulationResults = state.simulationResults,
        simulationBestScore = state.simulationBestScore,
        simulationError = state.simulationError
    )

    // Keyboard-aware, vertically scrollable dialog to add product or free text knowledge
    AddKnowledgeEntryDialog(
        isOpen = showAddEntryDialog,
        onDismiss = { showAddEntryDialog = false },
        onAddEntry = { formattedText, isTemporaryOffer ->
            viewModel.onInputQueryChanged(formattedText)
            viewModel.onTemporaryOfferToggled(isTemporaryOffer)
            viewModel.addManualEntry()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.knowledge_snackbar_entry_added),
                    duration = SnackbarDuration.Short
                )
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .personaScreenBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header cleanly below status bar (Simulator moved to FAB to prevent overlap)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.knowledge_management_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                IconButton(
                    onClick = { showClearConfirmationDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.knowledge_clear_confirm_title),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.knowledge_tab_catalog),
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                            if (state.entries.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(text = state.entries.size.toString())
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.knowledge_tab_pending_queries),
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                            if (state.unansweredQueries.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ) {
                                    Text(text = state.unansweredQueries.size.toString())
                                }
                            }
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                // Tab 1: Knowledge Base List & Creation Card
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Prominent action button to open Add Knowledge/Product Dialog
                    Button(
                        onClick = { showAddEntryDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.knowledge_btn_add_entry_full),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Search & Category Filter Bar
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text(stringResource(R.string.knowledge_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.knowledge_search_placeholder),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.knowledge_search_clear)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(KnowledgeFilters) { (key, resId) ->
                            FilterChip(
                                selected = state.selectedFilter == key,
                                onClick = { viewModel.onFilterSelected(key) },
                                label = { Text(stringResource(resId), fontSize = 12.sp) }
                            )
                        }
                    }

                    // Entries List Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.knowledge_catalog_items_count, state.filteredEntries.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 0.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Knowledge List
                    val displayList = state.filteredEntries
                    if (displayList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (state.searchQuery.isNotBlank())
                                        stringResource(R.string.knowledge_search_no_results)
                                    else
                                        stringResource(R.string.knowledge_empty_state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (state.searchQuery.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.onSearchQueryChanged("") },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.knowledge_search_clear))
                                    }
                                } else {
                                    Button(
                                        onClick = { showAddEntryDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.knowledge_empty_state_cta))
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayList, key = { it.chunkId }) { entry ->
                                KnowledgeEntryRow(
                                    entry = entry,
                                    onEdit = { viewModel.onEditEntry(entry) },
                                    onDelete = {
                                        val deletedItem = entry
                                        viewModel.deleteEntry(deletedItem)
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.knowledge_snackbar_entry_deleted),
                                                actionLabel = context.getString(R.string.knowledge_snackbar_undo),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreLastDeletedEntry()
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.knowledge_snackbar_entry_restored),
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Tab 2: Pending Unanswered Queries (Human-in-the-Loop Inbox)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.knowledge_hitl_inbox_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.unansweredQueries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = stringResource(R.string.knowledge_hitl_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.unansweredQueries, key = { it.id }) { query ->
                                FailedQueryRow(
                                    query = query,
                                    onUseAsInput = {
                                        viewModel.onStartAnsweringQuery(query)
                                    },
                                    onMarkReviewed = { viewModel.markQueryReviewed(query.id) },
                                    onDelete = { viewModel.deleteQuery(query.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Operational Snackbar Host for Action Confirmations & Undo
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        )

        // Floating Action Button to launch Bot Simulator cleanly
        ExtendedFloatingActionButton(
            onClick = { viewModel.onOpenSimulator() },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.bot_simulator_fab),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        )
    }
}
