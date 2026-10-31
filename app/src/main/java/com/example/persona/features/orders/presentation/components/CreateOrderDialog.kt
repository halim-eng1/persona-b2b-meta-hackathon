package com.example.persona.features.orders.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.persona.features.orders.domain.EgyptianGovernorates
import com.example.persona.features.orders.domain.EgyptianOrderUtils

/**
 * Dialog for manual order creation in Egyptian B2B commerce (phone calls, in-store, direct sales).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderDialog(
    onDismiss: () -> Unit,
    onCreate: (
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        governorate: String?,
        orderDetails: String,
        totalAmount: Double,
        shippingFee: Double,
        customNotes: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var selectedGov by remember { mutableStateOf("") }
    var govExpanded by remember { mutableStateOf(false) }
    var orderDetails by remember { mutableStateOf("") }
    var totalAmountText by remember { mutableStateOf("") }
    var shippingFeeText by remember { mutableStateOf("") }
    var customNotes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val subtotal = EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) ?: 0.0
    val shipping = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0
    val codTotal = subtotal + shipping

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
                    Text(
                        text = stringResource(R.string.orders_create_manual_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                validationError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.orders_customer_name_label)) },
                    placeholder = { Text("مثال: محمد أحمد") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = {
                        customerPhone = it
                        validationError = null
                    },
                    label = { Text("رقم الهاتف المحمول (11 رقم) *") },
                    placeholder = { Text("01012345678") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Governorate dropdown
                ExposedDropdownMenuBox(
                    expanded = govExpanded,
                    onExpandedChange = { govExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedGov,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المحافظة") },
                        placeholder = { Text("اختر المحافظة...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = govExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = govExpanded,
                        onDismissRequest = { govExpanded = false }
                    ) {
                        EgyptianGovernorates.ALL.forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov) },
                                onClick = {
                                    selectedGov = gov
                                    govExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("عنوان التوصيل بالتفصيل") },
                    placeholder = { Text("الشارع، رقم العمارة، الشقة، علامة مميزة") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = orderDetails,
                    onValueChange = {
                        orderDetails = it
                        validationError = null
                    },
                    label = { Text("محتويات الطلب والأصناف *") },
                    placeholder = { Text("مثال: 2x تي شيرت قطن أسود L\n1x قميص كتان أبيض XL") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = totalAmountText,
                        onValueChange = {
                            totalAmountText = it
                            validationError = null
                        },
                        label = { Text("سعر المنتجات (ج.م) *") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = shippingFeeText,
                        onValueChange = {
                            shippingFeeText = it
                            validationError = null
                        },
                        label = { Text("الشحن (ج.م)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Dynamic live COD calculation summary
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OrderGreen.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, OrderGreen.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = OrderGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إجمالي التحصيل عند الاستلام: ${EgyptianOrderUtils.formatMoney(codTotal)} ج.م (شامل الشحن)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = OrderGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    label = { Text(stringResource(R.string.orders_customer_notes_label)) },
                    placeholder = { Text("ملاحظات خاصة بالتسليم أو المواعيد") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()

                        val name = customerName.trim()
                        val phone = customerPhone.trim()
                        val details = orderDetails.trim()
                        val parsedTotal = EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) ?: 0.0
                        val parsedShipping = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0

                        if (name.isEmpty() && phone.isEmpty()) {
                            validationError = "يرجى كتابة اسم العميل أو رقم الهاتف"
                            return@Button
                        }
                        if (phone.isNotEmpty() && !EgyptianOrderUtils.isValidEgyptianMobile(phone)) {
                            validationError = "رقم الهاتف غير صالح. يجب أن يتكون من 11 رقماً ويبدأ بـ 010 أو 011 أو 012 أو 015"
                            return@Button
                        }
                        if (details.isEmpty()) {
                            validationError = "يرجى إدخال أصناف ومحتويات الطلب"
                            return@Button
                        }
                        if (totalAmountText.isNotBlank() && EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) == null) {
                            validationError = "يرجى إدخال سعر صحيح للمنتجات"
                            return@Button
                        }
                        if (shippingFeeText.isNotBlank() && EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) == null) {
                            validationError = "يرجى إدخال قيمة صحيحة لمصاريف الشحن"
                            return@Button
                        }
                        if (parsedTotal < 0.0 || parsedShipping < 0.0) {
                            validationError = "لا يمكن أن تكون المبالغ المالية بالسالب"
                            return@Button
                        }

                        val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(phone) ?: phone

                        validationError = null
                        onCreate(
                            name,
                            normalizedPhone,
                            deliveryAddress.trim(),
                            selectedGov.ifBlank { null },
                            details,
                            parsedTotal,
                            parsedShipping,
                            customNotes.trim().ifBlank { null }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إضافة الطلب للنظام",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
