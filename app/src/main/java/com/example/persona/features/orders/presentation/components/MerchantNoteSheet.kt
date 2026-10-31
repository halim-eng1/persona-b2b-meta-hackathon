package com.example.persona.features.orders.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.OrderEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MerchantNoteSheet(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteValue by remember(order.id) { mutableStateOf(order.merchantNote.orEmpty()) }

    val quickNoteSuggestions = listOf(
        "المندوب هيوصل لحضرتك اليوم",
        "يرجى تأكيد المقاس واللون المفضل",
        "العنوان غير واضح، يرجى إرسال اللوكيشن",
        "تم شحن الطلب وسيصلك خلال 48 ساعة"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            val customer = order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName
            Text(
                text = stringResource(R.string.orders_note_sheet_customer_title, customer),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "أي ملاحظة هتكتبها هنا، المساعد الآلي هيبلغها للعميل تلقائياً في أول رسالة على واتساب. يمكنك مسح الملاحظة بالكامل وحفظها لإلغائها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick suggestion chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickNoteSuggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { noteValue = suggestion },
                        label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = noteValue,
                onValueChange = { noteValue = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.orders_note_sheet_placeholder)) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSave(noteValue.trim()) },
                enabled = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.orders_note_sheet_save_btn), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
