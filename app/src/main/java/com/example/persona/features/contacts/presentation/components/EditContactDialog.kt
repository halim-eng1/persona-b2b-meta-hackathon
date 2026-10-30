package com.example.persona.features.contacts.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactDialog(
    contact: ContactEntity,
    onDismissRequest: () -> Unit,
    onConfirmUpdate: (tag: ContactTag, notes: String, phone: String) -> Unit
) {
    var selectedTag by remember {
        mutableStateOf(runCatching { ContactTag.valueOf(contact.tag) }.getOrDefault(ContactTag.REGULAR))
    }
    var phoneText by remember { mutableStateOf(contact.phoneNumber) }
    var notesText by remember { mutableStateOf(contact.notes) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "تعديل بيانات العميل",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = contact.contactName,
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary)
                )

                // Tag selection
                Text(
                    text = "تصنيف العميل (CRM Tag):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ContactTag.entries.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag.labelAr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Phone number field
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text("رقم الهاتف") },
                    placeholder = { Text("01xxxxxxxxx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes field
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("ملاحظات التاجر") },
                    placeholder = { Text("مثال: يفضل الشحن الصباحي، عميل دائم...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmUpdate(selectedTag, notesText.trim(), phoneText.trim())
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("إلغاء")
            }
        }
    )
}
