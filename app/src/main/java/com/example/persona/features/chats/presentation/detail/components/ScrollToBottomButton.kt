package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.ui.theme.chatColors

/**
 * WhatsApp-style floating "scroll to bottom" button.
 *
 * Appears when the user scrolls away from the bottom of the message stream.
 * Displays an unread badge indicating the count of new messages that arrived
 * while the user was reading earlier history.
 */
@Composable
fun ScrollToBottomButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = MaterialTheme.chatColors

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = if (unreadCount > 0) {
                    "Scroll to bottom, $unreadCount new messages"
                } else {
                    "Scroll to bottom"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Circular Button Base matching WhatsApp floating action button
        Surface(
            modifier = Modifier
                .size(42.dp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                ),
            color = chatColors.surfaceAlt,
            contentColor = chatColors.onSurface
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = chatColors.neutral,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Unread Badge (green circular badge centered on the top edge)
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-9).dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .background(color = chatColors.accent, shape = CircleShape)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color(0xFF111B21), // High-contrast dark text on bright WhatsApp green
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
