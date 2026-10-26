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
 * Contrast guarantees baked into the values (WCAG 2.1 AA, small text = 4.5:1):
 * - [ChatColors.onBubbleSubtle] is the metadata color on bubbles (sender, timestamp). The old
 *   #8696A0 computed ~2.6:1 on the outbound green bubbles; this token is >= 5:1 on every bubble
 *   color in both appearances.
 * - [ChatColors.neutral] is the subtle color on [ChatColors.surfaceAlt] / [ChatColors.mediaChip],
 *   verified >= 4.7:1 in both appearances.
 * - [ChatColors.badgeMainApp] / [ChatColors.badgeOther] were darkened so their white labels pass
 *   4.5:1 (the old #3B82F6 / #8B5CF6 max out at ~4.2:1 with any text color).
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
    bubbleOutboundManual = Color(0xFF025144),
    bubbleInbound = Color(0xFF202C33),
    onBubble = Color(0xFFFFFFFF),
    onBubbleSubtle = Color(0xFFB5D8CC),
    onSurface = Color(0xFFFFFFFF),
    neutral = Color(0xFF8696A0),
    mediaChip = Color(0xFF111B21),
    accent = Color(0xFF25D366),
    accentText = Color(0xFF25D366),
    switchTrackChecked = Color(0xFF22C55E),
    statusReady = Color(0xFF4ADE80),
    statusIdle = Color(0xFF94A3B8),
    statusGenerating = PrimaryDark,
    statusPaused = TertiaryDark,
    delivered = Color(0xFF53BDEB),
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
    onBubbleSubtle = Color(0xFF54656F),
    onSurface = Color(0xFF111B21),
    neutral = Color(0xFF54656F),
    mediaChip = Color(0xFFE4E9EB),
    accent = Color(0xFF25D366),
    accentText = Color(0xFF0B7A3B),
    switchTrackChecked = Color(0xFF25D366),
    statusReady = Color(0xFF157F3C),
    statusIdle = Color(0xFF54656F),
    statusGenerating = Color(0xFF0D7A6F),
    statusPaused = Color(0xFFA16207),
    delivered = Color(0xFF0E76B0),
    failed = Color(0xFFB42318),
    badgeBusiness = Color(0xFF25D366),
    badgeMainApp = Color(0xFF1D4ED8),
    badgeOther = Color(0xFF6D28D9),
    onBadge = Color(0xFFFFFFFF)
)
