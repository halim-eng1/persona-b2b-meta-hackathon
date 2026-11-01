package com.example.persona.features.knowledge.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.persona.R

private val ProductCategories = listOf("ملابس", "أحذية", "إلكترونيات", "عطور وتجميل", "أطعمة ومشروبات", "خدمات", "أخرى")
private val PolicyCategories = listOf("سياسة", "شحن وتوصيل", "عروض ترويجية", "معلومات عامة", "أخرى")

/**
 * Normalizes Arabic-Indic digits (٠-٩), Persian variants, and localized decimal separators (٫, ،, ,)
 * to standard ASCII digits (0-9) and standard dot (.) decimal point.
 */
internal fun sanitizeArabicIndicPrice(input: String): String {
    return input
        .replace('٠', '0')
        .replace('١', '1')
        .replace('٢', '2')
        .replace('٣', '3')
        .replace('٤', '4')
        .replace('٥', '5')
        .replace('٦', '6')
        .replace('٧', '7')
        .replace('٨', '8')
        .replace('٩', '9')
        .replace('٫', '.')
        .replace('،', '.')
        .replace(',', '.')
        .trim()
}

/**
 * Keyboard-aware, vertically scrollable dialog for adding new knowledge or structured catalog products.
 * Automatically closes as soon as the user taps "إضافة" and triggers the callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKnowledgeEntryDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAddEntry: (formattedText: String, isTemporaryOffer: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    var inputMode by remember { mutableStateOf(0) } // 0 = Structured Catalog, 1 = Free Text / Policy

    // Structured Catalog fields
    var productName by remember { mutableStateOf("") }
    var priceEgp by remember { mutableStateOf("") }
    var selectedProductCategory by remember { mutableStateOf("ملابس") }
    var productDescription by remember { mutableStateOf("") }
    var inStock by remember { mutableStateOf(true) }

    // Free Text / Policy fields
    var freeTitle by remember { mutableStateOf("") }
    var selectedPolicyCategory by remember { mutableStateOf("سياسة") }
    var freeContent by remember { mutableStateOf("") }
    var isTemporaryOffer by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.knowledge_input_card_toggle_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Segmented Button Row: [ منتج مهيكل (كتالوج) ] vs [ نص حر أو سياسة ]
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = inputMode == 0,
                        onClick = { inputMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.knowledge_input_mode_catalog),
                            fontSize = 12.sp,
                            fontWeight = if (inputMode == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    SegmentedButton(
                        selected = inputMode == 1,
                        onClick = { inputMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.knowledge_input_mode_free_text),
                            fontSize = 12.sp,
                            fontWeight = if (inputMode == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                if (inputMode == 0) {
                    // ── Structured Catalog Inputs ──
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text(stringResource(R.string.knowledge_input_product_name_label)) },
                        placeholder = { Text(stringResource(R.string.knowledge_input_product_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = priceEgp,
                        onValueChange = { input ->
                            val normalized = sanitizeArabicIndicPrice(input)
                            if (normalized.isEmpty() || (normalized.count { it == '.' } <= 1 && normalized.all { ch -> ch in '0'..'9' || ch == '.' })) {
                                priceEgp = normalized
                            }
                        },
                        label = { Text(stringResource(R.string.knowledge_input_price_label)) },
                        placeholder = { Text(stringResource(R.string.knowledge_input_price_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.knowledge_input_category_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ProductCategories) { cat ->
                                FilterChip(
                                    selected = selectedProductCategory == cat,
                                    onClick = { selectedProductCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = productDescription,
                        onValueChange = { productDescription = it },
                        label = { Text(stringResource(R.string.knowledge_input_variations_label)) },
                        placeholder = { Text(stringResource(R.string.knowledge_input_variations_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = inStock,
                                role = Role.Switch,
                                onValueChange = { inStock = it }
                            )
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (inStock) stringResource(R.string.knowledge_input_in_stock) else stringResource(R.string.knowledge_input_out_of_stock),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (inStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(checked = inStock, onCheckedChange = null)
                    }
                } else {
                    // ── Free Text / Policy Inputs ──
                    OutlinedTextField(
                        value = freeTitle,
                        onValueChange = { freeTitle = it },
                        label = { Text(stringResource(R.string.knowledge_dialog_free_title)) },
                        placeholder = { Text(stringResource(R.string.knowledge_dialog_free_title_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.knowledge_input_category_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(PolicyCategories) { cat ->
                                FilterChip(
                                    selected = selectedPolicyCategory == cat,
                                    onClick = { selectedPolicyCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = freeContent,
                        onValueChange = { freeContent = it },
                        label = { Text(stringResource(R.string.knowledge_dialog_free_content)) },
                        placeholder = { Text(stringResource(R.string.knowledge_add_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 6
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = isTemporaryOffer,
                                role = Role.Switch,
                                onValueChange = { isTemporaryOffer = it }
                            )
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.knowledge_source_offer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(checked = isTemporaryOffer, onCheckedChange = null)
                    }
                }

                // Action Buttons: "إلغاء" and "إضافة"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    val isAddEnabled = if (inputMode == 0) {
                        productName.isNotBlank()
                    } else {
                        freeContent.isNotBlank() || freeTitle.isNotBlank()
                    }

                    Button(
                        onClick = {
                            if (inputMode == 0) {
                                val formatted = buildString {
                                    append("[منتج]: ${productName.trim()}")
                                    val cleanPrice = sanitizeArabicIndicPrice(priceEgp)
                                    if (cleanPrice.isNotBlank()) append(" | [السعر]: $cleanPrice ج.م")
                                    append(" | [التصنيف]: $selectedProductCategory")
                                    if (productDescription.isNotBlank()) append(" | [المواصفات]: ${productDescription.trim()}")
                                    append(" | [المخزون]: ${if (inStock) "متوفر" else "نفد من المخزن"}")
                                }
                                onAddEntry(formatted, false)
                            } else {
                                val formatted = buildString {
                                    if (freeTitle.isNotBlank()) {
                                        append("[${freeTitle.trim()}]: ")
                                    }
                                    if (selectedPolicyCategory.isNotBlank() && selectedPolicyCategory != "معلومات عامة") {
                                        append("([التصنيف]: $selectedPolicyCategory) ")
                                    }
                                    append(freeContent.trim())
                                }
                                onAddEntry(formatted, isTemporaryOffer)
                            }
                            onDismiss()
                        },
                        enabled = isAddEnabled,
                        modifier = Modifier
                            .weight(1.5f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.common_add),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
