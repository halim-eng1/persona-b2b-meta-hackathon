package com.example.persona.features.settings.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.features.settings.domain.repository.ModelProviders

data class ProviderGuideDetails(
    val provider: String,
    val title: String,
    val platformName: String,
    val portalUrl: String,
    val keyOrIdLabel: String,
    val steps: List<String>,
    val tips: String
) {
    fun getAiPrompt(): String {
        return "أشرح لي خطوة بخطوة للمبتدئين بالصور والروابط كيف أنشئ حساباً على منصة $platformName ($portalUrl) وكيف أستخرج مفتاح $keyOrIdLabel مجاناً لاستخدامه في تطبيقي."
    }
}

object ProviderGuideRegistry {
    fun getGuide(provider: String): ProviderGuideDetails {
        return when (ModelProviders.normalize(provider)) {
            ModelProviders.QWEN -> ProviderGuideDetails(
                provider = ModelProviders.QWEN,
                title = "Alibaba Cloud DashScope (Qwen)",
                platformName = "DashScope Aliyun",
                portalUrl = "https://dashscope.console.aliyun.com",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "ادخل إلى منصة DashScope عبر الرابط الرسمي وسجل الدخول باستخدام حساب Alibaba Cloud.",
                    "من القائمة الجانبية في Model Studio، توجه إلى قسم مفاتيح الواجهة (API-KEY).",
                    "اضغط على Create API-KEY وانسخ الرمز السري المتولد فوراً (يبدأ بـ sk-...).",
                    "يدعم جميع نماذج Qwen 3.8 Max و Qwen 3.8 Flash و DeepSeek v4.1 السريعة."
                ),
                tips = "تأكد من حفظ المفتاح؛ يتيح لك التطبيق تدوير عدة مفاتيح مجانية لضمان استمرار الخدمة دون انقطاع."
            )
            ModelProviders.GEMINI -> ProviderGuideDetails(
                provider = ModelProviders.GEMINI,
                title = "Google AI Studio (Gemini)",
                platformName = "Google AI Studio",
                portalUrl = "https://aistudio.google.com/app/apikey",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "افتح رابط منصة Google AI Studio وسجل الدخول باستخدام حساب Google الخاص بك.",
                    "اضغط على زر (Get API key) في الزاوية العلوية ثم اختر (Create API key).",
                    "اختر مشروع Google Cloud جديد أو قائم، ثم انسخ المفتاح الذي يبدأ بـ AIzaSy...",
                    "يمنحك وصولاً مجانياً سخياً لنماذج Gemini 3.1 Flash-Lite و 3.5 Flash-Lite و 3.8 Flash."
                ),
                tips = "مفاتيح Gemini مجانية وسريعة جداً وتدعم معالجة الصور والنصوص بجودة فائقة."
            )
            ModelProviders.GROQ -> ProviderGuideDetails(
                provider = ModelProviders.GROQ,
                title = "GroqCloud Console",
                platformName = "GroqCloud",
                portalUrl = "https://console.groq.com/keys",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "سجل الدخول إلى GroqCloud Console عبر حساب Google أو GitHub مجاناً.",
                    "من القائمة الجانبية اضغط على تبويب (API Keys) ثم زر (Create API Key).",
                    "اكتب اسماً للمفتاح ثم انسخ الرمز المولد (يبدأ بـ gsk_...).",
                    "يوفر سرعة استجابة فائقة البرق مع معالجات LPU لنماذج GPT-OSS و Qwen 3.8."
                ),
                tips = "أسرع استجابة زمنية (TTFT) على الإطلاق، مثالية للرد الفوري على رسائل العملاء."
            )
            ModelProviders.NVIDIA -> ProviderGuideDetails(
                provider = ModelProviders.NVIDIA,
                title = "NVIDIA NIM / Build",
                platformName = "NVIDIA Build",
                portalUrl = "https://build.nvidia.com",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "ادخل إلى NVIDIA Build وسجل الدخول بحساب مطور NVIDIA Developer مجاناً.",
                    "اختر نموذج Nemotron (مثل Nemotron 3 Ultra 550B أو Lightning).",
                    "اضغط على زر (Get API Key) وأنشئ مفتاحاً سرياً جديداً (يبدأ بـ nvapi-...).",
                    "تحصل على رصيد ترحيبي قدره 1000 نقطة لتشغيل أضخم النماذج المعرفية المؤسسية."
                ),
                tips = "نماذج Nemotron فائقة الذكاء وممتازة للتفاوض المعقد واستخراج الطلبات."
            )
            ModelProviders.OPENROUTER -> ProviderGuideDetails(
                provider = ModelProviders.OPENROUTER,
                title = "OpenRouter Unified Gateway",
                platformName = "OpenRouter",
                portalUrl = "https://openrouter.ai/keys",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "سجل الدخول إلى بوابة OpenRouter عبر حساب Google أو GitHub.",
                    "انتقل إلى صفحة المفاتيح (Keys) ثم اضغط على (Create Key).",
                    "انسخ المفتاح السري الذي يبدأ بـ (sk-or-v1-...).",
                    "يدعم تلقائياً مئات النماذج المجانية الدائمة مثل Nemotron Free و Liquid Free."
                ),
                tips = "البديل الاحتياطي الأمثل (Failover Gateway) الذي يضمن عدم توقف الردود أبداً."
            )
            ModelProviders.CLOUDFLARE -> ProviderGuideDetails(
                provider = ModelProviders.CLOUDFLARE,
                title = "Cloudflare Workers AI",
                platformName = "Cloudflare Dashboard",
                portalUrl = "https://dash.cloudflare.com",
                keyOrIdLabel = "Account ID و API Token",
                steps = listOf(
                    "ادخل إلى لوحة تحكم Cloudflare وتوجه إلى قسم (Workers & Pages) ثم (AI).",
                    "انسخ معرّف الحساب (Account ID) الظاهر في الشريط الجانبي الأيمن وألصقه في حقل Account ID.",
                    "اضغط على (Manage API Tokens) وأنشئ توكن جديد بصلاحية (Workers AI Read).",
                    "انسخ التوكن السري (يبدأ بـ cfut_...) وأضفه في قائمة مفاتيح الـ API."
                ),
                tips = "يتطلب Cloudflare Workers AI كلاً من Account ID و API Token للعمل مع نماذج Clef Flash و Clef Omni."
            )
            ModelProviders.LIQUID -> ProviderGuideDetails(
                provider = ModelProviders.LIQUID,
                title = "Liquid AI LFM System",
                platformName = "Liquid AI",
                portalUrl = "https://www.liquid.ai",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "ادخل إلى بوابة مطوري Liquid AI وسجل الدخول بحسابك.",
                    "انتقل إلى قسم API Credentials وأنشئ مفتاحاً جديداً (يبدأ بـ liquid_...).",
                    "انسخ المفتاح السري وألصقه في التطبيق.",
                    "يدعم محرك اتخاذ القرار الفوري ونماذج d1 و d1:latest ذات زمن الاستجابة فائق الصغر."
                ),
                tips = "يعمل كحارس بوابات (Gatekeeper) لتصنيف نية العميل وكشف الشكاوى العاجلة خلال أقل من 800ms."
            )
            ModelProviders.NARA -> ProviderGuideDetails(
                provider = ModelProviders.NARA,
                title = "Nara Router AI",
                platformName = "Nara Router",
                portalUrl = "https://router.bynara.id",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "ادخل إلى بوابة Nara Router وأنشئ حساب مستخدم جديد.",
                    "توجه إلى إدارة مفاتيح API واستخرج مفتاحاً جديداً (يبدأ بـ sk-nry-...).",
                    "انسخ المفتاح وضعه في قائمة مفاتيح التطبيق.",
                    "يدعم نموذج laguna-s-2.1 المخصص للغة العربية والمعاملات."
                ),
                tips = "نموذج عربي مخصص ومدرب بكفاءة عالية على اللهجات العربية وتجربة التجارة الإلكترونية."
            )
            else -> ProviderGuideDetails(
                provider = provider,
                title = provider.uppercase(),
                platformName = provider,
                portalUrl = "https://google.com",
                keyOrIdLabel = "API Key",
                steps = listOf(
                    "سجل الدخول إلى المنصة الرسمية للمزود.",
                    "انتقل إلى إعدادات API وأنشئ مفتاحاً جديداً.",
                    "انسخ المفتاح وضعه داخل التطبيق."
                ),
                tips = "يمكنك إضافة أكثر من مفتاح لنفس المزود لتوزيع الحمل وتجنب حدود الاستخدام."
            )
        }
    }
}

@Composable
fun ProviderKeyGuideDialog(
    provider: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val guide = ProviderGuideRegistry.getGuide(provider)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "دليل استخراج المفتاح",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = guide.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Official Portal Link Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "رابط المنصة الرسمي:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = guide.portalUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching { uriHandler.openUri(guide.portalUrl) }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open Portal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Step by step instructions
                Text(
                    text = "خطوات الحصول على المفتاح:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    guide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Tips Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = guide.tips,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Prominent AI Guide Prompt Copy Button
                Button(
                    onClick = {
                        val aiPrompt = guide.getAiPrompt()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI Guide Prompt", aiPrompt)
                        clipboard.setPrimaryClip(clip)

                        val notificationText = "انسخ هذا النص وأرسله لأي نموذج ذكاء اصطناعي (مثل ChatGPT أو Gemini): $aiPrompt"
                        Toast.makeText(context, notificationText, Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🤖 اجعل الذكاء الاصطناعي يرشدك (Copy AI Guide Prompt)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إغلاق")
            }
        }
    )
}
