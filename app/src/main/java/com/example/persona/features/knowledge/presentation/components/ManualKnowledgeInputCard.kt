package com.example.persona.features.knowledge.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualKnowledgeInputCard(
    inputQuery: String,
    onInputQueryChanged: (String) -> Unit,
    isTemporaryOffer: Boolean,
    onTemporaryOfferToggled: (Boolean) -> Unit,
    isProcessing: Boolean,
    errorMessage: String?,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputMode by remember { mutableStateOf(0) } // 0 = Structured Catalog, 1 = Free Text / Policy

    // Structured fields
    var productName by remember { mutableStateOf("") }
    var priceEgp by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ملابس") }
    var variations by remember { mutableStateOf("") }
    var inStock by remember { mutableStateOf(true) }

    val categories = listOf("ملابس", "أحذية", "إلكترونيات", "عطور وتجميل", "أطعمة ومشروبات", "خدمات", "أخرى")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Selector Tabs
            TabRow(
                selectedTabIndex = inputMode,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = inputMode == 0,
                    onClick = { inputMode = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("منتج مهيكل (كتالوج)", fontSize = 13.sp, fontWeight = if (inputMode == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = inputMode == 1,
                    onClick = { inputMode = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("نص حر أو سياسة", fontSize = 13.sp, fontWeight = if (inputMode == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            if (inputMode == 0) {
                // Structured Catalog Input
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("اسم المنتج أو الخدمة") },
                    placeholder = { Text("مثال: سويت شيرت أوفر سايز قطن") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priceEgp,
                        onValueChange = {
                            if (it.all { ch -> ch.isDigit() || ch == '.' }) {
                                priceEgp = it
                            }
                        },
                        label = { Text("السعر (ج.م)") },
                        placeholder = { Text("350") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = variations,
                        onValueChange = { variations = it },
                        label = { Text("المقاسات والألوان") },
                        placeholder = { Text("أسود، كحلي / L, XL") },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Category Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "التصنيف:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // In-Stock & Add Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (inStock) "متوفر بالمخزن" else "نفد من المخزن",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (inStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = inStock,
                            onCheckedChange = { inStock = it }
                        )
                    }

                    Button(
                        onClick = {
                            val formatted = buildString {
                                append("[منتج]: ${productName.trim()}")
                                if (priceEgp.isNotBlank()) append(" | [السعر]: ${priceEgp.trim()} ج.م")
                                append(" | [التصنيف]: $selectedCategory")
                                if (variations.isNotBlank()) append(" | [المقاسات والألوان]: ${variations.trim()}")
                                append(" | [المخزون]: ${if (inStock) "متوفر" else "نفد من المخزن"}")
                            }
                            onInputQueryChanged(formatted)
                            onAddEntry()
                            // Reset structured fields
                            productName = ""
                            priceEgp = ""
                            variations = ""
                        },
                        enabled = productName.isNotBlank() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "إضافة للمتجر", fontWeight = FontWeight.Bold)
                        }
                    }
                }

            } else {
                // Free Text / Policy Input
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = onInputQueryChanged,
                    placeholder = { Text(text = stringResource(R.string.knowledge_add_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    minLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.knowledge_source_offer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = isTemporaryOffer,
                            onCheckedChange = onTemporaryOfferToggled
                        )
                    }

                    Button(
                        onClick = onAddEntry,
                        enabled = inputQuery.isNotBlank() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "إضافة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
