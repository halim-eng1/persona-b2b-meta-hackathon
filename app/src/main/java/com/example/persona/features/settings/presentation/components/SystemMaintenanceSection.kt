package com.example.persona.features.settings.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.features.settings.presentation.SettingsUiState

@Composable
fun SystemMaintenanceSection(
    state: SettingsUiState,
    onRefreshHealth: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Feedback message banner
        AnimatedVisibility(visible = state.maintenanceMessage != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = state.maintenanceMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Permissions Health Check ──────────────────────────────────────────
        PreferenceSection(
            title = "فحص سلامة النظام والصلاحيات",
            subtitle = "مراجعة فورية لحالة الأذونات الحيوية لضمان عدم توقف الرد الآلي في الخلفية."
        ) {
            HealthItemRow(
                title = "صلاحية قراءة إشعارات واتساب",
                isOk = state.isNotificationListenerGranted,
                okText = "مفعلة وتعمل بنجاح",
                missingText = "غير مفعلة (مطلوبة للرد الآلي)"
            )
            HealthItemRow(
                title = "استثناء توفير الطاقة والبطارية",
                isOk = state.isBatteryOptimizationIgnored,
                okText = "مستثناة (حماية من القتل في الخلفية)",
                missingText = "غير مستثناة (قد يتوقف التطبيق بعد قفل الشاشة)"
            )
            HealthItemRow(
                title = "التشغيل التلقائي مع فتح الهاتف",
                isOk = state.isAutoStartConfirmed,
                okText = "مؤكد ومفعل",
                missingText = "في انتظار التأكيد"
            )
            HealthItemRow(
                title = "الوصول لوسائط ورسائل واتساب الصوتية",
                isOk = state.isStorageAccessGranted,
                okText = "مفعلة (جاهز للصور والصوتيات)",
                missingText = "غير مفعلة (اختياري للوسائط)"
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRefreshHealth,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة فحص الصلاحيات الآن")
            }
        }

        // ── Encrypted Local Backup & Restore ──────────────────────────────────
        PreferenceSection(
            title = "النسخ الاحتياطي المحلي واستعادة البيانات",
            subtitle = "تصدير تفضيلات المتجر وقاعدة المعرفة مشفرة محلياً أو استعادتها عند تغيير الهاتف."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExportBackup,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير نسخة مشفرة", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRestoreBackup,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة نسخة سابقة", fontSize = 12.sp)
                }
            }
        }

        // ── Cache Cleaner ─────────────────────────────────────────────────────
        PreferenceSection(
            title = "صيانة الذاكرة المؤقتة",
            subtitle = "مسح الملفات المؤقتة وسجلات الفحص السريعة لتحرير مساحة التخزين."
        ) {
            OutlinedButton(
                onClick = onClearCache,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("مسح الذاكرة المؤقتة")
            }
        }
    }
}

@Composable
private fun HealthItemRow(
    title: String,
    isOk: Boolean,
    okText: String,
    missingText: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOk) okText else missingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
