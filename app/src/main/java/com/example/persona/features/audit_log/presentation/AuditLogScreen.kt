package com.example.persona.features.audit_log.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    modifier: Modifier = Modifier,
    viewModel: AuditLogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportFormatDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar ───────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "سجل التدقيق والمراقبة",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "تم تسجيل ${state.filteredItems.size} من أصل ${state.items.size} رد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Export Button
                    FilledTonalButton(
                        onClick = { showExportFormatDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "تصدير السجل",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "تصدير", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Search & Filters Toolbar ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Field
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    placeholder = {
                        Text(text = "بحث بالاسم، رقم الهاتف، أو نص الرسالة...", style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChange("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Filter Rows (Horizontal Scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الطبقة:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "ALL" to stringResource(R.string.common_all),
                        "RULE" to stringResource(R.string.audit_log_rule_tier),
                        "HEURISTIC" to "سياقي",
                        "MODEL" to "نموذج ذكي",
                        "FALLBACK" to "احتياطي"
                    ).forEach { (tierKey, label) ->
                        FilterChip(
                            selected = state.selectedTier == tierKey,
                            onClick = { viewModel.onTierSelected(tierKey) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المزود:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf("ALL" to "الكل", "Gemini" to "Gemini", "Qwen" to "Qwen", "Nvidia" to "NVIDIA", "Groq" to "Groq").forEach { (providerKey, label) ->
                        FilterChip(
                            selected = state.selectedProvider == providerKey,
                            onClick = { viewModel.onProviderSelected(providerKey) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الحالة:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "ALL" to stringResource(R.string.common_all),
                        "SUCCESS" to "ناجح",
                        "ESCALATED_HITL" to stringResource(R.string.audit_log_hitl_escalation),
                        "ERROR" to "خطأ"
                    ).forEach { (statusKey, label) ->
                        FilterChip(
                            selected = state.selectedStatus == statusKey,
                            onClick = { viewModel.onStatusSelected(statusKey) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            // ── Loading ───────────────────────────────────────────────────────
            AnimatedVisibility(visible = state.isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // ── Empty State ───────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !state.isLoading && state.filteredItems.isEmpty(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.items.isEmpty()) "لا توجد ردود مسجلة حتى الآن" else "لا توجد نتائج مطابقة لمعايير البحث",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.items.isEmpty())
                                "ستظهر الردود الآلية وتفاصيل كل محادثة هنا فور إرسالها."
                            else
                                "جرّب تغيير كلمات البحث أو إعادة ضبط الفلاتر المحددة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ── Log List ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !state.isLoading && state.filteredItems.isNotEmpty(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(state.filteredItems, key = { it.id }) { item ->
                        val isExpanded = state.expandedItemIds.contains(item.id)
                        AuditLogCard(
                            item = item,
                            isExpanded = isExpanded,
                            onToggleExpand = { viewModel.toggleExpand(item.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // ── Export Format Selection Dialog ────────────────────────────────────
        if (showExportFormatDialog) {
            AlertDialog(
                onDismissRequest = { showExportFormatDialog = false },
                title = { Text(text = "تصدير سجل التدقيق", fontWeight = FontWeight.Bold) },
                text = { Text(text = "اختر الصيغة المفضلة لتصدير السجلات (${state.filteredItems.size} سجل):") },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportFormatDialog = false
                            viewModel.exportAuditTrail("CSV")
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = "تصدير بتنسيق CSV")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showExportFormatDialog = false
                            viewModel.exportAuditTrail("JSON")
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = "تصدير بتنسيق JSON")
                    }
                }
            )
        }

        // ── Export Payload Display Dialog ─────────────────────────────────────
        state.exportPayload?.let { payload ->
            AlertDialog(
                onDismissRequest = { viewModel.clearExportPayload() },
                title = { Text(text = "تم تجهيز التقرير بنجاح", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "تم إنشاء التقرير (${state.filteredItems.size} رد). يمكنك نسخه أو مشاركته:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            Text(
                                text = payload.take(600) + if (payload.length > 600) "\n... [المزيد]" else "",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Persona Audit Trail", payload)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ التقرير إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                            viewModel.clearExportPayload()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "نسخ للحافظة")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Persona B2B Audit Trail")
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة تقرير المراجعة"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر فتح تطبيق المشاركة", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearExportPayload()
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = "مشاركة")
                    }
                }
            )
        }
    }
}

@Composable
private fun AuditLogCard(
    item: AuditLogItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val localDateTime = item.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val formattedTime = "%02d:%02d".format(localDateTime.hour, localDateTime.minute)
    val formattedDate = "${localDateTime.dayOfMonth}/${localDateTime.monthNumber}/${localDateTime.year}"
    val confidencePct = (item.similarityScore * 100).roundToInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Row 1: Contact Avatar + Name + Confidence & Status Pills ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = item.contactName.take(2).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.contactName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$formattedDate · $formattedTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = item.status)
                    ConfidencePill(confidencePct = confidencePct)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Incoming Message Box ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "الرسالة الواردة من العميل:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.incomingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Generated Reply Box ───────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "رد المساعد الآلي:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.generatedReply.ifBlank { "(لم يتم إرسال رد تلقائي - تم تعليق المحادثة)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Accordion Toggle for Deep Telemetry ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "إخفاء التفاصيل العميقة" else "عرض تشخيص اتخاذ القرار والنموذج",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${item.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Deep Telemetry Expanded View ──────────────────────────────────
            if (isExpanded) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryBadge(label = "طبقة القرار", value = item.gatekeeperTier)
                        TelemetryBadge(label = "النموذج", value = item.providerModel)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryBadge(label = stringResource(R.string.audit_log_tokens_badge), value = "إدخال: ${item.promptTokens} | إخراج: ${item.completionTokens}")
                        TelemetryBadge(label = "سرعة الاستجابة", value = "${item.latencyMs} ملي ثانية")
                    }

                    if (item.decisionReason != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "سبب القرار والمنطق:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.decisionReason,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (item.retrievedChunkIds.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = stringResource(R.string.audit_log_rag_citations),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.retrievedChunkIds.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, bgColor, textColor) = when (status.uppercase()) {
        "SUCCESS" -> Triple("ناجح", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        "ESCALATED_HITL" -> Triple("تدخل بشري", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("خطأ", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ConfidencePill(confidencePct: Int) {
    val color = when {
        confidencePct >= 85 -> MaterialTheme.colorScheme.primary
        confidencePct >= 70 -> MaterialTheme.colorScheme.tertiary
        else                -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "$confidencePct%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
