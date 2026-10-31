package com.example.persona.features.settings.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.persona.features.settings.presentation.components.rememberReorderableLazyListState
import com.example.persona.features.settings.presentation.components.reorderableHandle
import com.example.persona.features.settings.presentation.components.reorderableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackOrderSheet(
    order: List<String>,
    keyCounts: Map<String, Int>,
    onMoveUp: (provider: String) -> Unit,
    onMoveDown: (provider: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState, onMove)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_ai_priority_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_ai_priority_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(order, key = { _, provider -> provider }) { index, provider ->
                    val keys = keyCounts[provider] ?: 0
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .reorderableItem(reorderState, provider, index, haptics)
                    ) {
                        ListItem(
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = provider.uppercase(),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.settings_ai_keys_count, keys),
                                    color = if (keys == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onMoveUp(provider) },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = stringResource(R.string.reorder_move_up)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveDown(provider) },
                                        enabled = index < order.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = stringResource(R.string.reorder_move_down)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 4.dp, end = 2.dp)
                                            .reorderableHandle(reorderState, provider, index, haptics),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.reorder_drag_handle),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
        }
    }
}
