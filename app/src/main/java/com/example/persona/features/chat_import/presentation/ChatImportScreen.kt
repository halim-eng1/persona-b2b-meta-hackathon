package com.example.persona.features.chat_import.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.ui.theme.PersonaTheme
import com.example.persona.ui.theme.spacing
import kotlinx.coroutines.launch

/**
 * Stateful entry point for the Knowledge & Chat Import screen.
 * Observes [viewModel] state and handles system callbacks.
 */
@Composable
fun ChatImportScreen(
    viewModel: ChatImportViewModel,
    onImportStarted: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val acceptedMimeTypes = remember {
        arrayOf(
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "application/octet-stream"
        )
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked ->
            val displayName = picked.queryDisplayName(context)
            val isSupported = displayName?.endsWith(".txt", ignoreCase = true) == true ||
                displayName?.endsWith(".md", ignoreCase = true) == true
            if (isSupported || displayName == null) {
                viewModel.onFilePicked(picked, displayName)
            } else {
                viewModel.onUnsupportedFilePicked()
            }
        }
    }

    LaunchedEffect(uiState.isImporting) {
        if (uiState.isImporting) {
            onImportStarted()
        }
    }

    ChatImportContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        onCopyPrompt = { promptText, confirmationMessage ->
            clipboardManager.setText(AnnotatedString(promptText))
            coroutineScope.launch {
                snackbarHostState.showSnackbar(confirmationMessage)
            }
        },
        onDirectInputTextChanged = viewModel::onDirectInputTextChanged,
        onIngestDirectText = viewModel::ingestDirectText,
        onPickFile = { fileLauncher.launch(acceptedMimeTypes) },
        onStartImport = viewModel::startImport,
        modifier = modifier
    )
}

/**
 * Stateless presentation composable for the Knowledge Import screen.
 * Completely decoupled from ViewModel for previews and testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImportContent(
    uiState: ChatImportUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCopyPrompt: (promptText: String, confirmationMessage: String) -> Unit,
    onDirectInputTextChanged: (String) -> Unit,
    onIngestDirectText: () -> Unit,
    onPickFile: () -> Unit,
    onStartImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isDark = isSystemInDarkTheme()
    val cardBorder = if (isDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.import_screen_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(MaterialTheme.spacing.touchTargetMin)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.import_screen_nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = MaterialTheme.spacing.screenHorizontal,
                        vertical = MaterialTheme.spacing.screenVertical
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
            ) {
                // Tactile Bento Header Card (Solid M3 Container - High Contrast in Dark & Outdoor)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.chat_import_studio_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stringResource(R.string.chat_import_hero_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 1: AI Knowledge Architect Prompts
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.chat_import_prompt_generator_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = stringResource(R.string.chat_import_ai_card_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Button 1: Business / Commercial Prompt
                        val businessSnackbarMsg = stringResource(R.string.import_screen_copied_business_snackbar)
                        OutlinedButton(
                            onClick = {
                                onCopyPrompt(
                                    ChatImportPrompts.BUSINESS_KNOWLEDGE_PROMPT,
                                    businessSnackbarMsg
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                            )
                            Text(
                                text = stringResource(R.string.import_screen_copy_business_btn),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Button 2: Personal Assistant / Non-Commercial Prompt
                        val personalSnackbarMsg = stringResource(R.string.import_screen_copied_personal_snackbar)
                        OutlinedButton(
                            onClick = {
                                onCopyPrompt(
                                    ChatImportPrompts.PERSONAL_KNOWLEDGE_PROMPT,
                                    personalSnackbarMsg
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                            )
                            Text(
                                text = stringResource(R.string.import_screen_copy_personal_btn),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Card 2: Direct Text Paste Ingestion Box
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.chat_import_direct_text_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = stringResource(R.string.chat_import_direct_card_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = uiState.directInputText,
                            onValueChange = onDirectInputTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 260.dp),
                            label = {
                                Text(stringResource(R.string.chat_import_direct_text_title))
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.import_screen_paste_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            minLines = 6,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Success Feedback Banner with TalkBack LiveRegion
                        AnimatedVisibility(visible = uiState.directIndexSuccessMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical = MaterialTheme.spacing.s
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = uiState.directIndexSuccessMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Direct Indexing Error Banner with TalkBack LiveRegion
                        AnimatedVisibility(visible = uiState.directIndexError != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical = MaterialTheme.spacing.s
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = uiState.directIndexError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Primary Direct Ingestion Action Button
                        Button(
                            onClick = onIngestDirectText,
                            enabled = uiState.directInputText.isNotBlank() && !uiState.isDirectIndexing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                        ) {
                            if (uiState.isDirectIndexing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(MaterialTheme.spacing.s))
                                Text(stringResource(R.string.import_screen_indexing_indicator))
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                                )
                                Text(
                                    text = stringResource(R.string.import_screen_save_index_btn),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Card 3: Alternative File Upload
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.import_screen_upload_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        OutlinedButton(
                            onClick = onPickFile,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.import_screen_choose_file_desc),
                                modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                            )
                            Text(stringResource(R.string.import_screen_choose_file_btn))
                        }

                        uiState.selectedFileUri?.let { uri ->
                            val resolvedFileName = uiState.selectedFileName
                                ?: uri.lastPathSegment
                                ?: "knowledge.md"

                            Text(
                                text = stringResource(R.string.chat_import_selected_file_label, resolvedFileName),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Button(
                                onClick = onStartImport,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.chat_import_start_icon_desc),
                                    modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                                )
                                Text(
                                    text = stringResource(R.string.import_screen_start_btn),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // File Import Error Message with LiveRegion
                AnimatedVisibility(visible = uiState.error != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = stringResource(R.string.chat_import_error_icon_desc),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = uiState.error.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (uiState.isOwnerNameMissing) {
                                TextButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                                ) {
                                    Text(
                                        text = stringResource(R.string.import_screen_go_settings_btn),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queries [contentResolver] for the human-readable display name of this [Uri]
 * on document pick.
 */
private fun Uri.queryDisplayName(context: Context): String? = try {
    context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
} catch (e: Exception) {
    null
}

@Preview(name = "Chat Import - Dark Terminal", showBackground = true)
@Composable
private fun ChatImportScreenDarkPreview() {
    PersonaTheme(darkTheme = true) {
        ChatImportContent(
            uiState = ChatImportUiState(
                directInputText = "# Store FAQ\n- Shipping: 50 EGP",
                selectedFileName = "store_knowledge.md"
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToSettings = {},
            onCopyPrompt = { _, _ -> },
            onDirectInputTextChanged = {},
            onIngestDirectText = {},
            onPickFile = {},
            onStartImport = {}
        )
    }
}

@Preview(name = "Chat Import - Outdoor High Contrast", showBackground = true)
@Composable
private fun ChatImportScreenLightPreview() {
    PersonaTheme(darkTheme = false) {
        ChatImportContent(
            uiState = ChatImportUiState(
                directInputText = "",
                selectedFileName = null
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToSettings = {},
            onCopyPrompt = { _, _ -> },
            onDirectInputTextChanged = {},
            onIngestDirectText = {},
            onPickFile = {},
            onStartImport = {}
        )
    }
}
