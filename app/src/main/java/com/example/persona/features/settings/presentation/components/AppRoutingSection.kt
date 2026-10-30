package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.presentation.SettingsUiState

@Composable
fun AppRoutingSection(
    state: SettingsUiState,
    onListenMainAppChange: (Boolean) -> Unit,
    onListenBusinessAppChange: (Boolean) -> Unit,
    onListenCloneAppChange: (Boolean) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onAutoPauseOnHitlChange: (Boolean) -> Unit,
    onHitlHoldingMessageChange: (String) -> Unit,
    onMediaFallbackMessageChange: (String) -> Unit,
    onBlankReplyFallbackChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── WhatsApp App Routing ──────────────────────────────────────────────
        PreferenceSection(
            title = "توجيه تطبيقات واتساب (WhatsApp App Routing)",
            subtitle = "حدد أي من تطبيقات واتساب المثبتة على هاتفك يجب على Persona مراقبتها والرد عليها."
        ) {
            PreferenceSwitchRow(
                title = "تطبيق واتساب الأساسي (WhatsApp)",
                subtitle = "com.whatsapp (النسخة العادية)",
                checked = state.listenMainApp,
                onCheckedChange = onListenMainAppChange
            )
            PreferenceSwitchRow(
                title = "واتساب للأعمال (WhatsApp Business)",
                subtitle = "com.whatsapp.w4b (نسخة الأعمال التجارية)",
                checked = state.listenBusinessApp,
                onCheckedChange = onListenBusinessAppChange
            )
            PreferenceSwitchRow(
                title = "التطبيق المستنسخ / الشريحة الثانية (Dual App / Cloned)",
                subtitle = "مراقبة النسخة المستنسخة في واجهات شاومي وسامسونج وغيرها",
                checked = state.listenCloneApp,
                onCheckedChange = onListenCloneAppChange
            )
        }

        // ── WhatsApp Media & Voice Notes Access ────────────────────────────────
        PreferenceSection(
            title = "الوصول لوسائط ورسائل واتساب الصوتية (Storage Access)",
            subtitle = "يتيح قراءة الصور والرسائل الصوتية الواردة ومعالجتها بنماذج الذكاء الاصطناعي محلياً."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "صلاحية الوصول لكافة الملفات (Manage Storage)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (state.isStorageAccessGranted)
                            "مفعل ✓ - يمكن قراءة صور ورسائل الواتساب الصوتية وتحويل الصوت لنص"
                        else
                            "غير مفعل - يلزم منح الإذن لقراءة وسائط الواتساب والرسائل الصوتية",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestStoragePermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (state.isStorageAccessGranted) "مفعل ✓" else "منح الإذن",
                        color = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // ── Human Takeover & Safety (HITL) ────────────────────────────────────
        PreferenceSection(
            title = "التدخل البشري وأمان المحادثة (Human Takeover & Safety)",
            subtitle = "التحكم في آلية إيقاف الروبوت فور طلب العميل التحدث مع شخص بشري."
        ) {
            PreferenceSwitchRow(
                title = "إيقاف الرد الآلي تلقائياً عند طلب ممثل بشري",
                subtitle = "تجميد ردود الروبوت للعميل مؤقتاً لتجنب مقاطعة التاجر أو خدمة العملاء.",
                checked = state.isAutoPauseOnHitl,
                onCheckedChange = onAutoPauseOnHitlChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.hitlHoldingMessage,
                onValueChange = onHitlHoldingMessageChange,
                label = { Text("رسالة الانتظار عند التحويل البشري (HITL Holding Message)") },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // ── System Fallback Messages ──────────────────────────────────────────
        PreferenceSection(
            title = "رسائل الطوارئ والاحتياط (System Fallback Messages)",
            subtitle = "الرسائل التي يرسلها النظام عند تعذر معالجة الوسائط أو عدم توفر رد ذكاء اصطناعي."
        ) {
            OutlinedTextField(
                value = state.mediaFallbackMessage,
                onValueChange = onMediaFallbackMessageChange,
                label = { Text("رسالة تعذر معالجة الوسائط (Media Fallback)") },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.blankReplyFallbackTemplate,
                onValueChange = onBlankReplyFallbackChange,
                label = { Text("قالب الرد عند عدم التأكد (%s = اسم المتجر)") },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}
