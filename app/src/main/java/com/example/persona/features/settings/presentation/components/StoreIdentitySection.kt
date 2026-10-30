package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.ThresholdLevel

@Composable
fun StoreIdentitySection(
    state: SettingsUiState,
    onBotNameChange: (String) -> Unit,
    onStoreNameChange: (String) -> Unit,
    onPersonaToneChange: (PersonaTone) -> Unit,
    onThresholdChange: (ThresholdLevel) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onReplyDelayChange: (String) -> Unit,
    onGreetingTemplateChange: (String) -> Unit,
    onFarewellTemplateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Bot & Store Brand Identity ────────────────────────────────────────
        PreferenceSection(
            title = "هوية المتجر وممثل خدمة العملاء (Store & Bot Identity)",
            subtitle = "حدد اسم البوت واسم المتجر أو العلامة التجارية لاستخدامهما في ردود الذكاء الاصطناعي."
        ) {
            OutlinedTextField(
                value = state.botName,
                onValueChange = onBotNameChange,
                label = { Text("اسم ممثل خدمة العملاء (Bot Name)") },
                placeholder = { Text("مثال: سارة / مساعد المبيعات") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.storeName,
                onValueChange = onStoreNameChange,
                label = { Text("اسم المتجر أو العلامة التجارية (Store Name)") },
                placeholder = { Text("مثال: متجر الأناقة / متجرنا") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── AI Persona Tone ───────────────────────────────────────────────────
        PreferenceSection(
            title = "شخصية ونبرة الرد للروبوت (AI Persona Tone)",
            subtitle = "اختر الأسلوب واللهجة التي يتحدث بها البوت مع زبائنك في واتساب."
        ) {
            PersonaTone.entries.forEach { tone ->
                PersonaToneCard(
                    tone = tone,
                    isSelected = state.personaTone == tone,
                    onSelect = { onPersonaToneChange(tone) }
                )
            }
        }

        // ── Response Templates ────────────────────────────────────────────────
        PreferenceSection(
            title = "قوالب الردود السريعة (Response Templates)",
            subtitle = "رسائل مخصصة للتحية والترحيب الأولي أو ختام المحادثة."
        ) {
            OutlinedTextField(
                value = state.greetingTemplate,
                onValueChange = onGreetingTemplateChange,
                label = { Text("قالب رسالة الترحيب (Greeting Template)") },
                placeholder = { Text("أهلاً بحضرتك يا فندم! نورت متجرنا، إزاي نقدر نساعدك النهارده؟") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = state.farewellTemplate,
                onValueChange = onFarewellTemplateChange,
                label = { Text("قالب رسالة الختام (Farewell Template)") },
                placeholder = { Text("سعدنا جداً بخدمتك يا فندم، وتحت أمرك في أي وقت! مع السلامة وبنتمنالك يوم سعيد 💙") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // ── Reply Delay & Cooldown ────────────────────────────────────────────
        PreferenceSection(
            title = "توقيت الردود وسرعة التفاعل (Timing & Delays)",
            subtitle = "ضبط فترة الانتظار بين الردود لمحاكاة الكتابة البشرية الطبيعية."
        ) {
            OutlinedTextField(
                value = state.replyDelayInput,
                onValueChange = onReplyDelayChange,
                label = { Text("تأخير الرد بالثواني (Reply Delay in Seconds)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.replyDelayError != null,
                supportingText = {
                    if (state.replyDelayError != null) {
                        Text(
                            text = state.replyDelayError,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("يمنح العميل إحساساً بأن شخصاً حقيقياً يقرأ ويكتب الرد.")
                    }
                }
            )
        }

        // ── Match Sensitivity ─────────────────────────────────────────────────
        PreferenceSection(
            title = "حساسية المطابقة للأسئلة (Match Sensitivity)",
            subtitle = "التحكم في مدى دقة التطابق المطلوبة مع قاعدة المعرفة للرد التلقائي."
        ) {
            ThresholdLevel.entries.forEach { level ->
                ThresholdOption(
                    level = level,
                    isSelected = state.thresholdLevel == level,
                    onSelect = { onThresholdChange(level) }
                )
            }
        }

        // ── Cooldown Duration ─────────────────────────────────────────────────
        PreferenceSection(
            title = "فترة التهدئة بين الرسائل (Cooldown Duration)",
            subtitle = "أقل وقت انتظار مسموح به قبل إعادة الرد تلقائياً على نفس المحادثة."
        ) {
            listOf(0, 1, 5, 10, 30).forEach { mins ->
                CooldownOption(
                    mins = mins,
                    isSelected = state.cooldownMins == mins,
                    onSelect = { onCooldownChange(mins) }
                )
            }
        }
    }
}
