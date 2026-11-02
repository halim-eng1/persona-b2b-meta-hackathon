package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.VerificationState
import com.example.persona.ui.theme.PersonaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyRow(
    index: Int,
    apiKey: String,
    status: VerificationState,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    dragHandleModifier: Modifier = Modifier,
    canReorder: Boolean = false
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) {
                currentOnDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = if (canReorder) {
                    {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .then(dragHandleModifier),
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
                } else null,
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusDot(status = status)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (index == 0) {
                                PersonaTheme.statusColors.success.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            }
                        ) {
                            Text(
                                text = if (index == 0) {
                                    stringResource(R.string.settings_ai_model_primary_badge)
                                } else {
                                    stringResource(R.string.settings_ai_key_label, index + 1)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == 0) {
                                    PersonaTheme.statusColors.success
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (revealed) apiKey else maskKey(apiKey),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusText(status = status)
                    }
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleReveal,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.common_toggle_visibility),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onTest,
                            enabled = status !is VerificationState.Checking,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = stringResource(R.string.settings_ai_ping_btn),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (canReorder) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.chat_more_options),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_move_up)) },
                                        onClick = {
                                            menuExpanded = false
                                            onMoveUp?.invoke()
                                        },
                                        enabled = onMoveUp != null,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_move_down)) },
                                        onClick = {
                                            menuExpanded = false
                                            onMoveDown?.invoke()
                                        },
                                        enabled = onMoveDown != null,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.common_delete),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

fun maskKey(key: String): String = when {
    key.length <= 4 -> "••••••••"
    key.length < 12 -> key.take(2) + "••••••••" + key.takeLast(2)
    else -> key.take(6) + "••••••••••••" + key.takeLast(4)
}

@Composable
fun StatusDot(status: VerificationState, modifier: Modifier = Modifier) {
    val description = when (status) {
        VerificationState.Idle -> stringResource(R.string.settings_ai_status_untested)
        VerificationState.Checking -> stringResource(R.string.settings_ai_status_checking)
        is VerificationState.Success -> stringResource(R.string.settings_ai_status_syntax_valid)
        is VerificationState.Error -> stringResource(R.string.settings_ai_status_unknown_error)
    }

    Box(
        modifier = modifier.semantics {
            contentDescription = description
        },
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            VerificationState.Idle -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
            VerificationState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
            }
            is VerificationState.Success -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(PersonaTheme.statusColors.success, CircleShape)
                )
            }
            is VerificationState.Error -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }
    }
}

@Composable
fun StatusText(status: VerificationState, modifier: Modifier = Modifier) {
    when (status) {
        VerificationState.Idle -> {
            Text(
                text = stringResource(R.string.settings_ai_status_untested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
        VerificationState.Checking -> {
            Text(
                text = stringResource(R.string.settings_ai_status_checking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
        is VerificationState.Success -> {
            val text = when {
                status.rawMessage != null -> status.rawMessage
                status.messageRes != 0 && status.formatArgs.isNotEmpty() ->
                    stringResource(status.messageRes, *status.formatArgs.toTypedArray())
                status.messageRes != 0 -> stringResource(status.messageRes)
                else -> stringResource(R.string.settings_ai_status_syntax_valid)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = PersonaTheme.statusColors.success,
                modifier = modifier
            )
        }
        is VerificationState.Error -> {
            val text = when {
                status.rawError != null -> status.rawError
                status.errorRes != 0 && status.formatArgs.isNotEmpty() ->
                    stringResource(status.errorRes, *status.formatArgs.toTypedArray())
                status.errorRes != 0 -> stringResource(status.errorRes)
                else -> stringResource(R.string.settings_ai_status_unknown_error)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
    }
}
