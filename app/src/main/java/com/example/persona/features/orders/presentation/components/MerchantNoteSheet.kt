package com.example.persona.features.orders.presentation.components

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantNoteSheet(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteValue by remember(order.id) { mutableStateOf(order.merchantNote.orEmpty()) }

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
            Text(
                text = "Merchant Note — ${order.customerNameFromLlm ?: order.contactName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This note is injected into the bot's live context, so the assistant shares it with the customer automatically. " +
                    "Leave it empty and save to clear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = noteValue,
                onValueChange = { noteValue = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. المندوب حيوصل بكرة الساعة 3") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(noteValue.trim()) },
                enabled = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save Note", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
