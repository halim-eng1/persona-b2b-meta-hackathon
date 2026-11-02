package com.example.persona.features.orders.presentation.components

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.persona.R
import com.example.persona.features.orders.domain.EgyptianGovernorates
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.theme.PersonaPrimitives
import kotlinx.coroutines.launch

/**
 * Dialog for manual order creation in Egyptian B2B commerce (phone calls, in-store, direct sales).
 *
 * Implements accessible field-level error validation, adaptive keyboard insets,
 * memoized COD recalculations, and WCAG AAA compliant contrast for both Dark Terminal and Outdoor modes.
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
    var isPhoneError by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    var isDetailsError by remember { mutableStateOf(false) }
    var isPriceError by remember { mutableStateOf(false) }
    var isShippingError by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Memoize live COD calculation to avoid regex re-parsing on every recomposition tick
    val (_, _, codTotal) = remember(totalAmountText, shippingFeeText) {
        val sub = EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) ?: 0.0
        val ship = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0
        Triple(sub, ship, sub + ship)
    }

    // Adaptive contrast colors adhering to Persona Dark Terminal & Outdoor High-Contrast modes (WCAG AAA >= 7:1)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val codAccentColor = if (isDark) PersonaPrimitives.Emerald400 else PersonaPrimitives.Teal800
    val codContainerColor = if (isDark) {
        PersonaPrimitives.Emerald500.copy(alpha = 0.15f)
    } else {
        PersonaPrimitives.Teal100.copy(alpha = 0.60f)
    }
    val codBorder = if (isDark) {
        BorderStroke(1.dp, PersonaPrimitives.Emerald400.copy(alpha = 0.35f))
    } else {
        null // Tonal calm: borderless on light background per Persona specs
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
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

                // Accessible validation error banner with LiveRegionMode for TalkBack
                validationError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
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
                        isNameError = false
                    },
                    isError = isNameError,
                    label = { Text(stringResource(R.string.orders_customer_name_label)) },
                    placeholder = { Text(stringResource(R.string.orders_customer_name_placeholder)) },
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
                        isPhoneError = false
                    },
                    isError = isPhoneError,
                    label = { Text(stringResource(R.string.orders_customer_phone_label)) },
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
                        label = { Text(stringResource(R.string.orders_input_gov_label)) },
                        placeholder = { Text(stringResource(R.string.orders_input_gov_placeholder)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = govExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                    label = { Text(stringResource(R.string.orders_input_address_label)) },
                    placeholder = { Text(stringResource(R.string.orders_input_address_placeholder)) },
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
                        isDetailsError = false
                    },
                    isError = isDetailsError,
                    label = { Text(stringResource(R.string.orders_input_items_label)) },
                    placeholder = { Text(stringResource(R.string.orders_input_items_placeholder)) },
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
                            isPriceError = false
                        },
                        isError = isPriceError,
                        label = { Text(stringResource(R.string.orders_input_products_price_label)) },
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
                            isShippingError = false
                        },
                        isError = isShippingError,
                        label = { Text(stringResource(R.string.orders_input_shipping_fee_label)) },
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

                // Dynamic live COD calculation summary (WCAG AAA contrast & Tonal Calm)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = codContainerColor,
                    border = codBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = codAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.orders_total_cod_summary, EgyptianOrderUtils.formatMoney(codTotal)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = codAccentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    label = { Text(stringResource(R.string.orders_customer_notes_label)) },
                    placeholder = { Text(stringResource(R.string.orders_input_notes_placeholder)) },
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
                            validationError = context.getString(R.string.orders_error_name_or_phone_required)
                            isNameError = true
                            isPhoneError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }
                        if (phone.isNotEmpty() && !EgyptianOrderUtils.isValidEgyptianMobile(phone)) {
                            validationError = context.getString(R.string.orders_error_invalid_egyptian_phone)
                            isPhoneError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }
                        if (details.isEmpty()) {
                            validationError = context.getString(R.string.orders_error_items_required)
                            isDetailsError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }
                        if (totalAmountText.isNotBlank() && EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) == null) {
                            validationError = context.getString(R.string.orders_error_invalid_price)
                            isPriceError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }
                        if (shippingFeeText.isNotBlank() && EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) == null) {
                            validationError = context.getString(R.string.orders_error_invalid_shipping)
                            isShippingError = true
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }
                        if (parsedTotal < 0.0 || parsedShipping < 0.0) {
                            validationError = context.getString(R.string.orders_error_negative_amounts)
                            isPriceError = parsedTotal < 0.0
                            isShippingError = parsedShipping < 0.0
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            return@Button
                        }

                        val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(phone) ?: phone

                        validationError = null
                        isNameError = false
                        isPhoneError = false
                        isDetailsError = false
                        isPriceError = false
                        isShippingError = false

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
                        text = stringResource(R.string.orders_create_btn),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
