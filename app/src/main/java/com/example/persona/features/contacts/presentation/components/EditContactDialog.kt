package com.example.persona.features.contacts.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactDialog(
    contact: ContactEntity,
    onDismissRequest: () -> Unit,
    onConfirmUpdate: (tag: ContactTag, notes: String, phone: String) -> Unit,
    onDeleteContact: (() -> Unit)? = null
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
                text = stringResource(R.string.contacts_edit_dialog_title),
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
                    text = stringResource(R.string.contacts_crm_tag_label),
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
                            label = { Text(stringResource(tag.labelResId), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Phone number field
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text(stringResource(R.string.contacts_edit_dialog_phone_label)) },
                    placeholder = { Text("01xxxxxxxxx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Notes field
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(R.string.contacts_edit_dialog_notes_label)) },
                    placeholder = { Text("مثال: يفضل الشحن الصباحي، عميل دائم...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onDeleteContact != null) {
                    var showConfirmDelete by remember { mutableStateOf(false) }

                    if (showConfirmDelete) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDelete = false },
                            title = {
                                Text(
                                    text = stringResource(R.string.contacts_delete_contact_title),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.contacts_delete_contact_message, contact.contactName)
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showConfirmDelete = false
                                        onDeleteContact()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text(stringResource(R.string.common_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmDelete = false }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.contacts_delete_contact_button))
                    }
                }
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
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
