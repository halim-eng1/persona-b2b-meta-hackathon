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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.ui.theme.PersonaTheme
import com.example.persona.ui.theme.spacing
import kotlinx.coroutines.launch

/**
 * Stateful entry point for the Knowledge Base creation screen.
 * Observes [viewModel] state and handles activity/system callbacks.
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
            val isSupported = displayName != null && (
                displayName.endsWith(".txt", ignoreCase = true) ||
                displayName.endsWith(".md", ignoreCase = true) ||
                displayName.endsWith(".json", ignoreCase = true) ||
                displayName.endsWith(".csv", ignoreCase = true)
            )
            if (isSupported) {
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
        onClearSelectedFile = viewModel::clearSelectedFile,
        onStartImport = viewModel::startImport,
        modifier = modifier
    )
}

/**
 * Stateless presentation composable for the Knowledge Base screen.
 * Optimized for single-viewport clarity, tabbed direct text / file upload,
 * and progressive disclosure of AI prompt templates via bottom sheet.
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
    onClearSelectedFile: () -> Unit,
    onStartImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val cardBorder = if (isDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAiPromptSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.import_screen_title),
                        fontWeight = FontWeight.Bold
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
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                // 1. AI Assistant Helper Banner (Progressive Disclosure)
                Surface(
                    onClick = { showAiPromptSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.chat_import_ai_helper_banner_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.chat_import_ai_helper_banner_action),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Segmented Tabs: Direct Text vs File Upload
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                text = stringResource(R.string.chat_import_tab_direct_text),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                text = stringResource(R.string.chat_import_tab_file_upload),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }

                // 3. Tab Content Area
                if (selectedTabIndex == 0) {
                    // TAB 0: Direct Text Ingestion
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        OutlinedTextField(
                            value = uiState.directInputText,
                            onValueChange = onDirectInputTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 280.dp),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.import_screen_paste_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            trailingIcon = {
                                if (uiState.directInputText.isNotEmpty()) {
                                    IconButton(onClick = { onDirectInputTextChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.chat_import_clear_text_desc)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Direct Indexing Success Banner
                        AnimatedVisibility(visible = uiState.directIndexSuccessMessage != null || uiState.directIndexSuccessChunks != null) {
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
                                    val successText = when (val chunks = uiState.directIndexSuccessChunks) {
                                        null -> uiState.directIndexSuccessMessage.orEmpty()
                                        else -> stringResource(R.string.chat_import_direct_index_success, chunks)
                                    }
                                    Text(
                                        text = successText,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Direct Indexing Error Banner
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
                                    val errorText = when (uiState.directIndexError) {
                                        "EMPTY_TEXT" -> stringResource(R.string.chat_import_error_empty_text)
                                        "ENGINE_NOT_READY" -> stringResource(R.string.chat_import_error_engine_not_ready)
                                        "INDEXING_FAILED" -> stringResource(R.string.chat_import_error_indexing_failed)
                                        // Legacy fallback or custom exception message
                                        "يرجى لصق نص المعرفة أولاً" -> stringResource(R.string.chat_import_error_empty_text)
                                        "محرك المعرفة غير مهيأ" -> stringResource(R.string.chat_import_error_engine_not_ready)
                                        "حدث خطأ أثناء حفظ وفهرسة المعرفة. يرجى التحقق من النص والمحاولة مرة أخرى." -> stringResource(R.string.chat_import_error_indexing_failed)
                                        else -> uiState.directIndexError.orEmpty()
                                    }
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Primary Ingestion Action Button (Always in view)
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
                } else {
                    // TAB 1: File Upload Ingestion
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.UploadFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.import_screen_upload_section_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = stringResource(R.string.chat_import_file_supported_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                if (uiState.selectedFileUri == null) {
                                    Button(
                                        onClick = onPickFile,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = MaterialTheme.spacing.s)
                                        )
                                        Text(
                                            text = stringResource(R.string.import_screen_choose_file_btn),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else {
                                    // Selected file card with quick clear
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            val resolvedFileName = uiState.selectedFileName
                                                ?: uiState.selectedFileUri.lastPathSegment
                                                ?: "knowledge.md"
                                            Text(
                                                text = resolvedFileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = onClearSelectedFile,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.chat_import_clear_file_desc),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = onStartImport,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = MaterialTheme.spacing.touchTargetMin)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
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
                    }
                }

                // Error message banner (e.g. unsupported file format or missing owner)
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
                                val errorMessage = when (uiState.error) {
                                    "UNSUPPORTED_FILE" -> stringResource(R.string.chat_import_error_unsupported_file)
                                    "نوع الملف غير مدعوم. اختر ملف بنسق .txt أو .md (Unsupported file type. Please pick a .txt or .md file.)" -> stringResource(R.string.chat_import_error_unsupported_file)
                                    else -> uiState.error.orEmpty()
                                }
                                Text(
                                    text = errorMessage,
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

        // Bottom Sheet: AI Prompt Templates (Organized & Clean)
        if (showAiPromptSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAiPromptSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.chat_import_ai_sheet_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stringResource(R.string.chat_import_ai_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Card A: Business / Store Prompt
                    val businessSnackbarMsg = stringResource(R.string.import_screen_copied_business_snackbar)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = cardBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.import_screen_copy_business_btn),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.chat_import_ai_business_card_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    onCopyPrompt(
                                        ChatImportPrompts.getBusinessPrompt(context),
                                        businessSnackbarMsg
                                    )
                                    showAiPromptSheet = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.import_screen_copy_business_btn)
                                )
                            }
                        }
                    }

                    // Card B: Personal Assistant Prompt
                    val personalSnackbarMsg = stringResource(R.string.import_screen_copied_personal_snackbar)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = cardBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.import_screen_copy_personal_btn),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.chat_import_ai_personal_card_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    onCopyPrompt(
                                        ChatImportPrompts.getPersonalPrompt(context),
                                        personalSnackbarMsg
                                    )
                                    showAiPromptSheet = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.import_screen_copy_personal_btn)
                                )
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
            onClearSelectedFile = {},
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
            onClearSelectedFile = {},
            onStartImport = {}
        )
    }
}
