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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.knowledge.presentation.model.ExecutionTrace
import java.util.Locale

@Composable
fun ExecutionTraceCard(
    trace: ExecutionTrace,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isRagExpanded by remember { mutableStateOf(false) }
    var isPromptExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val seconds = String.format(Locale.US, "%.1f", trace.totalDurationMs / 1000.0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        // Collapsible Thinking Trigger Chip
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
            modifier = Modifier.wrapContentWidth()
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
                    text = "⚡ خطوات التفكير والقرار (استغرق $seconds ثانية)",
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
                    // 1. 🎯 النية المكتشفة (Intent)
                    TraceStepRow(
                        icon = "🎯",
                        title = "النية المكتشفة (Intent)",
                        badgeText = trace.intent.name,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        detail = "${trace.intent.name} عبر ${trace.intentSourceTier} (${trace.intentLatencyMs}ms، ثقة ${(trace.intentConfidence * 100).toInt()}%)"
                    )

                    // Optional ASR info if audio
                    if (!trace.asrTranscription.isNullOrBlank()) {
                        TraceStepRow(
                            icon = "🎙️",
                            title = "تفريغ الصوت (Voice ASR)",
                            badgeText = "FunASR / Whisper",
                            badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                            badgeTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            detail = trace.asrTranscription
                        )
                    }

                    // Optional Vision info if image
                    if (!trace.visionDescription.isNullOrBlank()) {
                        TraceStepRow(
                            icon = "👁️",
                            title = "تحليل الصورة البصري (Vision)",
                            badgeText = "Multimodal Pass",
                            badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                            badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            detail = trace.visionDescription
                        )
                    }

                    // 2. 📚 المقاطع المسترجعة من الكتالوج (RAG)
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
                                Text(text = "📚", fontSize = 14.sp)
                                Text(
                                    text = "المقاطع المسترجعة من الكتالوج (RAG)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${trace.ragCandidates.size} مقاطع (تطابق: ${(trace.ragBestScore * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (trace.ragCandidates.isNotEmpty()) {
                            Text(
                                text = if (isRagExpanded) "إخفاء المقاطع ▲" else "معاينة المقاطع المسترجعة (${trace.ragCandidates.size}) ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { isRagExpanded = !isRagExpanded }
                                    .padding(vertical = 2.dp)
                            )

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
                                text = "لم تتطلب النية استرجاع مقاطع من قاعدة المعرفة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. 🔄 إعادة البحث (RE_QUERY)
                    TraceStepRow(
                        icon = "🔄",
                        title = "إعادة البحث الدلالي (RE_QUERY)",
                        badgeText = if (trace.reQueryTriggered) "مفعلة (Pass 2)" else "غير مطلوبة (Direct)",
                        badgeColor = if (trace.reQueryTriggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                        badgeTextColor = if (trace.reQueryTriggered) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        detail = if (trace.reQueryTriggered) {
                            buildString {
                                append("تم تفعيل جولة بحث ثانية بنجاح.")
                                if (!trace.reQueryKeyword.isNullOrBlank()) {
                                    append("\n• الكلمات المنقحة: ${trace.reQueryKeyword}")
                                }
                                if (trace.reQueryCandidates.isNotEmpty()) {
                                    append("\n• المقاطع المسترجعة بالجولة الثانية: ${trace.reQueryCandidates.size}")
                                }
                            }
                        } else {
                            "تم الرد في الجولة الأولى مباشرة دون الحاجة لحلقة استرجاع ثانية."
                        }
                    )

                    // 4. 🧠 النموذج المستجيب (LLM)
                    TraceStepRow(
                        icon = "🧠",
                        title = "النموذج المستجيب (LLM)",
                        badgeText = trace.llmAction,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        detail = "${trace.llmProvider} (${trace.llmLatencyMs}ms) • الإجراء النهائي: ${trace.llmAction}"
                    )

                    // 5. 📦 إجراء الطلب (Order Action)
                    val orderActionColor = when (trace.orderAction) {
                        "CREATE" -> Color(0xFF2E7D32)
                        "UPDATE" -> Color(0xFFF57F17)
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
                                Text(text = "📦", fontSize = 14.sp)
                                Text(
                                    text = "إجراء الطلب (Order Action)",
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
                                        text = "📋 تفاصيل الطلب المستخرج آلياً:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = orderActionColor
                                    )
                                    if (!order.customerName.isNullOrBlank()) {
                                        Text(
                                            text = "👤 اسم العميل: ${order.customerName}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.customerPhone.isNullOrBlank()) {
                                        Text(
                                            text = "📞 رقم الهاتف: ${order.customerPhone}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.deliveryAddress.isNullOrBlank()) {
                                        Text(
                                            text = "📍 العنوان: ${order.deliveryAddress}${if (!order.governorate.isNullOrBlank()) " (${order.governorate})" else ""}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!order.orderDetails.isNullOrBlank()) {
                                        Text(
                                            text = "🛍️ المنتجات: ${order.orderDetails}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (order.totalAmount != null) {
                                        val totalWithShipping = order.totalAmount!! + (order.shippingFee ?: 0.0)
                                        Text(
                                            text = "💰 الإجمالي: ${order.totalAmount} ج.م ${if (order.shippingFee != null) "(+ ${order.shippingFee} ج.م شحن = $totalWithShipping ج.م)" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!order.customNotes.isNullOrBlank()) {
                                        Text(
                                            text = "📝 ملاحظات: ${order.customNotes}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "لم يتم اكتشاف تفاصيل طلب مستخرج في هذا الرد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 6. 📄 البرومبت المرسل (System Prompt)
                    if (trace.systemPrompt.isNotBlank()) {
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
                                    Text(text = "📄", fontSize = 14.sp)
                                    Text(
                                        text = "البرومبت المرسل (System Prompt)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isPromptExpanded) "إخفاء البرومبت ▲" else "معاينة البرومبت (${trace.systemPrompt.length} حرف) ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { isPromptExpanded = !isPromptExpanded }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            AnimatedVisibility(visible = isPromptExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "System Prompt Preview:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(trace.systemPrompt))
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "نسخ البرومبت",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = trace.systemPrompt,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
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
    }
}

@Composable
private fun TraceStepRow(
    icon: String,
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
                Text(text = icon, fontSize = 14.sp)
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
                    text = "#${idx + 1} ${candidate.headerContext ?: "معلومة متجر"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "${(candidate.similarityScore * 100).toInt()}% دقة",
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
                Text(
                    text = if (isChunkExpanded) "إخفاء التفاصيل ▲" else "عرض النص كاملاً ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isChunkExpanded = !isChunkExpanded }
                        .padding(top = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}
