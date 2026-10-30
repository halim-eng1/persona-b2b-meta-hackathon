package com.example.persona.features.chat_import.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

const val BUSINESS_KNOWLEDGE_PROMPT = """أنت خبير ومستشار استراتيجي لبناء قواعد المعرفة المتقدمة (Enterprise Knowledge Base Architect) لأنظمة الذكاء الاصطناعي وخدمة العملاء في تطبيق persona-B2B.
مهمتك هي مساعدتي خطوة بخطوة عبر محادثة تفاعلية في بناء ملف معرفة شامل ومحكم لمتجري أو نشاطي التجاري، بحيث يتم استخدامه في محرك الـ RAG للرد الآلي الفوري على استفسارات العملاء على واتساب.

يرجى طرح الأسئلة عليّ خطوة بخطوة باللغة العربية (سؤالاً أو قسمين في كل مرة) لتغطية التفاصيل التالية بدقة:
1. الهوية ونبرة الرد: اسم المتجر/النشاط، مجال العمل، نبرة المساعد الذكي (ودودة، احترافية، رسمية، مصرية محببة).
2. المنتجات والخدمات: أسماء المنتجات الرئيسية، المقاسات، الألوان، الخامات، والأسعار الرسمية المحددة بالجنيه المصري (أو العملة المحلية).
3. الشحن والتوصيل: تكلفة الشحن ومدة التوصيل حسب المناطق والمحافظات (القاهرة والجيزة، الإسكندرية، محافظات الدلتا، محافظات الصعيد، الشحن المجاني فوق حد معين إن وجد).
4. سياسة الاستبدال والاسترجاع: الشروط، المهلة الزمنية (مثلاً 14 يوماً)، مصاريف الشحن في حال الاسترجاع أو الاستبدال، والحالات المستثناة.
5. طرق الدفع المتاحة: الدفع عند الاستلام (COD)، إنستاباي (InstaPay)، فودافون كاش ومحافظ المحمول، أو التحويل البنكي مع تفاصيل التحويل.
6. بيانات التواصل والأسئلة الشائعة: أرقام خدمة العملاء، رابط الموقع، مواعيد العمل الرسمية، وأكثر الأسئلة المتكررة من العملاء مع إجاباتها النموذجية.

🚨 الشرط النهائي الإلزامي للمخرجات:
بعد إجابتي على الأسئلة واستيفاء كافة البيانات، قم بتجميع كل المعلومات في ملف ماركداون (Markdown) واحد متماسك واحترافي، موضوع بالكامل داخل كود بلوك واحد قابل للنسخ (Single Markdown Code Block ```markdown)، مع الالتزام التام بالقواعد التالية:
- استخدام الترويسات الرئيسية والفرعية (# و ## و ###) بوضوح تام لتقسيم الأقسام.
- كتابة المواصفات والأسعار بصيغة قوائم نقطية واضحة ومحددة.
- تجنب الحشو الإنشائي لضمان أعلى دقة عند تقطيع النصوص (RAG Chunking) وفهرستها في قاعدة البيانات."""

const val PERSONAL_KNOWLEDGE_PROMPT = """أنت مستشار خبير في إعداد وتخصيص المساعدين الشخصيين الأذكياء (Personal AI Persona Architect) لتطبيق persona-B2B.
مهمتك هي إجراء مقابلة سريعة ومنهجية معي لبناء ملف معرفة شخصي محكم، يُمكن المساعد الذكي من الرد نيابة عني على محادثات ورسائل واتساب بدقة بالغة وبنفس أسلوبي.

يرجى توجيه الأسئلة التالية لي خطوة بخطوة:
1. الهوية الشخصية والأسلوب: اسمي، مهنتي/تخصصي، ونبرة الحديث المفضلة (عفوية، رسمية، مختصرة، ذكية).
2. جدول المواعيد وساعات الرد: أوقات تواجدي وانشغالي المعتادة، ساعات النوم، ومتى يُفضل تأجيل الرد أو تحويل الرسالة كأولوية.
3. دوائر المعارف والعلاقات: كيفية التعامل والرد حسب المرسل:
   - الأصدقاء المقربون (نبرة ودية وعفوية).
   - العائلة والأقارب (نبرة احترام واهتمام).
   - زملاء العمل والعملاء (نبرة مهنية واحترافية).
   - الغرباء وأرقام مجهولة (نبرة حذرة ومهذبة).
4. الحدود والخطوط الحمراء (Strict Boundaries): الموضوعات أو القرارات التي يُحظر تماماً على البوت البت فيها أو الالتزام بها نيابة عني (مثل الاتفاقيات المالية، الالتزام بمواعيد نهائية دون مراجعتي، أو مشاركة معلومات خاصة).

🚨 الشرط النهائي الإلزامي للمخرجات:
بعد أن أجيبك، قم بتوليد مسودة المعرفة كاملة بصيغة ملف ماركداون (Markdown) داخل كود بلوك واحد جاهز للنسخ (Single Code Block ```markdown) مبني بترويسات واضحة (# و ##) وقوائم نقطية محددة بدون أي كلام تمهيدي خارج الكود، ليكون جاهزاً للنسخ واللصق فوراً داخل تطبيق persona-B2B."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImportScreen(
    viewModel: ChatImportViewModel,
    onImportStarted: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * Accepted MIME types for knowledge import: plain text plus Markdown.
     * "application/octet-stream" is included because some file providers
     * expose .md/.txt files with a generic MIME type.
     */
    val acceptedMimeTypes = arrayOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "application/octet-stream"
    )

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked ->
            val displayName = picked.queryDisplayName(context)
            val isSupported = displayName?.endsWith(".txt", ignoreCase = true) == true ||
                displayName?.endsWith(".md", ignoreCase = true) == true
            if (isSupported || displayName == null) {
                viewModel.onFilePicked(picked)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بناء واستيراد قاعدة المعرفة", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Gradient Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بناء واستيراد قاعدة المعرفة (Studio)",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "أنشئ قاعدة المعرفة الذكية لمتجرك أو مساعدك الشخصي عبر برومبتات الذكاء الاصطناعي الجاهزة، أو الصق النص مباشرة للفهرسة الفورية، أو اختر ملفاً من جهازك.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Card 1: AI Knowledge Architect Prompts
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "🤖 أنشئ ملف المعرفة بالذكاء الاصطناعي (AI Prompt Generator)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "انسخ البرومبت المتخصص والصقه في ChatGPT أو Claude. سيقوم الذكاء الاصطناعي بمقابلتك خطوة بخطوة وتوليد ملف المعرفة المنسق بالماركداون (# Heading) لتقوم بلصقه هنا مباشرة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Button 1: Business / Commercial Prompt
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(BUSINESS_KNOWLEDGE_PROMPT))
                            Toast.makeText(context, "تم نسخ برومبت الأنشطة التجارية!", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تم نسخ برومبت الأنشطة التجارية! الصقه في ChatGPT أو Claude")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "📋 نسخ برومبت الأنشطة التجارية والمتاجر",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Button 2: Personal Assistant / Non-Commercial Prompt
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(PERSONAL_KNOWLEDGE_PROMPT))
                            Toast.makeText(context, "تم نسخ برومبت المساعد الشخصي!", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تم نسخ برومبت المساعد الشخصي! الصقه في ChatGPT أو Claude")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "👤 نسخ برومبت المساعد الشخصي والأفراد",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Card 2: Direct Text Paste Ingestion Box
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✍️ لصق المعرفة وحفظها مباشرة (Direct Text)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "الصق نص المعرفة المنسق بالماركداون أو النص العادي ليتم تقطيعه وتوليد التضمينات الشعاعية (Embeddings) محلياً على جهازك دون الحاجة لحفظ أو رفع ملفات:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.directInputText,
                        onValueChange = { viewModel.onDirectInputTextChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 260.dp),
                        placeholder = {
                            Text(
                                text = "الصق نص المعرفة هنا مباشرة بعد نسخه من ChatGPT أو قوالب الماركداون...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        minLines = 6,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Success Feedback Banner
                    AnimatedVisibility(visible = uiState.directIndexSuccessMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = uiState.directIndexSuccessMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Direct Indexing Error Banner
                    AnimatedVisibility(visible = uiState.directIndexError != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = uiState.directIndexError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Primary Direct Ingestion Action Button
                    Button(
                        onClick = { viewModel.ingestDirectText() },
                        enabled = uiState.directInputText.isNotBlank() && !uiState.isDirectIndexing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                    ) {
                        if (uiState.isDirectIndexing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("جاري الفهرسة وتوليد التضمينات...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("💾 حفظ وفهرسة المعرفة الآن", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Card 3: Alternative File Upload
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📁 أو ارفع ملف من جهازك",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    OutlinedButton(
                        onClick = { fileLauncher.launch(acceptedMimeTypes) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "اختيار ملف",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("أو اختر ملف (.txt / .md) من جهازك")
                    }

                    uiState.selectedFileUri?.let { uri ->
                        Text(
                            text = "الملف المحدد: ${uri.queryDisplayName(context) ?: uri.lastPathSegment ?: "knowledge.md"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Button(
                            onClick = { viewModel.startImport() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "بدء",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("بدء استيراد وفهرسة الملف", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // File Import Error Message
            AnimatedVisibility(visible = uiState.error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "خطأ",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (uiState.isOwnerNameMissing) {
                            TextButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("الانتقال للإعدادات", fontWeight = FontWeight.Bold)
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
 * (SAF content URIs do not carry the file name in [Uri.lastPathSegment]).
 * Returns null when the name cannot be resolved.
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
