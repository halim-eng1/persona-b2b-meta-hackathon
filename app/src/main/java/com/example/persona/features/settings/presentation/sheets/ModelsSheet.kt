package com.example.persona.features.settings.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.presentation.AiActions
import com.example.persona.features.settings.presentation.components.LocalUndoHost
import com.example.persona.features.settings.presentation.components.rememberReorderableLazyListState
import com.example.persona.features.settings.presentation.components.reorderableHandle
import com.example.persona.features.settings.presentation.components.reorderableItem
import com.example.persona.ui.theme.PersonaTheme

// ── Helpers ──────────────────────────────────────────────────────────────────

private data class CapabilityChipDef(
    val capability: ModelCapability,
    val icon: ImageVector,
    val label: String
)

private val CAPABILITY_CHIPS = listOf(
    CapabilityChipDef(ModelCapability.TEXT,   Icons.Default.TextFields, "نص ومحادثة"),
    CapabilityChipDef(ModelCapability.VISION, Icons.Default.Image,      "رؤية وصور"),
    CapabilityChipDef(ModelCapability.AUDIO,  Icons.Default.MicNone,    "تفريغ صوتي")
)

private fun iconForCapability(cap: ModelCapability): ImageVector = when (cap) {
    ModelCapability.TEXT   -> Icons.Default.TextFields
    ModelCapability.VISION -> Icons.Default.Image
    ModelCapability.AUDIO  -> Icons.Default.MicNone
}

/** Infers smart capability pre-selection from the typed model ID. */
private fun inferCapabilities(modelId: String): Set<ModelCapability> =
    ModelConfig.inferCapabilities(modelId)

// ── Add Model Dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProviderModelDialog(
    onDismiss: () -> Unit,
    onConfirm: (modelId: String, capabilities: Set<ModelCapability>) -> Unit
) {
    var modelInput by rememberSaveable { mutableStateOf("") }
    var selectedCaps by remember { mutableStateOf(setOf(ModelCapability.TEXT)) }

    // Smart pre-selection: re-infer when user types
    LaunchedEffect(modelInput) {
        if (modelInput.isNotBlank()) {
            val inferred = inferCapabilities(modelInput)
            selectedCaps = inferred
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_ai_add_model_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text(stringResource(R.string.settings_ai_add_model_label)) },
                    placeholder = { Text("e.g. qwen3.8-omni-flash") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_ai_model_capabilities_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CAPABILITY_CHIPS.forEach { def ->
                            val selected = def.capability in selectedCaps
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCaps = if (selected && selectedCaps.size > 1) {
                                        selectedCaps - def.capability
                                    } else {
                                        selectedCaps + def.capability
                                    }
                                },
                                label = { Text(def.label, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = def.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    if (selectedCaps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_ai_model_capabilities_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = modelInput.trim()
                    if (trimmed.isNotBlank()) {
                        onConfirm(
                            trimmed,
                            selectedCaps.ifEmpty { setOf(ModelCapability.TEXT) }
                        )
                    }
                },
                enabled = modelInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// ── Capability badge row shown inside each model card ────────────────────────

@Composable
private fun CapabilityBadgeRow(capabilities: Set<ModelCapability>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        capabilities.sortedBy { it.ordinal }.forEach { cap ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (cap) {
                    ModelCapability.TEXT   -> MaterialTheme.colorScheme.secondaryContainer
                    ModelCapability.VISION -> MaterialTheme.colorScheme.tertiaryContainer
                    ModelCapability.AUDIO  -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = iconForCapability(cap),
                        contentDescription = cap.label,
                        modifier = Modifier.size(10.dp),
                        tint = when (cap) {
                            ModelCapability.TEXT   -> MaterialTheme.colorScheme.onSecondaryContainer
                            ModelCapability.VISION -> MaterialTheme.colorScheme.onTertiaryContainer
                            ModelCapability.AUDIO  -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Text(
                        text = cap.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (cap) {
                            ModelCapability.TEXT   -> MaterialTheme.colorScheme.onSecondaryContainer
                            ModelCapability.VISION -> MaterialTheme.colorScheme.onTertiaryContainer
                            ModelCapability.AUDIO  -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Main ModelsSheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSheet(
    provider: String,
    models: List<String>,
    actions: AiActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Capability-tagged configs parallel to [models]; may be shorter on first load. */
    modelConfigs: List<ModelConfig> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val undo = LocalUndoHost.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        actions.moveModel(provider, from, to)
    }

    var showAddDialog by remember { mutableStateOf(false) }

    // Build a lookup: modelId -> ModelConfig (for badge rendering)
    val configMap = remember(modelConfigs, models) {
        val byId = modelConfigs.associateBy { it.modelId }
        models.associate { id ->
            id to (byId[id] ?: ModelConfig(id, ModelConfig.inferCapabilities(id)))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 88.dp), // leave room for FAB
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Column {
                    Text(
                        text = stringResource(R.string.settings_ai_models_title, provider.uppercase()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_models_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Model list ────────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    itemsIndexed(models, key = { _, m -> m }) { index, modelName ->
                        val config = configMap[modelName]
                            ?: ModelConfig(modelName, setOf(ModelCapability.TEXT))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .reorderableItem(reorderState, modelName, index, haptics)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left: badge + name + capability chips
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (index == 0)
                                                PersonaTheme.statusColors.success.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(end = 2.dp)
                                        ) {
                                            Text(
                                                text = if (index == 0)
                                                    stringResource(R.string.settings_ai_model_primary_badge)
                                                else
                                                    stringResource(R.string.settings_ai_model_fallback_badge, index + 1),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (index == 0)
                                                    PersonaTheme.statusColors.success
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    CapabilityBadgeRow(capabilities = config.capabilities)
                                }

                                // Right: reorder + delete + drag handle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { actions.moveModelUp(provider, modelName) },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = stringResource(R.string.reorder_move_up),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { actions.moveModelDown(provider, modelName) },
                                        enabled = index < models.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = stringResource(R.string.reorder_move_down),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            actions.removeProviderModel(provider, modelName)
                                            undo.offer(
                                                message = context.getString(R.string.settings_ai_model_deleted, modelName),
                                                actionLabel = context.getString(R.string.common_undo)
                                            ) {
                                                actions.addProviderModelAtIndex(provider, modelName, index)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 2.dp, end = 2.dp)
                                            .reorderableHandle(reorderState, modelName, index, haptics),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.reorder_drag_handle),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Floating Action Button ─────────────────────────────────────────
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.settings_ai_add_model_fab)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    // ── Add Model Dialog ───────────────────────────────────────────────────────
    if (showAddDialog) {
        AddProviderModelDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { modelId, capabilities ->
                actions.addProviderModelWithCapabilities(provider, modelId, capabilities)
                showAddDialog = false
            }
        )
    }
}
