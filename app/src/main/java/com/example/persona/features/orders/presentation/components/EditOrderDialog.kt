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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
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
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.orders.domain.EgyptianGovernorates
import com.example.persona.features.orders.domain.EgyptianOrderUtils

/**
 * Dialog for editing existing orders (correcting AI hallucinated names, addresses, or prices).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOrderDialog(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onSave: (
        orderId: Long,
        customerName: String?,
        customerPhone: String,
        deliveryAddress: String?,
        governorate: String?,
        orderDetails: String?,
        totalAmount: Double,
        shippingFee: Double
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var customerName by remember { mutableStateOf(order.customerNameFromLlm.orEmpty()) }
    var customerPhone by remember {
        mutableStateOf(
            EgyptianOrderUtils.extractEgyptianMobilePhone(order.customerPhone)
                ?: order.customerPhone.ifBlank { order.contactName }
        )
    }
    var deliveryAddress by remember { mutableStateOf(order.deliveryAddress.orEmpty()) }
    var selectedGov by remember { mutableStateOf(order.governorate.orEmpty()) }
    var govExpanded by remember { mutableStateOf(false) }
    var orderDetails by remember { mutableStateOf(order.orderDetails.orEmpty()) }
    var totalAmountText by remember {
        mutableStateOf(if (order.totalAmount > 0) EgyptianOrderUtils.formatMoney(order.totalAmount) else "")
    }
    var shippingFeeText by remember {
        mutableStateOf(if (order.shippingFee > 0) EgyptianOrderUtils.formatMoney(order.shippingFee) else "")
    }
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
                        text = stringResource(R.string.orders_edit_order_title, order.id),
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
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
                    label = { Text("رقم الهاتف (للمندوب)") },
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
                    label = { Text("محتويات الطلب والأصناف") },
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
                        label = { Text("سعر المنتجات (ج.م)") },
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()

                        val phone = customerPhone.trim()
                        if (phone.isNotEmpty() && !EgyptianOrderUtils.isValidEgyptianMobile(phone)) {
                            validationError = "رقم الهاتف غير صالح. يجب أن يتكون من 11 رقماً ويبدأ بـ 010 أو 011 أو 012 أو 015"
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

                        val total = EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) ?: 0.0
                        val shippingFeeVal = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0

                        if (total < 0.0 || shippingFeeVal < 0.0) {
                            validationError = "لا يمكن أن تكون المبالغ المالية بالسالب"
                            return@Button
                        }

                        val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(phone) ?: phone

                        validationError = null
                        onSave(
                            order.id,
                            customerName.trim().ifBlank { null },
                            normalizedPhone,
                            deliveryAddress.trim().ifBlank { null },
                            selectedGov.ifBlank { null },
                            orderDetails.trim().ifBlank { null },
                            total,
                            shippingFeeVal
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "حفظ التعديلات",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
