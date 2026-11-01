package com.example.persona.features.onboarding.presentation

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
import com.example.persona.ui.theme.PersonaPrimitives
import kotlin.math.PI
import kotlin.math.sin

/**
 * Page 1 Animation: Smart Multi-modal Assistant.
 *
 * Illustrates incoming multi-modal requests (Text, Voice Note equalizer wave, Image badge)
 * flowing through animated neural waveforms into an intelligent Persona AI core,
 * which instantly emits an intelligent WhatsApp-green verified response.
 */
@Composable
fun NeuralChatWavesAnimation(
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val transition = rememberInfiniteTransition(label = "NeuralChatTransition")

    // Continuous floating phase for sinusoidal waveforms
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Pulse factor for the central AI nexus
    val corePulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Packet transmission progress (0f..1f)
    val packetProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PacketProgress"
    )

    // Audio equalizer fluctuation
    val eq1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq1"
    )
    val eq2 by transition.animateFloat(
        initialValue = 0.9f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq2"
    )
    val eq3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Eq3"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Visual anchors
        val coreX = w * 0.50f
        val coreY = h * 0.48f

        // Multi-modal source nodes (left side)
        val textNode = Offset(w * 0.16f, h * 0.24f)
        val audioNode = Offset(w * 0.13f, h * 0.50f)
        val imageNode = Offset(w * 0.18f, h * 0.74f)

        // Output smart response node (right side)
        val replyNode = Offset(w * 0.82f, h * 0.50f)

        // Colors
        val nodeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        val nodeBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        val tealPrimary = PersonaPrimitives.Teal500
        val tealGlow = PersonaPrimitives.Teal300
        val greenWhatsApp = Color(0xFF25D366)
        val indigoAccent = Color(0xFF6366F1)
        val amberAccent = Color(0xFFF59E0B)

        // 1. Draw glowing neural connection waves into the Core
        drawNeuralWave(textNode, Offset(coreX, coreY), wavePhase, 0f, indigoAccent.copy(alpha = 0.45f))
        drawNeuralWave(audioNode, Offset(coreX, coreY), wavePhase, 1.2f, tealPrimary.copy(alpha = 0.65f))
        drawNeuralWave(imageNode, Offset(coreX, coreY), wavePhase, 2.4f, amberAccent.copy(alpha = 0.45f))

        // Draw traveling packets along the waves
        drawTravelingPacket(textNode, Offset(coreX, coreY), wavePhase, 0f, packetProgress, indigoAccent)
        drawTravelingPacket(audioNode, Offset(coreX, coreY), wavePhase, 1.2f, (packetProgress + 0.33f) % 1f, tealGlow)
        drawTravelingPacket(imageNode, Offset(coreX, coreY), wavePhase, 2.4f, (packetProgress + 0.66f) % 1f, amberAccent)

        // 2. Draw Outgoing Synapse to Smart Reply
        drawOutgoingBeam(Offset(coreX, coreY), replyNode, wavePhase, greenWhatsApp)

        // 3. Central AI Core / Neural Nexus
        val coreRadius = (w * 0.11f) * corePulse
        // Ambient aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tealPrimary.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(coreX, coreY),
                radius = coreRadius * 2.2f
            ),
            radius = coreRadius * 2.2f,
            center = Offset(coreX, coreY)
        )
        // Outer pulsing ring
        drawCircle(
            color = tealGlow.copy(alpha = 0.6f),
            radius = coreRadius * 1.35f,
            center = Offset(coreX, coreY),
            style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), wavePhase * 10f))
        )
        // Solid core sphere with gradient
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(PersonaPrimitives.Teal700, PersonaPrimitives.Teal900),
                start = Offset(coreX - coreRadius, coreY - coreRadius),
                end = Offset(coreX + coreRadius, coreY + coreRadius)
            ),
            radius = coreRadius,
            center = Offset(coreX, coreY)
        )
        drawCircle(
            color = tealPrimary,
            radius = coreRadius,
            center = Offset(coreX, coreY),
            style = Stroke(width = 3f)
        )
        // Stylized AI Spark in center
        drawAiSpark(Offset(coreX, coreY), coreRadius * 0.55f, Color.White, tealGlow)

        // 4. Left Node: Text Message Badge
        val textCardW = w * 0.20f
        val textCardH = h * 0.12f
        drawCardBadge(textNode, textCardW, textCardH, nodeBg, nodeBorder)
        // Mini chat bubble lines inside
        drawLine(
            color = indigoAccent,
            start = Offset(textNode.x - textCardW * 0.3f, textNode.y - textCardH * 0.18f),
            end = Offset(textNode.x + textCardW * 0.25f, textNode.y - textCardH * 0.18f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            start = Offset(textNode.x - textCardW * 0.3f, textNode.y + textCardH * 0.12f),
            end = Offset(textNode.x + textCardW * 0.05f, textNode.y + textCardH * 0.12f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // 5. Left Node: Voice Note Equalizer Badge
        val audioCardW = w * 0.24f
        val audioCardH = h * 0.13f
        drawCardBadge(audioNode, audioCardW, audioCardH, nodeBg, nodeBorder)
        // Draw microphone dot + 5 dynamic equalizer bars
        drawCircle(color = tealPrimary, radius = 5f, center = Offset(audioNode.x - audioCardW * 0.32f, audioNode.y))
        val barHeights = listOf(eq1, eq2, eq3, eq1 * 0.7f + 0.3f, eq2 * 0.8f + 0.2f)
        val barSpacing = audioCardW * 0.11f
        val startX = audioNode.x - audioCardW * 0.15f
        val maxBarH = audioCardH * 0.55f
        barHeights.forEachIndexed { i, factor ->
            val barH = maxBarH * factor
            drawLine(
                color = tealPrimary,
                start = Offset(startX + i * barSpacing, audioNode.y - barH / 2f),
                end = Offset(startX + i * barSpacing, audioNode.y + barH / 2f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // 6. Left Node: Image Attachment Badge
        val imgCardW = w * 0.20f
        val imgCardH = h * 0.12f
        drawCardBadge(imageNode, imgCardW, imgCardH, nodeBg, nodeBorder)
        // Draw small camera/frame vector
        val frameRect = Rect(imageNode.x - imgCardW * 0.28f, imageNode.y - imgCardH * 0.25f, imageNode.x + imgCardW * 0.28f, imageNode.y + imgCardH * 0.25f)
        drawRoundRect(
            color = amberAccent,
            topLeft = frameRect.topLeft,
            size = frameRect.size,
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2.5f)
        )
        drawCircle(color = amberAccent, radius = 3.5f, center = Offset(frameRect.left + frameRect.width * 0.3f, frameRect.top + frameRect.height * 0.35f))
        val mountainPath = Path().apply {
            moveTo(frameRect.left + 4f, frameRect.bottom - 4f)
            lineTo(frameRect.left + frameRect.width * 0.45f, frameRect.top + frameRect.height * 0.45f)
            lineTo(frameRect.left + frameRect.width * 0.7f, frameRect.bottom - 4f)
        }
        drawPath(mountainPath, color = amberAccent, style = Stroke(width = 2f, join = StrokeJoin.Round))

        // 7. Right Node: WhatsApp Smart Reply Card
        val replyCardW = w * 0.26f
        val replyCardH = h * 0.17f
        val replyBg = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5)
        val replyBorder = greenWhatsApp
        drawCardBadge(replyNode, replyCardW, replyCardH, replyBg, replyBorder)

        // WhatsApp double checkmark icon in top-left of reply card
        val checkX = replyNode.x + replyCardW * 0.20f
        val checkY = replyNode.y - replyCardH * 0.20f
        drawDoubleCheck(Offset(checkX, checkY), greenWhatsApp)

        // Reply text lines inside
        drawLine(
            color = if (isDark) Color(0xFFECFDF5) else Color(0xFF065F46),
            start = Offset(replyNode.x - replyCardW * 0.35f, replyNode.y - replyCardH * 0.05f),
            end = Offset(replyNode.x + replyCardW * 0.10f, replyNode.y - replyCardH * 0.05f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF047857),
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
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val transition = rememberInfiniteTransition(label = "KnowledgeSyncTransition")

    // Expanding sonar / radar speed rings from the on-device chip
    val sonarPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SonarPhase"
    )

    // Data packets streaming into chip
    val syncPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncPhase"
    )

    // Gentle floating motion of satellite document cards
    val floatY by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatY"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val centerX = w * 0.50f
        val centerY = h * 0.50f

        val nodeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        val nodeBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        val tealPrimary = PersonaPrimitives.Teal500
        val amberAccent = Color(0xFFF59E0B)
        val indigoAccent = Color(0xFF6366F1)
        val emeraldAccent = Color(0xFF10B981)

        // 1. Radar / Sonar Speed Waves radiating from on-device brain
        val maxSonarRadius = w * 0.42f
        for (i in 0..2) {
            val adjustedPhase = (sonarPhase + i * 0.33f) % 1f
            val radius = maxSonarRadius * adjustedPhase
            val alpha = (1f - adjustedPhase).coerceIn(0f, 0.7f)
            drawCircle(
                color = tealPrimary.copy(alpha = alpha * 0.4f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )
        }

        // 2. Surrounding Knowledge Node positions
        val doc1Pos = Offset(w * 0.18f, h * 0.22f + floatY)
        val doc2Pos = Offset(w * 0.82f, h * 0.22f - floatY)
        val doc3Pos = Offset(w * 0.18f, h * 0.78f - floatY)
        val doc4Pos = Offset(w * 0.82f, h * 0.78f + floatY)

        // Connecting sync streams to central chip
        drawSyncStream(doc1Pos, Offset(centerX, centerY), syncPhase, 0f, amberAccent)
        drawSyncStream(doc2Pos, Offset(centerX, centerY), syncPhase, 0.25f, indigoAccent)
        drawSyncStream(doc3Pos, Offset(centerX, centerY), syncPhase, 0.5f, emeraldAccent)
        drawSyncStream(doc4Pos, Offset(centerX, centerY), syncPhase, 0.75f, tealPrimary)

        // 3. Central On-Device Neural Chip
        val chipSize = w * 0.22f
        val chipRect = Rect(centerX - chipSize / 2f, centerY - chipSize / 2f, centerX + chipSize / 2f, centerY + chipSize / 2f)

        // Chip Pins (4 on each side)
        val pinLength = 12f
        val pinColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        for (step in 1..3) {
            val offsetAlong = chipSize * (step / 4f)
            // Top pins
            drawLine(pinColor, Offset(chipRect.left + offsetAlong, chipRect.top), Offset(chipRect.left + offsetAlong, chipRect.top - pinLength), 3f)
            // Bottom pins
            drawLine(pinColor, Offset(chipRect.left + offsetAlong, chipRect.bottom), Offset(chipRect.left + offsetAlong, chipRect.bottom + pinLength), 3f)
            // Left pins
            drawLine(pinColor, Offset(chipRect.left, chipRect.top + offsetAlong), Offset(chipRect.left - pinLength, chipRect.top + offsetAlong), 3f)
            // Right pins
            drawLine(pinColor, Offset(chipRect.right, chipRect.top + offsetAlong), Offset(chipRect.right + pinLength, chipRect.top + offsetAlong), 3f)
        }

        // Chip Body
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0F172A), Color(0xFF0D5C54)),
                start = chipRect.topLeft,
                end = chipRect.bottomRight
            ),
            topLeft = chipRect.topLeft,
            size = chipRect.size,
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = tealPrimary,
            topLeft = chipRect.topLeft,
            size = chipRect.size,
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3f)
        )

        // Neural circuits inside chip
        drawChipCircuit(chipRect, tealPrimary)

        // Lightning Bolt / Speed Icon in chip center
        drawLightningBolt(Offset(centerX, centerY), chipSize * 0.25f, amberAccent)

        // 4. Floating Knowledge Document Cards
        val cardW = w * 0.24f
        val cardH = h * 0.14f

        // Document 1: Products / Catalog (Amber)
        drawCardBadge(doc1Pos, cardW, cardH, nodeBg, amberAccent)
        drawDocLines(doc1Pos, cardW, cardH, amberAccent, isDark)

        // Document 2: Chat History (Indigo)
        drawCardBadge(doc2Pos, cardW, cardH, nodeBg, indigoAccent)
        drawDocLines(doc2Pos, cardW, cardH, indigoAccent, isDark)

        // Document 3: Shipping & Policies (Emerald)
        drawCardBadge(doc3Pos, cardW, cardH, nodeBg, emeraldAccent)
        drawDocLines(doc3Pos, cardW, cardH, emeraldAccent, isDark)

        // Document 4: Store Info & FAQs (Teal)
        drawCardBadge(doc4Pos, cardW, cardH, nodeBg, tealPrimary)
        drawDocLines(doc4Pos, cardW, cardH, tealPrimary, isDark)
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
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val transition = rememberInfiniteTransition(label = "AutomatedCrmTransition")

    // Scanning laser sweep across the raw customer inquiry
    val scanSweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanSweep"
    )

    // Checkmark success pulse
    val checkPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CheckPulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val nodeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        val nodeBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        val emeraldGreen = Color(0xFF10B981)
        val tealPrimary = PersonaPrimitives.Teal500
        val amberAccent = Color(0xFFF59E0B)
        val indigoAccent = Color(0xFF6366F1)

        // 1. Top Card: Incoming Unstructured Customer Message
        val inputCardW = w * 0.76f
        val inputCardH = h * 0.24f
        val inputCardCenter = Offset(w * 0.5f, h * 0.20f)
        drawCardBadge(inputCardCenter, inputCardW, inputCardH, nodeBg, nodeBorder)

        // Raw message representation (simulated text message lines)
        val lineStartX = inputCardCenter.x - inputCardW * 0.38f
        val lineEndX = inputCardCenter.x + inputCardW * 0.38f
        val line1Y = inputCardCenter.y - inputCardH * 0.22f
        val line2Y = inputCardCenter.y
        val line3Y = inputCardCenter.y + inputCardH * 0.22f

        drawLine(
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
            start = Offset(lineStartX, line1Y),
            end = Offset(lineEndX - 20f, line1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
            start = Offset(lineStartX, line2Y),
            end = Offset(lineEndX - 60f, line2Y),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = indigoAccent,
            start = Offset(lineStartX, line3Y),
            end = Offset(lineStartX + 90f, line3Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Scanning laser beam sweeping across the message card
        val scanX = (inputCardCenter.x - inputCardW / 2f) + (inputCardW * scanSweep)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, tealPrimary, Color.Transparent),
                startY = inputCardCenter.y - inputCardH / 2f,
                endY = inputCardCenter.y + inputCardH / 2f
            ),
            start = Offset(scanX, inputCardCenter.y - inputCardH / 2f),
            end = Offset(scanX, inputCardCenter.y + inputCardH / 2f),
            strokeWidth = 4f
        )

        // 2. Conversion Transformation Funnel / Pipeline Arrows
        val funnelTopY = inputCardCenter.y + inputCardH / 2f + 8f
        val funnelBottomY = h * 0.48f
        val arrowPath = Path().apply {
            moveTo(w * 0.5f, funnelTopY)
            lineTo(w * 0.5f, funnelBottomY)
        }
        drawPath(
            arrowPath,
            color = tealPrimary,
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), -scanSweep * 20f)
            )
        )
        // Arrow head
        val arrowHead = Path().apply {
            moveTo(w * 0.46f, funnelBottomY - 12f)
            lineTo(w * 0.5f, funnelBottomY)
            lineTo(w * 0.54f, funnelBottomY - 12f)
        }
        drawPath(arrowHead, color = tealPrimary, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 3. Bottom Card: Formatted Structured Order & CRM Record
        val orderCardW = w * 0.82f
        val orderCardH = h * 0.38f
        val orderCardCenter = Offset(w * 0.5f, h * 0.72f)
        val orderCardBg = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
        val orderCardBorder = if (isDark) PersonaPrimitives.Teal700 else PersonaPrimitives.Teal600
        drawCardBadge(orderCardCenter, orderCardW, orderCardH, orderCardBg, orderCardBorder)

        // Order Header Row: Customer Name Avatar Chip + Order ID
        val avatarCenter = Offset(orderCardCenter.x - orderCardW * 0.34f, orderCardCenter.y - orderCardH * 0.30f)
        drawCircle(color = tealPrimary.copy(alpha = 0.25f), radius = 18f, center = avatarCenter)
        drawCircle(color = tealPrimary, radius = 8f, center = avatarCenter)

        drawLine(
            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
            start = Offset(avatarCenter.x + 28f, avatarCenter.y),
            end = Offset(orderCardCenter.x + orderCardW * 0.05f, avatarCenter.y),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Status Pill: "مؤكد" (Confirmed)
        val statusPillRect = Rect(
            orderCardCenter.x + orderCardW * 0.14f,
            avatarCenter.y - 14f,
            orderCardCenter.x + orderCardW * 0.40f,
            avatarCenter.y + 14f
        )
        drawRoundRect(
            color = emeraldGreen.copy(alpha = 0.2f),
            topLeft = statusPillRect.topLeft,
            size = statusPillRect.size,
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = emeraldGreen,
            topLeft = statusPillRect.topLeft,
            size = statusPillRect.size,
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 2f)
        )

        // Order Item 1 Row: (2x Product + Price)
        val item1Y = orderCardCenter.y - orderCardH * 0.02f
        // Quantity pill (2x)
        drawRoundRect(
            color = amberAccent.copy(alpha = 0.2f),
            topLeft = Offset(orderCardCenter.x - orderCardW * 0.38f, item1Y - 11f),
            size = Size(36f, 22f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawLine(
            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
            start = Offset(orderCardCenter.x - orderCardW * 0.38f + 46f, item1Y),
            end = Offset(orderCardCenter.x + orderCardW * 0.12f, item1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = amberAccent,
            start = Offset(orderCardCenter.x + orderCardW * 0.22f, item1Y),
            end = Offset(orderCardCenter.x + orderCardW * 0.38f, item1Y),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Divider
        val dividerY = orderCardCenter.y + orderCardH * 0.14f
        drawLine(
            color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
            start = Offset(orderCardCenter.x - orderCardW * 0.38f, dividerY),
            end = Offset(orderCardCenter.x + orderCardW * 0.38f, dividerY),
            strokeWidth = 2f
        )

        // Bottom Row: Address pin + Big Animated Success Checkmark Badge
        val bottomRowY = orderCardCenter.y + orderCardH * 0.30f
        drawLine(
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            start = Offset(orderCardCenter.x - orderCardW * 0.38f, bottomRowY),
            end = Offset(orderCardCenter.x + orderCardW * 0.10f, bottomRowY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Animated Success Checkmark Badge
        val checkCenter = Offset(orderCardCenter.x + orderCardW * 0.30f, bottomRowY)
        val checkRadius = 18f * checkPulse
        // Glow ring
        drawCircle(
            color = emeraldGreen.copy(alpha = 0.3f),
            radius = checkRadius * 1.5f,
            center = checkCenter
        )
        // Solid green circle
        drawCircle(
            color = emeraldGreen,
            radius = checkRadius,
            center = checkCenter
        )
        // White checkmark inside
        val checkPath = Path().apply {
            moveTo(checkCenter.x - checkRadius * 0.45f, checkCenter.y)
            lineTo(checkCenter.x - checkRadius * 0.1f, checkCenter.y + checkRadius * 0.35f)
            lineTo(checkCenter.x + checkRadius * 0.45f, checkCenter.y - checkRadius * 0.35f)
        }
        drawPath(
            checkPath,
            color = Color.White,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ── Private Drawing Helpers ──────────────────────────────────────────────────────

private fun DrawScope.drawCardBadge(
    center: Offset,
    w: Float,
    h: Float,
    bgColor: Color,
    borderColor: Color
) {
    val rect = Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
    drawRoundRect(
        color = bgColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(20f, 20f)
    )
    drawRoundRect(
        color = borderColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(width = 2.5f)
    )
}

private fun DrawScope.drawNeuralWave(
    start: Offset,
    end: Offset,
    phase: Float,
    phaseOffset: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        val segments = 24
        val dx = (end.x - start.x) / segments
        val dy = (end.y - start.y) / segments
        for (i in 1..segments) {
            val currX = start.x + dx * i
            val currY = start.y + dy * i
            val wave = sin(phase + phaseOffset + i * 0.4f) * 12f * (1f - (i.toFloat() / segments))
            lineTo(currX, currY + wave)
        }
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
    start: Offset,
    end: Offset,
    phase: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        val midX = (start.x + end.x) / 2f
        quadraticTo(midX, start.y - 18f, end.x, end.y)
    }
    drawPath(
        path,
        color = color.copy(alpha = 0.55f),
        style = Stroke(
            width = 3f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), -phase * 15f)
        )
    )
}

private fun DrawScope.drawAiSpark(
    center: Offset,
    radius: Float,
    centerColor: Color,
    sparkColor: Color
) {
    val sparkPath = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(sparkPath, color = sparkColor)
    drawCircle(color = centerColor, radius = radius * 0.35f, center = center)
}

private fun DrawScope.drawDoubleCheck(
    topRight: Offset,
    color: Color
) {
    val check1 = Path().apply {
        moveTo(topRight.x - 14f, topRight.y)
        lineTo(topRight.x - 8f, topRight.y + 6f)
        lineTo(topRight.x, topRight.y - 4f)
    }
    val check2 = Path().apply {
        moveTo(topRight.x - 20f, topRight.y)
        lineTo(topRight.x - 14f, topRight.y + 6f)
        lineTo(topRight.x - 6f, topRight.y - 4f)
    }
    drawPath(check1, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(check2, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSyncStream(
    start: Offset,
    end: Offset,
    phase: Float,
    phaseOffset: Float,
    color: Color
) {
    val streamPath = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)
    }
    drawPath(
        streamPath,
        color = color.copy(alpha = 0.35f),
        style = Stroke(
            width = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -phase * 20f)
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
    center: Offset,
    size: Float,
    color: Color
) {
    val bolt = Path().apply {
        moveTo(center.x + size * 0.15f, center.y - size)
        lineTo(center.x - size * 0.6f, center.y)
        lineTo(center.x - size * 0.05f, center.y)
        lineTo(center.x - size * 0.25f, center.y + size)
        lineTo(center.x + size * 0.6f, center.y - size * 0.15f)
        lineTo(center.x + size * 0.05f, center.y - size * 0.15f)
        close()
    }
    drawPath(bolt, color = color)
}

private fun DrawScope.drawDocLines(
    center: Offset,
    w: Float,
    h: Float,
    accent: Color,
    isDark: Boolean
) {
    val lineX = center.x - w * 0.32f
    val endX = center.x + w * 0.32f
    val textColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Top title line in accent
    drawLine(accent, Offset(lineX, center.y - h * 0.22f), Offset(endX - 18f, center.y - h * 0.22f), 4f, StrokeCap.Round)
    // Body lines
    drawLine(textColor, Offset(lineX, center.y), Offset(endX, center.y), 3f, StrokeCap.Round)
    drawLine(textColor, Offset(lineX, center.y + h * 0.22f), Offset(endX - 30f, center.y + h * 0.22f), 3f, StrokeCap.Round)
}
