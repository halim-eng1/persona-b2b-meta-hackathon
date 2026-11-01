package com.example.persona.features.settings.presentation.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.features.settings.domain.repository.ModelCapability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackOrderSheet(
    order: List<String>,
    keyCounts: Map<String, Int>,
    onMoveUp: (provider: String) -> Unit,
    onMoveDown: (provider: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    selectedCapability: ModelCapability = ModelCapability.TEXT,
    onSelectCapability: (ModelCapability) -> Unit = {}
) {
    ProviderPrioritySheet(
        selectedCapability = selectedCapability,
        onSelectCapability = onSelectCapability,
        order = order,
        keyCounts = keyCounts,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onDismiss = onDismiss,
        modifier = modifier,
        onMove = onMove
    )
}
