package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.persona.core.database.entity.OrderEntity

/**
 * Confirmation dialog for cancelling orders with mandatory cancellation reason
 * to prevent accidental status changes and track lost sales causes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CancelOrderDialog(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onConfirmCancel: (reason: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReason by remember { mutableStateOf("") }
    var customReason by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val quickReasons = listOf(
        "العميل غير رأيه",
        "تكرار بالخطأ",
        "خارج نطاق التوصيل",
        "لم يرد على مكالمة التأكيد",
        "السعر غير مناسب",
        "المنتج غير متوفر بالمخزن"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تأكيد إلغاء الطلب #${order.id}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val customerName = order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName
                Text(
                    text = "هل أنت متأكد من إلغاء طلب العميل: $customerName؟ يرجى تحديد سبب الإلغاء:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Quick reason chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = {
                                selectedReason = if (selectedReason == reason) "" else reason
                                if (selectedReason.isNotEmpty()) {
                                    customReason = ""
                                    errorMessage = null
                                }
                            },
                            label = { Text(reason, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customReason,
                    onValueChange = {
                        customReason = it
                        if (it.isNotBlank()) {
                            selectedReason = ""
                            errorMessage = null
                        }
                    },
                    label = { Text("أو اكتب سبباً آخر...") },
                    placeholder = { Text("تفاصيل سبب الإلغاء") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("تراجع")
                    }

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()

                            val finalReason = selectedReason.ifBlank { customReason.trim() }
                            if (finalReason.isBlank()) {
                                errorMessage = "يرجى اختيار أو كتابة سبب الإلغاء"
                                return@Button
                            }
                            errorMessage = null
                            onConfirmCancel(finalReason)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "تأكيد الإلغاء",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}
