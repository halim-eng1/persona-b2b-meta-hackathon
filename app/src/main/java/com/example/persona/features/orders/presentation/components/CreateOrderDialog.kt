package com.example.persona.features.orders.presentation.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.example.persona.R
import com.example.persona.features.orders.domain.CurrencyFormatter
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.theme.PersonaPrimitives
import kotlinx.coroutines.launch

/**
 * Modern B2B Modal Bottom Sheet for manual order creation.
 *
 * Engineered specifically for Egyptian retail merchants:
 * - ModalBottomSheet layout instead of cramped floating Dialog.
 * - Sticky bottom summary & action bar (always visible above keyboard & scroll).
 * - Visual chunking into 2 compact cards (Customer/Delivery + Order/Financials).
 * - 1-Tap "Smart Paste" auto-extracting WhatsApp messages into form fields.
 * - Progressive disclosure for optional notes.
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
    var orderDetails by remember { mutableStateOf("") }
    var totalAmountText by remember { mutableStateOf("") }
    var shippingFeeText by remember { mutableStateOf("") }
    var customNotes by remember { mutableStateOf("") }
    var showNotesField by remember { mutableStateOf(false) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var isPhoneError by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    var isDetailsError by remember { mutableStateOf(false) }
    var isPriceError by remember { mutableStateOf(false) }
    var isShippingError by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Memoize live COD calculation
    val (_, _, codTotal) = remember(totalAmountText, shippingFeeText) {
        val sub = EgyptianOrderUtils.parseEgyptianDecimal(totalAmountText) ?: 0.0
        val ship = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0
        Triple(sub, ship, sub + ship)
    }

    // Adaptive contrast colors for Dark Terminal and Outdoor High-Contrast (WCAG AAA >= 7:1)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val codAccentColor = if (isDark) PersonaPrimitives.Emerald400 else PersonaPrimitives.Teal800
    val codContainerColor = if (isDark) {
        PersonaPrimitives.Emerald500.copy(alpha = 0.15f)
    } else {
        PersonaPrimitives.Teal100.copy(alpha = 0.60f)
    }
    val cardContainerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f)
    }

    // ── Smart Auto-Fill from Clipboard ─────────────────────────────────────────
    fun performSmartPaste() {
        val rawText = clipboardManager.getText()?.text?.trim().orEmpty()
        if (rawText.isBlank()) {
            Toast.makeText(context, context.getString(R.string.orders_create_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Extract Phone (ITU-T E.164 compliant)
        val extractedPhone = EgyptianOrderUtils.extractPhoneNumber(rawText)
        if (extractedPhone != null) {
            customerPhone = extractedPhone
            isPhoneError = false
        }

        // 2. Extract Delivery Address (Single comprehensive address field)
        val addressKeywords = listOf("عنوان", "شارع", "ش ", "عمارة", "شقة", "بجوار", "امام", "أمام", "طريق", "حي", "الحي", "ميدان", "منطقة", "دور", "الدور", "address", "street", "building", "floor", "suite", "road", "avenue", "city", "state", "zip")
        val shortAddressRegex = Regex("""\b(?:st|apt|rd|ave)\b""", RegexOption.IGNORE_CASE)
        val isAddressLine: (String) -> Boolean = { line ->
            addressKeywords.any { kw -> line.contains(kw, ignoreCase = true) } || shortAddressRegex.containsMatchIn(line)
        }
        val addressLine = lines.firstOrNull { line -> isAddressLine(line) }
        if (addressLine != null) {
            deliveryAddress = addressLine.replace(Regex("^(العنوان|عنوان|العنوان:|address:|address)\\s*", RegexOption.IGNORE_CASE), "").trim()
        }

        // 4. Extract Pricing
        val priceRegex = Regex(
            """(?:سعر|إجمالي|المبلغ|مبلغ|حساب|اجمالي|total|price|amount)?\s*[:=-]?\s*([$€£¥₹]?\s*[0-9٠-٩]+(?:\.[0-9٠-٩]+)?)\s*(?:ج\.?م|جنيه|egp|le|usd|eur|gbp|aed|sar|[$€£¥₹])?""",
            RegexOption.IGNORE_CASE
        )
        val matchPrice = priceRegex.find(rawText)
        if (matchPrice != null && totalAmountText.isBlank()) {
            val extractedNum = matchPrice.groupValues[1].replace(Regex("""[$€£¥₹\s]"""), "")
            val parsed = EgyptianOrderUtils.parseEgyptianDecimal(extractedNum)
            if (parsed != null && parsed > 0) {
                totalAmountText = EgyptianOrderUtils.formatMoney(parsed)
                isPriceError = false
            }
        }

        // 5. Extract Customer Name
        val nameRegex = Regex("^(?:الاسم|اسم العميل|اسم|name|customer)[:=-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        val explicitName = lines.firstNotNullOfOrNull { line -> nameRegex.find(line)?.groupValues?.get(1)?.trim() }
        if (explicitName != null) {
            customerName = explicitName
            isNameError = false
        } else if (customerName.isBlank() && lines.isNotEmpty()) {
            val firstLine = lines.first()
            if (!EgyptianOrderUtils.isValidPhoneNumber(firstLine) && !isAddressLine(firstLine)) {
                customerName = firstLine
                isNameError = false
            }
        }

        // 6. Remaining Lines as Items
        val remainingLines = lines.filter { line ->
            line != addressLine &&
            !line.contains(extractedPhone ?: "___NONE___") &&
            !line.startsWith("الاسم") &&
            !line.startsWith("name", ignoreCase = true) &&
            line != explicitName
        }
        if (remainingLines.isNotEmpty() && orderDetails.isBlank()) {
            orderDetails = remainingLines.joinToString("\n")
            isDetailsError = false
        }

        validationError = null
        Toast.makeText(context, context.getString(R.string.orders_create_clipboard_applied), Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Scrollable Body ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.orders_create_manual_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }

                // Smart Paste Action Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { performSmartPaste() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPasteGo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.orders_create_smart_paste),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.orders_create_smart_paste_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accessible Validation Error Banner
                validationError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── Card 1: Customer & Delivery Info (Chunk 1) ────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardContainerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.orders_create_section_customer),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Phone Number First (Primary identifier in Egyptian commerce)
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = {
                                customerPhone = it
                                validationError = null
                                isPhoneError = false
                            },
                            isError = isPhoneError,
                            label = { Text(stringResource(R.string.orders_customer_phone_label)) },
                            placeholder = { Text("+1 555 123 4567 / +20 101 234 5678") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Customer Name
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Detailed Delivery Address
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
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Card 2: Items & Financials (Chunk 2) ──────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardContainerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.orders_create_section_items),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Order Items
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
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Price & Shipping Row
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
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Progressive Disclosure: Courier notes toggle
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!showNotesField && customNotes.isBlank()) {
                            TextButton(
                                onClick = { showNotesField = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.orders_create_add_notes),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        AnimatedVisibility(visible = showNotesField || customNotes.isNotBlank()) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
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
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Sticky Persistent Bottom Action Bar ───────────────────────────
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Live COD calculation summary pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = codContainerColor,
                        border = if (isDark) BorderStroke(1.dp, PersonaPrimitives.Emerald400.copy(alpha = 0.35f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = codAccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.orders_total_cod_summary, CurrencyFormatter.formatAmountWithCurrency(codTotal)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = codAccentColor
                                )
                            }
                            if (shippingFeeText.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = codAccentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val parsedShippingAmount = EgyptianOrderUtils.parseEgyptianDecimal(shippingFeeText) ?: 0.0
                                    Text(
                                        text = CurrencyFormatter.formatAmountWithCurrency(parsedShippingAmount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = codAccentColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // Main Submit CTA Button (Always reachable)
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
                            if (phone.isNotEmpty() && !EgyptianOrderUtils.isValidPhoneNumber(phone)) {
                                validationError = context.getString(R.string.orders_error_invalid_phone)
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

                            val normalizedPhone = EgyptianOrderUtils.extractPhoneNumber(phone) ?: phone

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
                                null,
                                details,
                                parsedTotal,
                                parsedShipping,
                                customNotes.trim().ifBlank { null }
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.orders_create_btn),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
