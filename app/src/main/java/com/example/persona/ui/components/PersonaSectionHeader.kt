package com.example.persona.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Detects whether a string contains Arabic characters.
 */
fun containsArabic(text: String): Boolean {
    return text.any {
        it in '\u0600'..'\u06FF' || // Arabic
        it in '\u0750'..'\u077F' || // Arabic Supplement
        it in '\u08A0'..'\u08FF' || // Arabic Extended-A
        it in '\uFB50'..'\uFDFF' || // Arabic Presentation Forms-A
        it in '\uFE70'..'\uFEFF'    // Arabic Presentation Forms-B
    }
}

/**
 * Standardized section header with optional subtitle and trailing action.
 * Automatically zeroes out letterSpacing on Arabic text to avoid breaking cursive ligatures,
 * while preserving uppercase tracking for Latin scripts.
 */
@Composable
fun PersonaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val isArabic = containsArabic(title)
    val displayTitle = if (isArabic) title else title.uppercase()
    val tracking = if (isArabic) 0.sp else 1.2.sp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = tracking,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                action()
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
