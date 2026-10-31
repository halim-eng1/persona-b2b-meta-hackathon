package com.example.persona.features.settings.presentation.components.rows

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.persona.ui.theme.PersonaTheme

@Composable
fun StatusRow(
    title: String,
    isOk: Boolean,
    okText: String,
    missingText: String,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = if (isOk) okText else missingText,
                color = if (isOk) PersonaTheme.statusColors.success else MaterialTheme.colorScheme.error
            )
        },
        leadingContent = {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) PersonaTheme.statusColors.success else MaterialTheme.colorScheme.error
            )
        },
        trailingContent = trailingAction,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.defaultMinSize(minHeight = 56.dp)
    )
}
