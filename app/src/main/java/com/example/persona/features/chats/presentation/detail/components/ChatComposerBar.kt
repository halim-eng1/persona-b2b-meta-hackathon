package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.ui.theme.chatColors

val DEFAULT_QUICK_REPLIES = listOf(
    "تمام يا فندم",
    "تم تأكيد طلبك",
    "الطلب مع المندوب",
    "العنوان مظبوط؟"
)

/**
 * Chat Composer Bar with quick reply chips.
 * Note: Does NOT include imePadding internally, in accordance with root layout padding rules.
 */
@Composable
fun ChatComposerBar(
    inputText: String,
    isSending: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    quickReplies: List<String> = DEFAULT_QUICK_REPLIES,
    onQuickReplyClicked: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val chatColors = MaterialTheme.chatColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = chatColors.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Quick reply chips bar
            if (quickReplies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickReplies.forEach { reply ->
                        SuggestionChip(
                            onClick = {
                                onQuickReplyClicked?.invoke(reply) ?: onInputTextChanged(reply)
                            },
                            label = {
                                Text(
                                    text = reply,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = chatColors.field,
                                labelColor = chatColors.onSurface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = chatColors.surfaceAlt
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("اكتب رداً يدوياً للعميل...", color = chatColors.statusIdle) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = chatColors.onSurface,
                        unfocusedTextColor = chatColors.onSurface,
                        focusedContainerColor = chatColors.field,
                        unfocusedContainerColor = chatColors.field,
                        focusedBorderColor = chatColors.fieldBorderFocused,
                        unfocusedBorderColor = chatColors.surfaceAlt
                    )
                )

                IconButton(
                    onClick = onSendMessage,
                    enabled = inputText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (inputText.isNotBlank()) chatColors.accent else chatColors.surfaceAlt,
                            shape = CircleShape
                        )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = chatColors.onSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) chatColors.onSurface else chatColors.statusIdle
                        )
                    }
                }
            }
        }
    }
}
