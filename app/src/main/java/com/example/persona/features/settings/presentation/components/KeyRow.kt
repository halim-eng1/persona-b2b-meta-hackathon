package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        ListItem(
            leadingContent = { StatusDot(status = status) },
            overlineContent = {
                Text(
                    text = stringResource(R.string.settings_ai_key_label, index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            headlineContent = {
                Text(
                    text = if (revealed) apiKey else maskKey(apiKey),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = { StatusText(status = status) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleReveal) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.common_toggle_visibility),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onTest, enabled = status !is VerificationState.Checking) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = stringResource(R.string.settings_ai_ping_btn),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }
}

fun maskKey(key: String): String = when {
    key.length <= 4 -> "••••••••"
    key.length < 12 -> key.take(2) + "••••••••" + key.takeLast(2)
    else -> key.take(6) + "••••••••••••" + key.takeLast(4)
}

@Composable
fun StatusDot(status: VerificationState, modifier: Modifier = Modifier) {
    when (status) {
        VerificationState.Idle -> {
            Box(
                modifier = modifier
                    .size(12.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        VerificationState.Checking -> {
            CircularProgressIndicator(
                modifier = modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        }
        is VerificationState.Success -> {
            Box(
                modifier = modifier
                    .size(12.dp)
                    .background(PersonaTheme.statusColors.success, CircleShape)
            )
        }
        is VerificationState.Error -> {
            Box(
                modifier = modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
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
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = PersonaTheme.statusColors.success,
                modifier = modifier
            )
        }
        is VerificationState.Error -> {
            Text(
                text = status.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
    }
}
