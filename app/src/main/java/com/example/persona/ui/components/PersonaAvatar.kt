package com.example.persona.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.persona.ui.theme.PersonaPrimitives
import kotlin.math.abs

enum class AvatarShape { CIRCULAR, ROUNDED }

/**
 * Curated list of container-content color pairs used for deterministic hash tinting.
 */
private val DefaultAvatarTints = listOf(
    PersonaPrimitives.Teal800 to PersonaPrimitives.Teal100,
    PersonaPrimitives.Indigo400 to PersonaPrimitives.Slate900,
    PersonaPrimitives.Blue500 to Color.White,
    PersonaPrimitives.Emerald400 to PersonaPrimitives.Teal900,
    PersonaPrimitives.Amber400 to PersonaPrimitives.Slate900,
    PersonaPrimitives.Slate700 to PersonaPrimitives.Slate100,
    PersonaPrimitives.Teal600 to Color.White
)

/**
 * Extracts a 1 or 2 letter uppercase monogram from [name].
 * Correctly handles:
 * - Multi-word Latin names (e.g. "Ahmed Hassan" -> "AH")
 * - Compound Arabic names with prefixes like "عبد الله" -> "عل" or "أبو بكر" -> "أب"
 * - Single-word Arabic/Latin names safely
 */
fun extractMonogram(name: String, letterCount: Int = 2): String {
    val clean = name.trim().replace("\u202A", "").replace("\u202C", "").replace("\u200E", "").replace("\u200F", "")
    if (clean.isEmpty()) return "?"

    // If the contact name is a phone number (e.g. starts with + or digits), return "#" or last 2 digits
    if (clean.startsWith("+") || clean.take(2).all { it.isDigit() }) {
        val digitsOnly = clean.filter { it.isDigit() }
        return if (digitsOnly.length >= 2) {
            digitsOnly.takeLast(2)
        } else {
            "#"
        }
    }

    val words = clean.split("\\s+".toRegex()).filter { it.isNotEmpty() }

    val compoundPrefixes = setOf("عبد", "أبو", "ابو", "أم", "ام", "آل", "ابن", "بن")
    return if (words.size >= 2 && letterCount >= 2) {
        val firstWord = words[0]
        val secondWord = words[1]
        if (firstWord in compoundPrefixes) {
            val secondCore = stripArabicArticle(secondWord)
            (firstWord.take(1) + secondCore.take(1)).uppercase()
        } else {
            val firstCore = stripArabicArticle(firstWord)
            val secondCore = stripArabicArticle(secondWord)
            (firstCore.take(1) + secondCore.take(1)).uppercase()
        }
    } else if (words.isNotEmpty()) {
        val firstCore = stripArabicArticle(words[0])
        firstCore.take(letterCount).uppercase()
    } else {
        clean.take(letterCount).uppercase()
    }
}

/**
 * Helper to strip Arabic definite articles ("ال", "أل", "إل", "آل") from a word
 * to extract the meaningful core letter for avatar monograms (e.g. "المهندس" -> "مهندس").
 */
private fun stripArabicArticle(word: String): String {
    return if ((word.startsWith("ال") || word.startsWith("أل") || word.startsWith("إل") || word.startsWith("آل")) && word.length > 2) {
        word.substring(2)
    } else {
        word
    }
}

/**
 * Consolidated Avatar Monogram component with hash tinting and accessibility semantics.
 */
@Composable
fun PersonaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: AvatarShape = AvatarShape.ROUNDED,
    letterCount: Int = 2,
    containerColor: Color? = null,
    contentColor: Color? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val monogram = extractMonogram(name, letterCount)

    // Deterministic tint hash if containerColor is not explicitly provided
    val resolvedContainerColor: Color
    val resolvedContentColor: Color
    if (containerColor != null) {
        resolvedContainerColor = containerColor
        resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        val tintIndex = abs(name.hashCode()) % DefaultAvatarTints.size
        val (tintBg, tintFg) = DefaultAvatarTints[tintIndex]
        resolvedContainerColor = tintBg
        resolvedContentColor = contentColor ?: tintFg
    }

    val resolvedShape: Shape = when (shape) {
        AvatarShape.CIRCULAR -> CircleShape
        AvatarShape.ROUNDED -> RoundedCornerShape(size * 0.28f)
    }

    val textStyle = when {
        size >= 48.dp -> MaterialTheme.typography.titleMedium
        size >= 40.dp -> MaterialTheme.typography.titleSmall
        size >= 36.dp -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelSmall
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "صورة رمزية لـ $name"
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(resolvedShape)
                .background(resolvedContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                color = resolvedContentColor,
                style = textStyle,
                fontWeight = FontWeight.Bold
            )
        }
        if (badge != null) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                badge()
            }
        }
    }
}
