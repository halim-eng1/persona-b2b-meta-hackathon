package com.example.persona.features.knowledge.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry

private data class ParsedProductKnowledge(
    val title: String,
    val price: String?,
    val category: String?,
    val variations: String?,
    val inStock: Boolean?
)

private fun tryParseProduct(text: String): ParsedProductKnowledge? {
    if (!text.contains("[منتج]:")) return null
    val parts = text.split("|").map { it.trim() }
    var title = ""
    var price: String? = null
    var category: String? = null
    var variations: String? = null
    var inStock: Boolean? = null

    for (part in parts) {
        when {
            part.startsWith("[منتج]:") -> title = part.removePrefix("[منتج]:").trim()
            part.startsWith("[السعر]:") -> price = part.removePrefix("[السعر]:").trim()
            part.startsWith("[التصنيف]:") -> category = part.removePrefix("[التصنيف]:").trim()
            part.startsWith("[المقاسات والألوان]:") -> variations = part.removePrefix("[المقاسات والألوان]:").trim()
            part.startsWith("[المخزون]:") -> {
                val stockStr = part.removePrefix("[المخزون]:").trim()
                inStock = stockStr.contains("متوفر") && !stockStr.contains("نفد")
            }
        }
    }
    return if (title.isNotBlank()) {
        ParsedProductKnowledge(title, price, category, variations, inStock)
    } else null
}

private data class ParsedQaKnowledge(
    val question: String,
    val answer: String
)

private fun tryParseQa(text: String): ParsedQaKnowledge? {
    if (text.startsWith("سؤال:") && text.contains("\nإجابة:")) {
        val q = text.substringAfter("سؤال:").substringBefore("\nإجابة:").trim()
        val a = text.substringAfter("\nإجابة:").trim()
        if (q.isNotBlank() && a.isNotBlank()) {
            return ParsedQaKnowledge(q, a)
        }
    }
    return null
}

@Composable
fun KnowledgeEntryRow(
    entry: KnowledgeEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = stringResource(R.string.knowledge_delete_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.knowledge_delete_dialog_message)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.knowledge_delete_confirm_btn))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val parsedProduct = remember(entry.chunkText) { tryParseProduct(entry.chunkText) }
    val parsedQa = remember(entry.chunkText) { if (parsedProduct == null) tryParseQa(entry.chunkText) else null }

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
                when {
                    parsedProduct != null -> {
                        // Structured Commercial Product Layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = parsedProduct.category ?: stringResource(R.string.knowledge_source_manual),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            parsedProduct.inStock?.let { inStock ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (inStock) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = if (inStock) stringResource(R.string.knowledge_input_in_stock)
                                        else stringResource(R.string.knowledge_input_out_of_stock),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (inStock) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            parsedProduct.price?.let { price ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "\u202A$price\u202C",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = parsedProduct.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!parsedProduct.variations.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = parsedProduct.variations,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    parsedQa != null -> {
                        // Trained Q&A Layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.knowledge_source_qa),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${stringResource(R.string.knowledge_question_label)} ${parsedQa.question}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${stringResource(R.string.knowledge_answer_label)} ${parsedQa.answer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    else -> {
                        // Free Text or General Policy Layout
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

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeTextColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

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
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons with WCAG >= 48dp touch targets
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
