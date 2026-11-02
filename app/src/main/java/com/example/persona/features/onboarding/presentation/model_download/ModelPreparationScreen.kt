package com.example.persona.features.onboarding.presentation.model_download

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.features.onboarding.domain.model_download.DownloadProgress
import com.example.persona.ui.components.personaScreenBackground
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModelPreparationScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelPreparationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Keep screen awake while downloading or verifying
    val isBusy = state is ModelPreparationUiState.Downloading ||
            state is ModelPreparationUiState.Verifying ||
            state is ModelPreparationUiState.CheckingStorage

    val isSuccess = state is ModelPreparationUiState.Success
    val isError = state is ModelPreparationUiState.Failed ||
            state is ModelPreparationUiState.InsufficientStorage ||
            state is ModelPreparationUiState.NetworkDisconnected

    val currentProgressFraction = (state as? ModelPreparationUiState.Downloading)?.progress?.progressFraction ?: if (isSuccess) 1f else 0f

    DisposableEffect(isBusy) {
        val window = (context as? Activity)?.window
        if (isBusy) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isBusy) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.model_prep_exit_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.model_prep_exit_dialog_desc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.model_prep_exit_dialog_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.model_prep_exit_dialog_dismiss))
                }
            }
        )
    }

    // Auto-navigate when complete
    LaunchedEffect(state) {
        if (state is ModelPreparationUiState.Success) {
            delay(1400) // Brief delay to show completion animation
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .personaScreenBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic High-Tech Canvas Animation
            ModelPreparationCanvasAnimation(
                progressFraction = currentProgressFraction,
                isBusy = isBusy,
                isSuccess = isSuccess,
                isError = isError,
                modifier = Modifier.size(190.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Header (Fixed typo: لمتجرك)
            Text(
                text = stringResource(R.string.model_prep_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = stringResource(R.string.model_prep_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Dynamic Status & Progress Card
            when (val s = state) {
                is ModelPreparationUiState.Idle,
                is ModelPreparationUiState.CheckingStorage -> {
                    CheckingStorageCard(isDark = isDark)
                }

                is ModelPreparationUiState.InsufficientStorage -> {
                    InsufficientStorageCard(
                        requiredMb = s.requiredMb,
                        availableMb = s.availableMb,
                        onOpenSettings = { viewModel.openStorageSettings(context) },
                        onRetry = { viewModel.retry() },
                        isDark = isDark
                    )
                }

                is ModelPreparationUiState.CellularConfirmationRequired -> {
                    CellularWarningCard(
                        totalMb = s.totalMb,
                        onProceed = { viewModel.proceedOnCellular() },
                        isDark = isDark
                    )
                }

                is ModelPreparationUiState.Downloading -> {
                    DownloadingCard(progress = s.progress, isDark = isDark)
                }

                is ModelPreparationUiState.Verifying -> {
                    VerifyingCard(isDark = isDark)
                }

                is ModelPreparationUiState.NetworkDisconnected -> {
                    DisconnectedCard(
                        onRetry = { viewModel.retry() },
                        isDark = isDark
                    )
                }

                is ModelPreparationUiState.Failed -> {
                    FailedCard(
                        message = s.errorMessage,
                        canRetry = s.canRetry,
                        onRetry = { viewModel.retry() },
                        isDark = isDark
                    )
                }

                is ModelPreparationUiState.Success -> {
                    SuccessCard(isDark = isDark)
                }
            }

            // Interactive Rotating Tips Card to entertain & educate during the 1-2 min download
            if (isBusy && !isSuccess) {
                Spacer(modifier = Modifier.height(24.dp))
                ModelPreparationRotatingTips(isDark = isDark)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 60fps Vector Canvas Animation for Model Download & Preparation.
 * Illustrates expanding sonar wave pulses, streaming data packet dots,
 * an orbital progress arc, and a glowing neural processor core.
 */
@Composable
fun ModelPreparationCanvasAnimation(
    progressFraction: Float,
    isBusy: Boolean,
    isSuccess: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val transition = rememberInfiniteTransition(label = "ModelPrepCanvasTransition")

    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val sonarPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SonarPhase"
    )

    val particlePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticlePhase"
    )

    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitAngle"
    )

    val tealPrimary = MaterialTheme.colorScheme.primary
    val emeraldSuccess = Color(0xFF10B981)
    val errorColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center = Offset(w / 2f, h / 2f)
        val maxRadius = minOf(w, h) / 2f

        // 1. Sonar expanding wave rings
        if (isBusy && !isSuccess && !isError) {
            val ringRadius = maxRadius * (0.45f + sonarPhase * 0.5f)
            val ringAlpha = (1f - sonarPhase) * 0.35f
            drawCircle(
                color = tealPrimary.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 2. Outer Progress Ring & Track
        val progressRadius = maxRadius * 0.82f
        val trackColor = if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
        drawCircle(
            color = trackColor,
            radius = progressRadius,
            center = center,
            style = Stroke(width = 5.dp.toPx())
        )

        val sweepAngle = if (isSuccess) 360f else (progressFraction * 360f).coerceIn(0f, 360f)
        if (sweepAngle > 0f) {
            val progressBrush = if (isSuccess) {
                Brush.sweepGradient(listOf(emeraldSuccess, Color(0xFF34D399), emeraldSuccess))
            } else {
                Brush.sweepGradient(
                    listOf(
                        tealPrimary,
                        Color(0xFF06B6D4),
                        Color(0xFF3B82F6),
                        tealPrimary
                    )
                )
            }
            drawArc(
                brush = progressBrush,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - progressRadius, center.y - progressRadius),
                size = Size(progressRadius * 2f, progressRadius * 2f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Leading Edge Glowing Indicator
            if (!isSuccess && sweepAngle in 2f..358f) {
                val headAngleRad = ((sweepAngle - 90f) * PI / 180f).toFloat()
                val headX = center.x + progressRadius * cos(headAngleRad)
                val headY = center.y + progressRadius * sin(headAngleRad)
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(headX, headY)
                )
                drawCircle(
                    color = tealPrimary.copy(alpha = 0.5f),
                    radius = 7.dp.toPx(),
                    center = Offset(headX, headY)
                )
            }
        }

        // 3. Inward Streaming Data Packets (Download visualization)
        if (isBusy && !isSuccess && !isError) {
            val particleCount = 6
            for (i in 0 until particleCount) {
                val baseAngle = (i * (360f / particleCount) + orbitAngle * 0.5f) * PI / 180f
                val distanceFraction = (1f - ((particlePhase + i * 0.16f) % 1f))
                val pRadius = maxRadius * (0.35f + distanceFraction * 0.44f)
                val px = center.x + pRadius * cos(baseAngle).toFloat()
                val py = center.y + pRadius * sin(baseAngle).toFloat()
                val pAlpha = (1f - distanceFraction) * 0.8f
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = pAlpha.coerceIn(0f, 1f)),
                    radius = 3.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // 4. Orbiting Synaptic Nodes
        val nodeCount = 4
        for (i in 0 until nodeCount) {
            val nodeAngle = (i * 90f + orbitAngle) * PI / 180f
            val nx = center.x + progressRadius * cos(nodeAngle).toFloat()
            val ny = center.y + progressRadius * sin(nodeAngle).toFloat()
            val nodeColor = when {
                isSuccess -> emeraldSuccess
                isError -> errorColor
                else -> Color(0xFF0EA5E9)
            }
            drawCircle(
                color = if (isDark) Color(0xFF0F172A) else Color.White,
                radius = 5.dp.toPx(),
                center = Offset(nx, ny)
            )
            drawCircle(
                color = nodeColor,
                radius = 3.5.dp.toPx(),
                center = Offset(nx, ny)
            )
        }

        // 5. Central AI Nexus / Core
        val coreRadius = maxRadius * 0.36f * (if (isBusy) pulseScale else 1f)
        val coreColor = when {
            isSuccess -> emeraldSuccess
            isError -> errorColor
            else -> tealPrimary
        }

        // Outer Core Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = 0.25f), coreColor.copy(alpha = 0.02f)),
                center = center,
                radius = coreRadius * 1.5f
            ),
            radius = coreRadius * 1.5f,
            center = center
        )

        // Core Base Surface
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0xFF1E293B) else Color.White,
                    if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                ),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )

        // Core Border
        drawCircle(
            color = coreColor.copy(alpha = 0.6f),
            radius = coreRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Central Icon / Circuits
        if (isSuccess) {
            // Radiant Checkmark
            val checkPath = Path().apply {
                moveTo(center.x - coreRadius * 0.45f, center.y - coreRadius * 0.05f)
                lineTo(center.x - coreRadius * 0.12f, center.y + coreRadius * 0.32f)
                lineTo(center.x + coreRadius * 0.48f, center.y - coreRadius * 0.35f)
            }
            drawPath(
                path = checkPath,
                color = emeraldSuccess,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else if (isError) {
            // Exclamation / Warning
            drawLine(
                color = errorColor,
                start = Offset(center.x, center.y - coreRadius * 0.4f),
                end = Offset(center.x, center.y + coreRadius * 0.1f),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = errorColor,
                radius = 2.5.dp.toPx(),
                center = Offset(center.x, center.y + coreRadius * 0.32f)
            )
        } else {
            // Neural Microchip Processor Circuits
            val chipSize = coreRadius * 0.72f
            val half = chipSize / 2f
            val chipRect = Rect(center.x - half, center.y - half, center.x + half, center.y + half)

            // Chip square
            drawRoundRect(
                color = tealPrimary.copy(alpha = 0.18f),
                topLeft = Offset(chipRect.left, chipRect.top),
                size = Size(chipSize, chipSize),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = tealPrimary,
                topLeft = Offset(chipRect.left, chipRect.top),
                size = Size(chipSize, chipSize),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner Core Dot
            drawCircle(
                color = tealPrimary,
                radius = 3.5.dp.toPx(),
                center = center
            )

            // Radiating Microchip Pins (3 pins per side)
            val pinLen = 5.dp.toPx()
            val offsets = listOf(-chipSize * 0.25f, 0f, chipSize * 0.25f)
            offsets.forEach { off ->
                // Top pins
                drawLine(
                    color = tealPrimary,
                    start = Offset(center.x + off, chipRect.top),
                    end = Offset(center.x + off, chipRect.top - pinLen),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Bottom pins
                drawLine(
                    color = tealPrimary,
                    start = Offset(center.x + off, chipRect.bottom),
                    end = Offset(center.x + off, chipRect.bottom + pinLen),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Left pins
                drawLine(
                    color = tealPrimary,
                    start = Offset(chipRect.left, center.y + off),
                    end = Offset(chipRect.left - pinLen, center.y + off),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Right pins
                drawLine(
                    color = tealPrimary,
                    start = Offset(chipRect.right, center.y + off),
                    end = Offset(chipRect.right + pinLen, center.y + off),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private data class PreparationTip(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int
)

/**
 * Interactive rotating tips card that smoothly cycles value propositions every 4.5 seconds.
 */
@Composable
fun ModelPreparationRotatingTips(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val tips = remember {
        listOf(
            PreparationTip(Icons.Default.Bolt, R.string.model_prep_tip1_title, R.string.model_prep_tip1_desc),
            PreparationTip(Icons.Default.Security, R.string.model_prep_tip2_title, R.string.model_prep_tip2_desc),
            PreparationTip(Icons.Default.WifiOff, R.string.model_prep_tip3_title, R.string.model_prep_tip3_desc),
            PreparationTip(Icons.Default.AutoAwesome, R.string.model_prep_tip4_title, R.string.model_prep_tip4_desc)
        )
    }

    var currentTipIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500L)
            currentTipIndex = (currentTipIndex + 1) % tips.size
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        },
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Sparkle + "هل تعلم؟"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.model_prep_tip_header),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Tip Content
            AnimatedContent(
                targetState = currentTipIndex,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(350))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(250)))
                },
                label = "RotatingTipTransition"
            ) { index ->
                val tip = tips[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = tip.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(tip.titleRes),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(tip.descRes),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tips.indices.forEach { i ->
                    val isSelected = i == currentTipIndex
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "TipDotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                }
                            )
                            .clickable { currentTipIndex = i }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingCard(progress: DownloadProgress, isDark: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressFraction,
        animationSpec = tween(durationMillis = 250),
        label = "DownloadProgressBar"
    )

    val downloadedMb = progress.downloadedBytes.toDouble() / (1024 * 1024)
    val totalMb = progress.totalBytes.toDouble() / (1024 * 1024)
    val speedMb = progress.speedBytesPerSec.toDouble() / (1024 * 1024)
    val percent = (animatedProgress * 100).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
            },
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
        },
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.model_prep_downloading_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, stringResource(R.string.model_prep_mb_format), downloadedMb, totalMb),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val rateText = remember(progress.speedBytesPerSec, progress.etaSeconds) {
                    if (progress.speedBytesPerSec > 50_000L) {
                        val eta = when {
                            progress.etaSeconds in 1..59 -> "باقي ${progress.etaSeconds} ثانية"
                            progress.etaSeconds in 60..119 -> "باقي دقيقة تقريباً"
                            progress.etaSeconds >= 120 -> "باقي ${progress.etaSeconds / 60} دقائق تقريباً"
                            else -> ""
                        }
                        if (eta.isNotBlank()) {
                            String.format(Locale.US, "%.1f ميجا/ث • %s", speedMb, eta)
                        } else {
                            String.format(Locale.US, "%.1f ميجا/ث", speedMb)
                        }
                    } else if (progress.downloadedBytes > 0) {
                        "جاري القياس..."
                    } else {
                        ""
                    }
                }

                if (rateText.isNotBlank()) {
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckingStorageCard(isDark: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.model_prep_checking_storage),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InsufficientStorageCard(
    requiredMb: Long,
    availableMb: Long,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.model_prep_storage_error_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.model_prep_storage_error_desc, requiredMb, availableMb),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.model_prep_btn_open_storage), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.model_prep_btn_recheck), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CellularWarningCard(
    totalMb: Long,
    onProceed: () -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.model_prep_cellular_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.model_prep_cellular_desc, totalMb),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.model_prep_btn_proceed_cellular), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VerifyingCard(isDark: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.model_prep_verifying_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.model_prep_verifying_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DisconnectedCard(
    onRetry: () -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SignalCellularConnectedNoInternet0Bar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.model_prep_disconnected_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.model_prep_disconnected_desc),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.model_prep_btn_retry), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FailedCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.model_prep_failed_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (canRetry) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.model_prep_btn_retry), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SuccessCard(isDark: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF10B981).copy(alpha = if (isDark) 0.25f else 0.15f),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.model_prep_success_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.model_prep_success_desc),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
