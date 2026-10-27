package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.features.chats.presentation.detail.ExportTimeRange
import com.example.persona.ui.theme.chatColors

/**
 * Dialog prompting the merchant to select the time window for exporting local chat history.
 *
 * Options:
 *  - [ExportTimeRange.ALL_TIME]: All messages stored locally for this contact.
 *  - [ExportTimeRange.TODAY]: Messages since midnight of today.
 *  - [ExportTimeRange.LAST_TWO_HOURS]: Messages within the past 2 hours.
 */
@Composable
fun ExportChatDialog(
    onDismissRequest: () -> Unit,
    onConfirmExport: (ExportTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(ExportTimeRange.ALL_TIME) }
    val chatColors = MaterialTheme.chatColors

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = chatColors.accent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "تصدير المحادثة (Export Chat)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = chatColors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "اختر النطاق الزمني لسجل المحادثة المراد تصديره:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = chatColors.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                ExportTimeRange.values().forEach { range ->
                    val isSelected = range == selectedRange
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) chatColors.surfaceAlt else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) chatColors.accent else chatColors.neutral.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedRange = range }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null, // handled by parent selectable row
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = chatColors.accent,
                                    unselectedColor = chatColors.neutral
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${range.titleEnglish} (${range.titleArabic})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = chatColors.onSurface
                                )
                                Text(
                                    text = range.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = chatColors.neutral
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmExport(selectedRange) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = chatColors.accent,
                    contentColor = chatColors.onSurface
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تصدير ومشاركة",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    text = "إلغاء",
                    style = MaterialTheme.typography.labelLarge,
                    color = chatColors.neutral
                )
            }
        },
        containerColor = chatColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
