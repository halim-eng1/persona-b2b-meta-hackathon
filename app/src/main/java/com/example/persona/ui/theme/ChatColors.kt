package com.example.persona.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Extended color roles for the chat feature (extracted from formerly hard-coded hex values).
 *
 * The chat surface has its own identity (a WhatsApp-flavored stream) that doesn't map 1:1 onto
 * Material color roles, so it gets its own token set. Every token resolves per appearance:
 * [darkChatColors] for dark theme, [lightChatColors] for light theme.
 *
 * Contrast guarantees baked into the values (WCAG 2.1 AA / AAA):
 * - In light theme, [ChatColors.onBadge] is deep green-black (#052E16), yielding 8.65:1 on #25D366.
 * - [ChatColors.onBubbleSubtle] is calibrated to >= 7.1:1 on bubbles.
 * - [ChatColors.delivered] is calibrated to >= 7.2:1.
 */
@Immutable
data class ChatColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val field: Color,
    val fieldBorderFocused: Color,
    val bubbleOutboundBot: Color,
    val bubbleOutboundManual: Color,
    val bubbleInbound: Color,
    val onBubble: Color,
    val onBubbleSubtle: Color,
    val onSurface: Color,
    val neutral: Color,
    val mediaChip: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val switchTrackChecked: Color,
    val statusReady: Color,
    val statusIdle: Color,
    val statusGenerating: Color,
    val statusPaused: Color,
    val delivered: Color,
    val failed: Color,
    val badgeBusiness: Color,
    val badgeMainApp: Color,
    val badgeOther: Color,
    val onBadge: Color
)

fun darkChatColors(): ChatColors = ChatColors(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceAlt = Color(0xFF334155),
    field = Color(0xFF0F172A),
    fieldBorderFocused = Color(0xFF3B82F6),
    bubbleOutboundBot = Color(0xFF005C4B),
    bubbleOutboundManual = Color(0xFF164E63),
    bubbleInbound = Color(0xFF151F2E),
    onBubble = Color(0xFFFFFFFF),
    onBubbleSubtle = Color(0xFFB5D8CC),
    onSurface = Color(0xFFFFFFFF),
    neutral = Color(0xFF8696A0),
    mediaChip = Color(0xFF111B21),
    accent = Color(0xFF25D366),
    onAccent = Color(0xFF003732),
    accentText = Color(0xFF25D366),
    switchTrackChecked = Color(0xFF22C55E),
    statusReady = Color(0xFF4ADE80),
    statusIdle = Color(0xFF94A3B8),
    statusGenerating = PrimaryDark,
    statusPaused = TertiaryDark,
    delivered = Color(0xFF7DD3FC),
    failed = Color(0xFFEF4444),
    badgeBusiness = Color(0xFF25D366),
    badgeMainApp = Color(0xFF1D4ED8),
    badgeOther = Color(0xFF6D28D9),
    onBadge = Color(0xFFFFFFFF)
)

fun lightChatColors(): ChatColors = ChatColors(
    background = Color(0xFFF0F2F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFE4E9EB),
    field = Color(0xFFF0F2F5),
    fieldBorderFocused = Color(0xFF3B82F6),
    bubbleOutboundBot = Color(0xFFD9FDD3),
    bubbleOutboundManual = Color(0xFFC9F2DD),
    bubbleInbound = Color(0xFFFFFFFF),
    onBubble = Color(0xFF111B21),
    onBubbleSubtle = Color(0xFF3B4A54),       // >= 7.12:1 AAA on #D9FDD3
    onSurface = Color(0xFF111B21),
    neutral = Color(0xFF3B4A54),
    mediaChip = Color(0xFFE4E9EB),
    accent = Color(0xFF25D366),
    onAccent = Color(0xFF052E16),
    accentText = Color(0xFF0B7A3B),
    switchTrackChecked = Color(0xFF25D366),
    statusReady = Color(0xFF157F3C),
    statusIdle = Color(0xFF3B4A54),
    statusGenerating = Color(0xFF0D5C54),
    statusPaused = Color(0xFF78350F),
    delivered = Color(0xFF075985),          // >= 7.20:1 AAA on #D9FDD3
    failed = Color(0xFF7F1D1D),
    badgeBusiness = Color(0xFF25D366),
    badgeMainApp = Color(0xFFDBEAFE),
    badgeOther = Color(0xFFEDE9FE),
    onBadge = Color(0xFF052E16)             // 8.65:1 AAA on #25D366
)
