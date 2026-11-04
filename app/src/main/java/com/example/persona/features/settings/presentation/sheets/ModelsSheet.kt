package com.example.persona.features.settings.presentation.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    CapabilityChipDef(ModelCapability.TEXT,     Icons.Default.TextFields, "نص"),
    CapabilityChipDef(ModelCapability.VISION,   Icons.Default.Image,      "صور"),
    CapabilityChipDef(ModelCapability.AUDIO,    Icons.Default.MicNone,    "صوت"),
    CapabilityChipDef(ModelCapability.DECISION, Icons.Default.Bolt,       "قرار")
)

private fun iconForCapability(cap: ModelCapability): ImageVector = when (cap) {
    ModelCapability.TEXT     -> Icons.Default.TextFields
    ModelCapability.VISION   -> Icons.Default.Image
    ModelCapability.AUDIO    -> Icons.Default.MicNone
    ModelCapability.DECISION -> Icons.Default.Bolt
}

/** Infers smart capability pre-selection from the typed model ID. */
private fun inferCapabilities(modelId: String): Set<ModelCapability> =
    ModelConfig.inferCapabilities(modelId)

// ── Add Model Dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProviderModelDialog(
    initialCapability: ModelCapability = ModelCapability.TEXT,
    onDismiss: () -> Unit,
    onConfirm: (modelId: String, capabilities: Set<ModelCapability>) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var modelInput by rememberSaveable { mutableStateOf("") }
    var selectedCaps by remember { mutableStateOf(setOf(initialCapability)) }
    var userHasManuallyEditedCaps by remember { mutableStateOf(false) }

    // Smart pre-selection: re-infer when user types, only if user hasn't manually customized chips
    LaunchedEffect(modelInput) {
        if (!userHasManuallyEditedCaps && modelInput.isNotBlank()) {
            val inferred = inferCapabilities(modelInput)
            selectedCaps = inferred
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
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
                    onValueChange = { modelInput = it.replace("|", "") },
                    label = { Text(stringResource(R.string.settings_ai_add_model_label)) },
                    placeholder = { Text("e.g. muse-spark-1.3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                                    userHasManuallyEditedCaps = true
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
                            selectedCaps.ifEmpty { setOf(initialCapability) }
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        capabilities.sortedBy { it.ordinal }.forEach { cap ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (cap) {
                    ModelCapability.TEXT     -> MaterialTheme.colorScheme.secondaryContainer
                    ModelCapability.VISION   -> MaterialTheme.colorScheme.tertiaryContainer
                    ModelCapability.AUDIO    -> MaterialTheme.colorScheme.primaryContainer
                    ModelCapability.DECISION -> MaterialTheme.colorScheme.errorContainer
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
                            ModelCapability.TEXT     -> MaterialTheme.colorScheme.onSecondaryContainer
                            ModelCapability.VISION   -> MaterialTheme.colorScheme.onTertiaryContainer
                            ModelCapability.AUDIO    -> MaterialTheme.colorScheme.onPrimaryContainer
                            ModelCapability.DECISION -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        text = cap.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (cap) {
                            ModelCapability.TEXT     -> MaterialTheme.colorScheme.onSecondaryContainer
                            ModelCapability.VISION   -> MaterialTheme.colorScheme.onTertiaryContainer
                            ModelCapability.AUDIO    -> MaterialTheme.colorScheme.onPrimaryContainer
                            ModelCapability.DECISION -> MaterialTheme.colorScheme.onErrorContainer
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
    modelConfigs: List<ModelConfig> = emptyList(),
    /** Per-capability ordered model configs. */
    capabilityModelConfigs: Map<ModelCapability, List<ModelConfig>> = emptyMap()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val undo = LocalUndoHost.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val initialCapability = remember(modelConfigs) {
        if (modelConfigs.any { ModelCapability.TEXT in it.capabilities }) {
            ModelCapability.TEXT
        } else {
            modelConfigs.flatMap { it.capabilities }.firstOrNull() ?: ModelCapability.TEXT
        }
    }
    var selectedCapability by rememberSaveable { mutableStateOf(initialCapability) }

    val visibleConfigs = remember(selectedCapability, capabilityModelConfigs, modelConfigs, models) {
        val fromCapMap = capabilityModelConfigs[selectedCapability]
        if (!fromCapMap.isNullOrEmpty()) {
            fromCapMap
        } else {
            val matching = modelConfigs.filter { selectedCapability in it.capabilities }
            if (matching.isNotEmpty()) {
                matching
            } else {
                models.map { id ->
                    modelConfigs.firstOrNull { it.modelId == id }
                        ?: ModelConfig(id, ModelConfig.inferCapabilities(id))
                }.filter { selectedCapability in it.capabilities }
            }
        }
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        actions.moveModelForCapability(provider, selectedCapability, from, to)
    }

    var showAddDialog by remember { mutableStateOf(false) }

    val capabilityChips = listOf(
        ModelCapability.TEXT to (Icons.Default.TextFields to R.string.capability_text),
        ModelCapability.VISION to (Icons.Default.Image to R.string.capability_vision),
        ModelCapability.AUDIO to (Icons.Default.MicNone to R.string.capability_audio),
        ModelCapability.DECISION to (Icons.Default.Bolt to R.string.capability_decision)
    )

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

                // ── Capability Filter Chips ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    capabilityChips.forEach { (cap, iconAndLabel) ->
                        val (icon, labelRes) = iconAndLabel
                        val isSelected = cap == selectedCapability
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCapability = cap },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // ── Model list ────────────────────────────────────────────────
                if (visibleConfigs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = iconForCapability(selectedCapability),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_ai_no_models_for_capability),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(visibleConfigs, key = { _, m -> m.modelId }) { index, config ->
                            val modelName = config.modelId

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .reorderableItem(reorderState, modelName, index, haptics)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // TOP ROW: Model ID (start, weight 1f) + Priority Badge (end)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (index == 0)
                                                PersonaTheme.statusColors.success.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
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
                                    }

                                    // BOTTOM ROW: Badges & Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Start side: Capability badges in a Row with Modifier.weight(1f)
                                        CapabilityBadgeRow(
                                            capabilities = config.capabilities,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // End side: Fixed action icons (Move Up, Move Down, Delete, Drag Handle)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { actions.moveModelUpForCapability(provider, selectedCapability, modelName) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = stringResource(R.string.reorder_move_up),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { actions.moveModelDownForCapability(provider, selectedCapability, modelName) },
                                                enabled = index < visibleConfigs.size - 1,
                                                modifier = Modifier.size(36.dp)
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
                                                        actions.addProviderModelWithCapabilities(provider, modelName, config.capabilities)
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = stringResource(R.string.common_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
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
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }

    // ── Add Model Dialog ───────────────────────────────────────────────────────
    if (showAddDialog) {
        AddProviderModelDialog(
            initialCapability = selectedCapability,
            onDismiss = { showAddDialog = false },
            onConfirm = { modelId, capabilities ->
                actions.addProviderModelWithCapabilities(provider, modelId, capabilities)
                showAddDialog = false
            }
        )
    }
}
