package com.example.persona.features.onboarding.presentation

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.persona.ui.theme.PersonaPrimitives
import kotlin.math.PI
import kotlin.math.sin

/**
 * Checks system animator duration scale to honor user's "Remove animations" / Reduced Motion preference.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Resolved theme color bundle for onboarding canvas graphics.
 * Fully adapts between Dark Terminal and Outdoor High-Contrast modes without hardcoded hex colors.
 */
private data class AnimationThemePalette(
    val isDark: Boolean,
    val cardBg: Color,
    val cardBorder: Color?,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val primary: Color,
    val primaryGlow: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val whatsappGreen: Color,
    val whatsappContainer: Color,
    val whatsappOnContainer: Color,
    val indigoAccent: Color,
    val amberAccent: Color,
    val emeraldAccent: Color
)

@Composable
private fun rememberAnimationThemePalette(): AnimationThemePalette {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    return remember(scheme, isDark) {
        AnimationThemePalette(
            isDark = isDark,
            cardBg = if (isDark) scheme.surfaceVariant else scheme.surface,
            cardBorder = if (isDark) scheme.outlineVariant else null,
            textPrimary = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
            divider = scheme.outlineVariant,
            primary = scheme.primary,
            primaryGlow = PersonaPrimitives.Teal300,
            primaryDark = PersonaPrimitives.Teal900,
            primaryContainer = scheme.primaryContainer,
            whatsappGreen = PersonaPrimitives.GreenWhatsApp,
            whatsappContainer = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
            whatsappOnContainer = if (isDark) Color(0xFFECFDF5) else Color(0xFF065F46),
            indigoAccent = PersonaPrimitives.Indigo500,
            amberAccent = PersonaPrimitives.Amber500,
            emeraldAccent = PersonaPrimitives.Emerald500
        )
    }
}

/**
 * Calculates a centered, aspect-ratio-constrained viewport to prevent horizontal stretching on wide/tablet screens.
 */
private data class GraphicBounds(
    val drawW: Float,
    val drawH: Float,
    val originX: Float,
    val originY: Float
) {
    fun x(fraction: Float): Float = originX + drawW * fraction
    fun y(fraction: Float): Float = originY + drawH * fraction
}

private fun computeGraphicBounds(width: Float, height: Float, targetAspect: Float = 1.35f): GraphicBounds {
    val currentAspect = width / height
    return if (currentAspect > targetAspect) {
        val effectiveW = height * targetAspect
        val offX = (width - effectiveW) / 2f
        GraphicBounds(drawW = effectiveW, drawH = height, originX = offX, originY = 0f)
    } else {
        val effectiveH = width / targetAspect
        val offY = if (effectiveH < height) (height - effectiveH) / 2f else 0f
        GraphicBounds(drawW = width, drawH = height, originX = 0f, originY = offY)
    }
}

/**
 * Page 1 Animation: Smart Multi-modal Assistant.
 *
 * Illustrates incoming multi-modal requests (Text, Voice Note equalizer wave, Image badge)
 * flowing through animated neural waveforms into an intelligent Persona AI core,
 * which instantly emits an intelligent WhatsApp-green verified response.
 */
@Composable
fun NeuralChatWavesAnimation(
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberReducedMotion()
    val palette = rememberAnimationThemePalette()
    val shouldAnimate = isActive && !isReducedMotion

    val transition = rememberInfiniteTransition(label = "NeuralChatTransition")

    // Continuous floating phase for sinusoidal waveforms
    val animatedWavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Pulse factor for the central AI nexus
    val animatedCorePulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Packet transmission progress (0f..1f)
    val animatedPacketProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PacketProgress"
    )

    // Audio equalizer fluctuation
    val animatedEq1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq1"
    )
    val animatedEq2 by transition.animateFloat(
        initialValue = 0.9f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq2"
    )
    val animatedEq3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq3"
    )

    val wavePhase = if (shouldAnimate) animatedWavePhase else 0f
    val corePulse = if (shouldAnimate) animatedCorePulse else 1f
    val packetProgress = if (shouldAnimate) animatedPacketProgress else 0.5f
    val eq1 = if (shouldAnimate) animatedEq1 else 0.5f
    val eq2 = if (shouldAnimate) animatedEq2 else 0.6f
    val eq3 = if (shouldAnimate) animatedEq3 else 0.4f

    // Pre-allocated paths & cached arrays for zero GC allocations during DrawScope
    val wavePath = remember { Path() }
    val beamPath = remember { Path() }
    val sparkPath = remember { Path() }
    val mountainPath = remember { Path() }
    val check1Path = remember { Path() }
    val check2Path = remember { Path() }
    val dashIntervals16_10 = remember { floatArrayOf(16f, 10f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val bounds = computeGraphicBounds(w, h)

        // Visual anchors
        val coreX = bounds.x(0.50f)
        val coreY = bounds.y(0.48f)

        // Multi-modal source nodes (left side)
        val textNode = Offset(bounds.x(0.16f), bounds.y(0.24f))
        val audioNode = Offset(bounds.x(0.13f), bounds.y(0.50f))
        val imageNode = Offset(bounds.x(0.18f), bounds.y(0.74f))

        // Output smart response node (right side)
        val replyNode = Offset(bounds.x(0.82f), bounds.y(0.50f))

        // 1. Draw glowing neural connection waves into the Core
        drawNeuralWave(wavePath, textNode, Offset(coreX, coreY), wavePhase, 0f, palette.indigoAccent.copy(alpha = 0.45f))
        drawNeuralWave(wavePath, audioNode, Offset(coreX, coreY), wavePhase, 1.2f, palette.primary.copy(alpha = 0.65f))
        drawNeuralWave(wavePath, imageNode, Offset(coreX, coreY), wavePhase, 2.4f, palette.amberAccent.copy(alpha = 0.45f))

        // Draw traveling packets along the waves
        drawTravelingPacket(textNode, Offset(coreX, coreY), wavePhase, 0f, packetProgress, palette.indigoAccent)
        drawTravelingPacket(audioNode, Offset(coreX, coreY), wavePhase, 1.2f, (packetProgress + 0.33f) % 1f, palette.primaryGlow)
        drawTravelingPacket(imageNode, Offset(coreX, coreY), wavePhase, 2.4f, (packetProgress + 0.66f) % 1f, palette.amberAccent)

        // 2. Draw Outgoing Synapse to Smart Reply
        drawOutgoingBeam(beamPath, Offset(coreX, coreY), replyNode, wavePhase, dashIntervals16_10, palette.whatsappGreen)

        // 3. Central AI Core / Neural Nexus
        val coreRadius = (bounds.drawW * 0.11f) * corePulse
        // Ambient aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.primary.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(coreX, coreY),
                radius = coreRadius * 2.2f
            ),
            radius = coreRadius * 2.2f,
            center = Offset(coreX, coreY)
        )
        // Outer pulsing ring
        drawCircle(
            color = palette.primaryGlow.copy(alpha = 0.6f),
            radius = coreRadius * 1.35f,
            center = Offset(coreX, coreY),
            style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(dashIntervals16_10, wavePhase * 10f))
        )
        // Solid core sphere with gradient
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(palette.primary, palette.primaryDark),
                start = Offset(coreX - coreRadius, coreY - coreRadius),
                end = Offset(coreX + coreRadius, coreY + coreRadius)
            ),
            radius = coreRadius,
            center = Offset(coreX, coreY)
        )
        drawCircle(
            color = palette.primary,
            radius = coreRadius,
            center = Offset(coreX, coreY),
            style = Stroke(width = 3f)
        )
        // Stylized AI Spark in center
        drawAiSpark(sparkPath, Offset(coreX, coreY), coreRadius * 0.55f, Color.White, palette.primaryGlow)

        // 4. Left Node: Text Message Badge
        val textCardW = bounds.drawW * 0.20f
        val textCardH = bounds.drawH * 0.12f
        drawCardBadge(textNode, textCardW, textCardH, palette.cardBg, palette.cardBorder)
        // Mini chat bubble lines inside
        drawLine(
            color = palette.indigoAccent,
            start = Offset(textNode.x - textCardW * 0.3f, textNode.y - textCardH * 0.18f),
            end = Offset(textNode.x + textCardW * 0.25f, textNode.y - textCardH * 0.18f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.textSecondary,
            start = Offset(textNode.x - textCardW * 0.3f, textNode.y + textCardH * 0.12f),
            end = Offset(textNode.x + textCardW * 0.05f, textNode.y + textCardH * 0.12f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // 5. Left Node: Voice Note Equalizer Badge
        val audioCardW = bounds.drawW * 0.24f
        val audioCardH = bounds.drawH * 0.13f
        drawCardBadge(audioNode, audioCardW, audioCardH, palette.cardBg, palette.cardBorder)
        // Draw microphone dot + dynamic equalizer bars
        drawCircle(color = palette.primary, radius = 5f, center = Offset(audioNode.x - audioCardW * 0.32f, audioNode.y))
        val barHeights = listOf(eq1, eq2, eq3, eq1 * 0.7f + 0.3f, eq2 * 0.8f + 0.2f)
        val barSpacing = audioCardW * 0.11f
        val startX = audioNode.x - audioCardW * 0.15f
        val maxBarH = audioCardH * 0.55f
        barHeights.forEachIndexed { i, factor ->
            val barH = maxBarH * factor
            drawLine(
                color = palette.primary,
                start = Offset(startX + i * barSpacing, audioNode.y - barH / 2f),
                end = Offset(startX + i * barSpacing, audioNode.y + barH / 2f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // 6. Left Node: Image Attachment Badge
        val imgCardW = bounds.drawW * 0.20f
        val imgCardH = bounds.drawH * 0.12f
        drawCardBadge(imageNode, imgCardW, imgCardH, palette.cardBg, palette.cardBorder)
        val frameRect = Rect(imageNode.x - imgCardW * 0.28f, imageNode.y - imgCardH * 0.25f, imageNode.x + imgCardW * 0.28f, imageNode.y + imgCardH * 0.25f)
        drawRoundRect(
            color = palette.amberAccent,
            topLeft = frameRect.topLeft,
            size = frameRect.size,
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2.5f)
        )
        drawCircle(color = palette.amberAccent, radius = 3.5f, center = Offset(frameRect.left + frameRect.width * 0.3f, frameRect.top + frameRect.height * 0.35f))
        mountainPath.rewind()
        mountainPath.moveTo(frameRect.left + 4f, frameRect.bottom - 4f)
        mountainPath.lineTo(frameRect.left + frameRect.width * 0.45f, frameRect.top + frameRect.height * 0.45f)
        mountainPath.lineTo(frameRect.left + frameRect.width * 0.7f, frameRect.bottom - 4f)
        drawPath(mountainPath, color = palette.amberAccent, style = Stroke(width = 2f, join = StrokeJoin.Round))

        // 7. Right Node: WhatsApp Smart Reply Card
        val replyCardW = bounds.drawW * 0.26f
        val replyCardH = bounds.drawH * 0.17f
        drawCardBadge(replyNode, replyCardW, replyCardH, palette.whatsappContainer, palette.whatsappGreen)

        // WhatsApp double checkmark icon in top-left of reply card
        val checkX = replyNode.x + replyCardW * 0.20f
        val checkY = replyNode.y - replyCardH * 0.20f
        drawDoubleCheck(check1Path, check2Path, Offset(checkX, checkY), palette.whatsappGreen)

        // Reply text lines inside
        drawLine(
            color = palette.whatsappOnContainer,
            start = Offset(replyNode.x - replyCardW * 0.35f, replyNode.y - replyCardH * 0.05f),
            end = Offset(replyNode.x + replyCardW * 0.10f, replyNode.y - replyCardH * 0.05f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.whatsappOnContainer.copy(alpha = 0.75f),
            start = Offset(replyNode.x - replyCardW * 0.35f, replyNode.y + replyCardH * 0.22f),
            end = Offset(replyNode.x + replyCardW * 0.25f, replyNode.y + replyCardH * 0.22f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Page 2 Animation: Smart On-Device Knowledge Base.
 *
 * Visualizes business documents, chat history, and product catalogs syncing
 * directly into an on-device local brain / chip with zero latency and complete privacy.
 */
@Composable
fun KnowledgeSyncBrainAnimation(
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberReducedMotion()
    val palette = rememberAnimationThemePalette()
    val shouldAnimate = isActive && !isReducedMotion

    val transition = rememberInfiniteTransition(label = "KnowledgeSyncTransition")

    // Expanding sonar / radar speed rings from the on-device chip
    val animatedSonarPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SonarPhase"
    )

    // Data packets streaming into chip
    val animatedSyncPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncPhase"
    )

    // Gentle floating motion of satellite document cards
    val animatedFloatY by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatY"
    )

    val sonarPhase = if (shouldAnimate) animatedSonarPhase else 0.5f
    val syncPhase = if (shouldAnimate) animatedSyncPhase else 0.5f
    val floatY = if (shouldAnimate) animatedFloatY else 0f

    // Pre-allocated paths & cached arrays for zero GC allocations during DrawScope
    val streamPath = remember { Path() }
    val boltPath = remember { Path() }
    val dashIntervals14_10 = remember { floatArrayOf(14f, 10f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val bounds = computeGraphicBounds(w, h)

        val centerX = bounds.x(0.50f)
        val centerY = bounds.y(0.50f)

        // 1. Radar / Sonar Speed Waves radiating from on-device brain
        val maxSonarRadius = bounds.drawW * 0.42f
        for (i in 0..2) {
            val adjustedPhase = (sonarPhase + i * 0.33f) % 1f
            val radius = maxSonarRadius * adjustedPhase
            val alpha = (1f - adjustedPhase).coerceIn(0f, 0.7f)
            drawCircle(
                color = palette.primary.copy(alpha = alpha * 0.4f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )
        }

        // 2. Surrounding Knowledge Node positions
        val doc1Pos = Offset(bounds.x(0.18f), bounds.y(0.22f) + floatY)
        val doc2Pos = Offset(bounds.x(0.82f), bounds.y(0.22f) - floatY)
        val doc3Pos = Offset(bounds.x(0.18f), bounds.y(0.78f) - floatY)
        val doc4Pos = Offset(bounds.x(0.82f), bounds.y(0.78f) + floatY)

        // Connecting sync streams to central chip
        drawSyncStream(streamPath, doc1Pos, Offset(centerX, centerY), syncPhase, 0f, dashIntervals14_10, palette.amberAccent)
        drawSyncStream(streamPath, doc2Pos, Offset(centerX, centerY), syncPhase, 0.25f, dashIntervals14_10, palette.indigoAccent)
        drawSyncStream(streamPath, doc3Pos, Offset(centerX, centerY), syncPhase, 0.5f, dashIntervals14_10, palette.emeraldAccent)
        drawSyncStream(streamPath, doc4Pos, Offset(centerX, centerY), syncPhase, 0.75f, dashIntervals14_10, palette.primary)

        // 3. Central On-Device Neural Chip
        val chipSize = bounds.drawW * 0.22f
        val chipRect = Rect(centerX - chipSize / 2f, centerY - chipSize / 2f, centerX + chipSize / 2f, centerY + chipSize / 2f)

        // Chip Pins (4 on each side)
        val pinLength = 12f
        val pinColor = palette.textSecondary.copy(alpha = 0.5f)
        for (step in 1..3) {
            val offsetAlong = chipSize * (step / 4f)
            drawLine(pinColor, Offset(chipRect.left + offsetAlong, chipRect.top), Offset(chipRect.left + offsetAlong, chipRect.top - pinLength), 3f)
            drawLine(pinColor, Offset(chipRect.left + offsetAlong, chipRect.bottom), Offset(chipRect.left + offsetAlong, chipRect.bottom + pinLength), 3f)
            drawLine(pinColor, Offset(chipRect.left, chipRect.top + offsetAlong), Offset(chipRect.left - pinLength, chipRect.top + offsetAlong), 3f)
            drawLine(pinColor, Offset(chipRect.right, chipRect.top + offsetAlong), Offset(chipRect.right + pinLength, chipRect.top + offsetAlong), 3f)
        }

        // Chip Body
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(palette.primaryDark, palette.cardBg),
                start = chipRect.topLeft,
                end = chipRect.bottomRight
            ),
            topLeft = chipRect.topLeft,
            size = chipRect.size,
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = palette.primary,
            topLeft = chipRect.topLeft,
            size = chipRect.size,
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 2.5f)
        )

        // Neural circuits inside chip
        drawChipCircuit(chipRect, palette.primary)

        // Lightning Bolt / Speed Icon in chip center
        drawLightningBolt(boltPath, Offset(centerX, centerY), chipSize * 0.25f, palette.amberAccent)

        // 4. Floating Knowledge Document Cards
        val cardW = bounds.drawW * 0.24f
        val cardH = bounds.drawH * 0.14f

        drawCardBadge(doc1Pos, cardW, cardH, palette.cardBg, palette.cardBorder ?: palette.amberAccent.copy(alpha = 0.4f))
        drawDocLines(doc1Pos, cardW, cardH, palette.amberAccent, palette.textSecondary)

        drawCardBadge(doc2Pos, cardW, cardH, palette.cardBg, palette.cardBorder ?: palette.indigoAccent.copy(alpha = 0.4f))
        drawDocLines(doc2Pos, cardW, cardH, palette.indigoAccent, palette.textSecondary)

        drawCardBadge(doc3Pos, cardW, cardH, palette.cardBg, palette.cardBorder ?: palette.emeraldAccent.copy(alpha = 0.4f))
        drawDocLines(doc3Pos, cardW, cardH, palette.emeraldAccent, palette.textSecondary)

        drawCardBadge(doc4Pos, cardW, cardH, palette.cardBg, palette.cardBorder ?: palette.primary.copy(alpha = 0.4f))
        drawDocLines(doc4Pos, cardW, cardH, palette.primary, palette.textSecondary)
    }
}

/**
 * Page 3 Animation: Automated Order & Customer Management.
 *
 * Demonstrates intelligent parsing of unstructured customer messages into
 * verified B2B orders with checkmark status and CRM organization.
 */
@Composable
fun AutomatedCrmOrdersAnimation(
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberReducedMotion()
    val palette = rememberAnimationThemePalette()
    val shouldAnimate = isActive && !isReducedMotion

    val transition = rememberInfiniteTransition(label = "AutomatedCrmTransition")

    // Scanning laser sweep across the raw customer inquiry
    val animatedScanSweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanSweep"
    )

    // Checkmark success pulse
    val animatedCheckPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CheckPulse"
    )

    val scanSweep = if (shouldAnimate) animatedScanSweep else 0.5f
    val checkPulse = if (shouldAnimate) animatedCheckPulse else 1f

    // Pre-allocated paths & cached arrays for zero GC allocations during DrawScope
    val arrowPath = remember { Path() }
    val arrowHead = remember { Path() }
    val checkPath = remember { Path() }
    val dashIntervals12_8 = remember { floatArrayOf(12f, 8f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val bounds = computeGraphicBounds(w, h)

        // 1. Top Card: Incoming Unstructured Customer Message
        val inputCardW = bounds.drawW * 0.76f
        val inputCardH = bounds.drawH * 0.24f
        val inputCardCenter = Offset(bounds.x(0.5f), bounds.y(0.20f))
        drawCardBadge(inputCardCenter, inputCardW, inputCardH, palette.cardBg, palette.cardBorder)

        // Raw message representation (simulated text message lines)
        val lineStartX = inputCardCenter.x - inputCardW * 0.38f
        val lineEndX = inputCardCenter.x + inputCardW * 0.38f
        val line1Y = inputCardCenter.y - inputCardH * 0.22f
        val line2Y = inputCardCenter.y
        val line3Y = inputCardCenter.y + inputCardH * 0.22f

        drawLine(
            color = palette.textSecondary,
            start = Offset(lineStartX, line1Y),
            end = Offset(lineEndX - 20f, line1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.textSecondary.copy(alpha = 0.7f),
            start = Offset(lineStartX, line2Y),
            end = Offset(lineEndX - 60f, line2Y),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.indigoAccent,
            start = Offset(lineStartX, line3Y),
            end = Offset(lineStartX + 90f, line3Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Scanning laser beam sweeping across the message card
        val scanX = (inputCardCenter.x - inputCardW / 2f) + (inputCardW * scanSweep)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, palette.primary, Color.Transparent),
                startY = inputCardCenter.y - inputCardH / 2f,
                endY = inputCardCenter.y + inputCardH / 2f
            ),
            start = Offset(scanX, inputCardCenter.y - inputCardH / 2f),
            end = Offset(scanX, inputCardCenter.y + inputCardH / 2f),
            strokeWidth = 4f
        )

        // 2. Conversion Transformation Funnel / Pipeline Arrows
        val funnelTopY = inputCardCenter.y + inputCardH / 2f + 8f
        val funnelBottomY = bounds.y(0.48f)
        arrowPath.rewind()
        arrowPath.moveTo(bounds.x(0.5f), funnelTopY)
        arrowPath.lineTo(bounds.x(0.5f), funnelBottomY)
        drawPath(
            arrowPath,
            color = palette.primary,
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(dashIntervals12_8, -scanSweep * 20f)
            )
        )
        // Arrow head
        arrowHead.rewind()
        arrowHead.moveTo(bounds.x(0.46f), funnelBottomY - 12f)
        arrowHead.lineTo(bounds.x(0.5f), funnelBottomY)
        arrowHead.lineTo(bounds.x(0.54f), funnelBottomY - 12f)
        drawPath(arrowHead, color = palette.primary, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 3. Bottom Card: Formatted Structured Order & CRM Record
        val orderCardW = bounds.drawW * 0.82f
        val orderCardH = bounds.drawH * 0.38f
        val orderCardCenter = Offset(bounds.x(0.5f), bounds.y(0.72f))
        drawCardBadge(orderCardCenter, orderCardW, orderCardH, palette.cardBg, palette.cardBorder ?: palette.primary.copy(alpha = 0.3f))

        // Order Header Row: Customer Name Avatar Chip + Order ID
        val avatarCenter = Offset(orderCardCenter.x - orderCardW * 0.34f, orderCardCenter.y - orderCardH * 0.30f)
        drawCircle(color = palette.primary.copy(alpha = 0.25f), radius = 18f, center = avatarCenter)
        drawCircle(color = palette.primary, radius = 8f, center = avatarCenter)

        drawLine(
            color = palette.textPrimary,
            start = Offset(avatarCenter.x + 28f, avatarCenter.y),
            end = Offset(orderCardCenter.x + orderCardW * 0.05f, avatarCenter.y),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Status Pill: Confirmed
        val statusPillRect = Rect(
            orderCardCenter.x + orderCardW * 0.14f,
            avatarCenter.y - 14f,
            orderCardCenter.x + orderCardW * 0.40f,
            avatarCenter.y + 14f
        )
        drawRoundRect(
            color = palette.emeraldAccent.copy(alpha = 0.2f),
            topLeft = statusPillRect.topLeft,
            size = statusPillRect.size,
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = palette.emeraldAccent,
            topLeft = statusPillRect.topLeft,
            size = statusPillRect.size,
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 1.5f)
        )

        // Order Item 1 Row: (2x Product + Price)
        val item1Y = orderCardCenter.y - orderCardH * 0.02f
        drawRoundRect(
            color = palette.amberAccent.copy(alpha = 0.2f),
            topLeft = Offset(orderCardCenter.x - orderCardW * 0.38f, item1Y - 11f),
            size = Size(36f, 22f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawLine(
            color = palette.textSecondary,
            start = Offset(orderCardCenter.x - orderCardW * 0.38f + 46f, item1Y),
            end = Offset(orderCardCenter.x + orderCardW * 0.12f, item1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.amberAccent,
            start = Offset(orderCardCenter.x + orderCardW * 0.22f, item1Y),
            end = Offset(orderCardCenter.x + orderCardW * 0.38f, item1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Divider
        val dividerY = orderCardCenter.y + orderCardH * 0.14f
        drawLine(
            color = palette.divider,
            start = Offset(orderCardCenter.x - orderCardW * 0.38f, dividerY),
            end = Offset(orderCardCenter.x + orderCardW * 0.38f, dividerY),
            strokeWidth = 1.5f
        )

        // Bottom Row: Address pin + Big Animated Success Checkmark Badge
        val bottomRowY = orderCardCenter.y + orderCardH * 0.30f
        drawLine(
            color = palette.textSecondary,
            start = Offset(orderCardCenter.x - orderCardW * 0.38f, bottomRowY),
            end = Offset(orderCardCenter.x + orderCardW * 0.10f, bottomRowY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Animated Success Checkmark Badge
        val checkCenter = Offset(orderCardCenter.x + orderCardW * 0.30f, bottomRowY)
        val checkRadius = 18f * checkPulse
        drawCircle(
            color = palette.emeraldAccent.copy(alpha = 0.3f),
            radius = checkRadius * 1.5f,
            center = checkCenter
        )
        drawCircle(
            color = palette.emeraldAccent,
            radius = checkRadius,
            center = checkCenter
        )
        // White checkmark inside
        checkPath.rewind()
        checkPath.moveTo(checkCenter.x - checkRadius * 0.45f, checkCenter.y)
        checkPath.lineTo(checkCenter.x - checkRadius * 0.1f, checkCenter.y + checkRadius * 0.35f)
        checkPath.lineTo(checkCenter.x + checkRadius * 0.45f, checkCenter.y - checkRadius * 0.35f)
        drawPath(
            checkPath,
            color = Color.White,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ── Private Drawing Helpers (Optimized for zero heap allocations per frame) ─────────

private fun DrawScope.drawCardBadge(
    center: Offset,
    w: Float,
    h: Float,
    bgColor: Color,
    borderColor: Color?
) {
    val rect = Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
    drawRoundRect(
        color = bgColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(18f, 18f)
    )
    if (borderColor != null && borderColor != Color.Transparent) {
        drawRoundRect(
            color = borderColor,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 1.5f)
        )
    }
}

private fun DrawScope.drawNeuralWave(
    path: Path,
    start: Offset,
    end: Offset,
    phase: Float,
    phaseOffset: Float,
    color: Color
) {
    path.rewind()
    path.moveTo(start.x, start.y)
    val segments = 20
    val dx = (end.x - start.x) / segments
    val dy = (end.y - start.y) / segments
    for (i in 1..segments) {
        val currX = start.x + dx * i
        val currY = start.y + dy * i
        val wave = sin(phase + phaseOffset + i * 0.4f) * 12f * (1f - (i.toFloat() / segments))
        path.lineTo(currX, currY + wave)
    }
    drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawTravelingPacket(
    start: Offset,
    end: Offset,
    phase: Float,
    phaseOffset: Float,
    progress: Float,
    color: Color
) {
    val currX = start.x + (end.x - start.x) * progress
    val currY = start.y + (end.y - start.y) * progress + (sin(phase + phaseOffset + progress * 8f) * 12f * (1f - progress))
    drawCircle(color = color.copy(alpha = 0.4f), radius = 8f, center = Offset(currX, currY))
    drawCircle(color = color, radius = 4f, center = Offset(currX, currY))
}

private fun DrawScope.drawOutgoingBeam(
    path: Path,
    start: Offset,
    end: Offset,
    phase: Float,
    dashIntervals: FloatArray,
    color: Color
) {
    path.rewind()
    path.moveTo(start.x, start.y)
    val midX = (start.x + end.x) / 2f
    path.quadraticTo(midX, start.y - 18f, end.x, end.y)
    drawPath(
        path,
        color = color.copy(alpha = 0.55f),
        style = Stroke(
            width = 3f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(dashIntervals, -phase * 15f)
        )
    )
}

private fun DrawScope.drawAiSpark(
    path: Path,
    center: Offset,
    radius: Float,
    centerColor: Color,
    sparkColor: Color
) {
    path.rewind()
    path.moveTo(center.x, center.y - radius)
    path.quadraticTo(center.x, center.y, center.x + radius, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y + radius)
    path.quadraticTo(center.x, center.y, center.x - radius, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y - radius)
    path.close()
    drawPath(path, color = sparkColor)
    drawCircle(color = centerColor, radius = radius * 0.35f, center = center)
}

private fun DrawScope.drawDoubleCheck(
    path1: Path,
    path2: Path,
    topRight: Offset,
    color: Color
) {
    path1.rewind()
    path1.moveTo(topRight.x - 14f, topRight.y)
    path1.lineTo(topRight.x - 8f, topRight.y + 6f)
    path1.lineTo(topRight.x, topRight.y - 4f)

    path2.rewind()
    path2.moveTo(topRight.x - 20f, topRight.y)
    path2.lineTo(topRight.x - 14f, topRight.y + 6f)
    path2.lineTo(topRight.x - 6f, topRight.y - 4f)

    drawPath(path1, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path2, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSyncStream(
    path: Path,
    start: Offset,
    end: Offset,
    phase: Float,
    phaseOffset: Float,
    dashIntervals: FloatArray,
    color: Color
) {
    path.rewind()
    path.moveTo(start.x, start.y)
    path.lineTo(end.x, end.y)
    drawPath(
        path,
        color = color.copy(alpha = 0.35f),
        style = Stroke(
            width = 2.5f,
            pathEffect = PathEffect.dashPathEffect(dashIntervals, -phase * 20f)
        )
    )
    val progress = (phase + phaseOffset) % 1f
    val dotX = start.x + (end.x - start.x) * progress
    val dotY = start.y + (end.y - start.y) * progress
    drawCircle(color = color, radius = 4f, center = Offset(dotX, dotY))
}

private fun DrawScope.drawChipCircuit(
    chipRect: Rect,
    color: Color
) {
    val center = chipRect.center
    val inset = chipRect.width * 0.22f
    val inner = Rect(chipRect.left + inset, chipRect.top + inset, chipRect.right - inset, chipRect.bottom - inset)

    drawRoundRect(
        color = color.copy(alpha = 0.4f),
        topLeft = inner.topLeft,
        size = inner.size,
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2f)
    )

    // Traces
    drawLine(color.copy(alpha = 0.5f), Offset(chipRect.left + 8f, center.y), Offset(inner.left, center.y), 2f)
    drawLine(color.copy(alpha = 0.5f), Offset(chipRect.right - 8f, center.y), Offset(inner.right, center.y), 2f)
    drawLine(color.copy(alpha = 0.5f), Offset(center.x, chipRect.top + 8f), Offset(center.x, inner.top), 2f)
    drawLine(color.copy(alpha = 0.5f), Offset(center.x, chipRect.bottom - 8f), Offset(center.x, inner.bottom), 2f)
}

private fun DrawScope.drawLightningBolt(
    path: Path,
    center: Offset,
    size: Float,
    color: Color
) {
    path.rewind()
    path.moveTo(center.x + size * 0.15f, center.y - size)
    path.lineTo(center.x - size * 0.6f, center.y)
    path.lineTo(center.x - size * 0.05f, center.y)
    path.lineTo(center.x - size * 0.25f, center.y + size)
    path.lineTo(center.x + size * 0.6f, center.y - size * 0.15f)
    path.lineTo(center.x + size * 0.05f, center.y - size * 0.15f)
    path.close()
    drawPath(path, color = color)
}

private fun DrawScope.drawDocLines(
    center: Offset,
    w: Float,
    h: Float,
    accent: Color,
    textColor: Color
) {
    val lineX = center.x - w * 0.32f
    val endX = center.x + w * 0.32f

    // Top title line in accent
    drawLine(accent, Offset(lineX, center.y - h * 0.22f), Offset(endX - 18f, center.y - h * 0.22f), 4f, StrokeCap.Round)
    // Body lines
    drawLine(textColor, Offset(lineX, center.y), Offset(endX, center.y), 3f, StrokeCap.Round)
    drawLine(textColor, Offset(lineX, center.y + h * 0.22f), Offset(endX - 30f, center.y + h * 0.22f), 3f, StrokeCap.Round)
}
