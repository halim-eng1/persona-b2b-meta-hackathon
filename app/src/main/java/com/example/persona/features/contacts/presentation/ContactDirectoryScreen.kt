package com.example.persona.features.contacts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.contacts.presentation.components.ContactCard
import com.example.persona.features.contacts.presentation.components.ContactStatsBanner
import com.example.persona.features.contacts.presentation.components.ContactTagFilterChips
import com.example.persona.features.contacts.presentation.components.EditContactDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDirectoryScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToChat: ((contactName: String, sourceClass: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "دليل العملاء (CRM)",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة عميل جديد")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("ابحث بالاسم أو رقم الهاتف...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "مسح",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Tag Filter Chips (VIP, REGULAR, PROBLEMATIC, BLOCKED)
            ContactTagFilterChips(
                selectedTag = state.selectedTagFilter,
                onTagSelected = { viewModel.onTagFilterSelected(it) }
            )

            // Customer Stats Summary Banner
            ContactStatsBanner(summary = state.statsSummary)

            // Contacts List / Empty State
            if (state.contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.searchQuery.isNotBlank() || state.selectedTagFilter != null) {
                                "لا توجد نتائج مطابقة للبحث"
                            } else {
                                "لا يوجد عملاء مسجلين حالياً"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المحادثات الواردة من واتساب تسجل تلقائياً هنا",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = state.contacts,
                        key = { it.id }
                    ) { contact ->
                        ContactCard(
                            contact = contact,
                            onEditClicked = { viewModel.onSelectContactForEdit(contact) },
                            onNavigateToChat = if (onNavigateToChat != null) {
                                { name -> onNavigateToChat(name, "BUSINESS") }
                            } else null
                        )
                    }
                }
            }
        }
    }

    // Edit contact dialog
    state.selectedContactForEdit?.let { contactToEdit ->
        EditContactDialog(
            contact = contactToEdit,
            onDismissRequest = { viewModel.onSelectContactForEdit(null) },
            onConfirmUpdate = { newTag, newNotes, newPhone ->
                viewModel.onUpdateContact(
                    id = contactToEdit.id,
                    newTag = newTag,
                    newNotes = newNotes,
                    newPhone = newPhone
                )
            }
        )
    }

    // Add new contact dialog
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("") }
        var newNotes by remember { mutableStateOf("") }
        var newTag by remember { mutableStateOf(ContactTag.REGULAR) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة عميل جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("اسم العميل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("ملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.onSaveNewContact(newName, newPhone, newTag, newNotes)
                            showAddDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
