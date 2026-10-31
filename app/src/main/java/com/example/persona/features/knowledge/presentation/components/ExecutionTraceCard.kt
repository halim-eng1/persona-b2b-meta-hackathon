package com.example.persona.features.knowledge.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.knowledge.presentation.model.ExecutionTrace
import com.example.persona.ui.theme.statusColors
import java.util.Locale

@Composable
fun ExecutionTraceCard(
    trace: ExecutionTrace,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isRagExpanded by remember { mutableStateOf(false) }

    val seconds = String.format(Locale.US, "%.1f", trace.totalDurationMs / 1000.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        // Collapsible Thinking Trigger Chip
        val expandedDesc = stringResource(R.string.common_expanded)
        val collapsedDesc = stringResource(R.string.common_collapsed)
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            color = if (isExpanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            },
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .wrapContentWidth()
                .semantics {
                    stateDescription = if (isExpanded) expandedDesc else collapsedDesc
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.trace_thinking_steps, seconds),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expanded Drawer
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Detected Intent
                    TraceStepRow(
                        icon = Icons.Default.AdsClick,
                        title = stringResource(R.string.trace_intent_title),
                        badgeText = trace.intent.name,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        detail = "${trace.intent.name} • ${trace.intentSourceTier} (${trace.intentLatencyMs}ms, ${(trace.intentConfidence * 100).toInt()}%)"
                    )

                    // Optional ASR info if audio
                    if (!trace.asrTranscription.isNullOrBlank()) {
                        TraceStepRow(
                            icon = Icons.Default.Mic,
                            title = stringResource(R.string.trace_asr_title),
                            badgeText = "FunASR / Whisper",
                            badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                            badgeTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            detail = trace.asrTranscription
                        )
                    }

                    // Optional Vision info if image
                    if (!trace.visionDescription.isNullOrBlank()) {
                        TraceStepRow(
                            icon = Icons.Default.Visibility,
                            title = stringResource(R.string.trace_vision_title),
                            badgeText = "Multimodal Pass",
                            badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                            badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            detail = trace.visionDescription
                        )
                    }

                    // 2. Retrieved Catalog Chunks
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.trace_rag_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.trace_rag_match_format,
                                        trace.ragCandidates.size,
                                        (trace.ragBestScore * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (trace.ragCandidates.isNotEmpty()) {
                            TextButton(
                                onClick = { isRagExpanded = !isRagExpanded },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text(
                                    text = if (isRagExpanded) {
                                        stringResource(R.string.trace_rag_collapse)
                                    } else {
                                        stringResource(R.string.trace_rag_expand, trace.ragCandidates.size)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            AnimatedVisibility(visible = isRagExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    trace.ragCandidates.forEachIndexed { idx, candidate ->
                                        CandidateChunkItem(idx = idx, candidate = candidate)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.trace_rag_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Re-Query
                    val pass2ExecutedStr = stringResource(R.string.trace_requery_pass2_executed)
                    val requeryDirectStr = stringResource(R.string.trace_requery_direct)
                    TraceStepRow(
                        icon = Icons.Default.Autorenew,
                        title = stringResource(R.string.trace_requery_title),
                        badgeText = if (trace.reQueryTriggered) {
                            stringResource(R.string.trace_requery_active)
                        } else {
                            stringResource(R.string.trace_requery_inactive)
                        },
                        badgeColor = if (trace.reQueryTriggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                        badgeTextColor = if (trace.reQueryTriggered) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        detail = if (trace.reQueryTriggered) {
                            buildString {
                                append(pass2ExecutedStr)
                                if (!trace.reQueryKeyword.isNullOrBlank()) {
                                    append("\n• ")
                                    append(stringResource(R.string.trace_requery_keyword, trace.reQueryKeyword))
                                }
                                if (trace.reQueryCandidates.isNotEmpty()) {
                                    append("\n• ")
                                    append(stringResource(R.string.trace_requery_chunks, trace.reQueryCandidates.size))
                                }
                            }
                        } else {
                            requeryDirectStr
                        }
                    )

                    // 4. Responding Model (LLM)
                    TraceStepRow(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.trace_llm_title),
                        badgeText = trace.llmAction,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        detail = "${trace.llmProvider} (${trace.llmLatencyMs}ms) • Action: ${trace.llmAction}"
                    )

                    // 5. Order Action
                    val orderActionColor = when (trace.orderAction) {
                        "CREATE" -> MaterialTheme.statusColors.completed
                        "UPDATE" -> MaterialTheme.statusColors.pending
                        "CANCEL" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = orderActionColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.trace_order_action_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = orderActionColor.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, orderActionColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = trace.orderAction,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = orderActionColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (trace.extractedOrder != null) {
                            val order = trace.extractedOrder
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    orderActionColor.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.trace_order_details_header),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = orderActionColor
                                    )
                                    if (!order.customerName.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.trace_order_customer_name, order.customerName),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.customerPhone.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.trace_order_customer_phone, order.customerPhone),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.deliveryAddress.isNullOrBlank()) {
                                        val addr = "${order.deliveryAddress}${if (!order.governorate.isNullOrBlank()) " (${order.governorate})" else ""}"
                                        Text(
                                            text = stringResource(R.string.trace_order_address, addr),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.orderDetails.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.trace_order_products, order.orderDetails),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    val amount = order.totalAmount
                                    if (amount != null) {
                                        val totalWithShipping = amount + (order.shippingFee ?: 0.0)
                                        val totalText = if (order.shippingFee != null) {
                                            stringResource(
                                                R.string.trace_order_total_with_shipping,
                                                amount.toString(),
                                                order.shippingFee.toString(),
                                                totalWithShipping.toString()
                                            )
                                        } else {
                                            amount.toString()
                                        }
                                        Text(
                                            text = stringResource(R.string.trace_order_total, totalText),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!order.customNotes.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.trace_order_notes, order.customNotes),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.trace_order_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 6. Token Consumption
                    TraceStepRow(
                        icon = Icons.Default.Analytics,
                        title = stringResource(R.string.trace_tokens_title),
                        badgeText = stringResource(R.string.trace_tokens_badge, trace.totalTokens),
                        badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                        badgeTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        detail = stringResource(
                            R.string.trace_tokens_detail,
                            trace.promptTokens,
                            trace.completionTokens,
                            trace.totalTokens
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TraceStepRow(
    icon: ImageVector,
    title: String,
    badgeText: String,
    badgeColor: Color,
    badgeTextColor: Color,
    detail: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CandidateChunkItem(idx: Int, candidate: RetrievalCandidate) {
    var isChunkExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${idx + 1} ${candidate.headerContext ?: stringResource(R.string.trace_chunk_default_header)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = stringResource(R.string.trace_chunk_accuracy, (candidate.similarityScore * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = candidate.chunkText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isChunkExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            if (candidate.chunkText.length > 180) {
                TextButton(
                    onClick = { isChunkExpanded = !isChunkExpanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                ) {
                    Text(
                        text = if (isChunkExpanded) {
                            stringResource(R.string.trace_chunk_collapse)
                        } else {
                            stringResource(R.string.trace_chunk_expand)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
