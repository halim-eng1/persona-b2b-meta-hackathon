package com.example.persona.features.knowledge.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry

@Composable
fun KnowledgeEntryRow(
    entry: KnowledgeEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeColor = when (entry.sourceType) {
                        "CORE_DOCUMENT" -> MaterialTheme.colorScheme.secondaryContainer
                        "MANUAL_ENTRY" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    val badgeText = when (entry.sourceType) {
                        "CORE_DOCUMENT" -> stringResource(R.string.knowledge_source_core)
                        "MANUAL_ENTRY" -> stringResource(R.string.knowledge_source_manual)
                        else -> stringResource(R.string.knowledge_source_offer)
                    }
                    val badgeTextColor = when (entry.sourceType) {
                        "CORE_DOCUMENT" -> MaterialTheme.colorScheme.onSecondaryContainer
                        "MANUAL_ENTRY" -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }

                    SuggestionChip(
                        onClick = {},
                        label = { Text(text = badgeText, fontSize = 10.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = badgeColor,
                            labelColor = badgeTextColor
                        ),
                        border = null
                    )

                    if (!entry.headerContext.isNullOrBlank()) {
                        Text(
                            text = entry.headerContext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entry.chunkText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (entry.sourceType != "CORE_DOCUMENT") {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
