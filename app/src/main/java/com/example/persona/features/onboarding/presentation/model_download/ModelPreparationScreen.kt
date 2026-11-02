package com.example.persona.features.onboarding.presentation.model_download

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import com.example.persona.features.onboarding.domain.model_download.DownloadProgress
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ModelPreparationScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelPreparationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Keep screen awake while downloading or verifying
    val isBusy = state is ModelPreparationUiState.Downloading ||
            state is ModelPreparationUiState.Verifying ||
            state is ModelPreparationUiState.CheckingStorage

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
                    text = "هل تريد إيقاف تجهيز المساعد؟",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("المساعد يحتاج إلى إتمام التجهيز ليعمل بكفاءة وبدون إنترنت. يمكنك استئناف التحميل في أي وقت لاحقاً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("خروج من التطبيق")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("متابعة التجهيز")
                }
            }
        )
    }

    // Auto-navigate when complete
    LaunchedEffect(state) {
        if (state is ModelPreparationUiState.Success) {
            delay(1200) // Brief delay to show completion animation
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Pulsing Brain/Assistant Icon
            PulsingAssistantIcon(isBusy = isBusy, isSuccess = state is ModelPreparationUiState.Success)

            Spacer(modifier = Modifier.height(28.dp))

            // Main Header
            Text(
                text = "تجهيز المساعد الذكي لمتاجرك",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "نقوم بتحميل وتجهيز محرك الذكاء الاصطناعي على جهازك لمرة واحدة فقط ليعمل بدون استهلاك باقة الإنترنت.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Dynamic State Rendering
            when (val s = state) {
                is ModelPreparationUiState.Idle,
                is ModelPreparationUiState.CheckingStorage -> {
                    CheckingStorageCard()
                }

                is ModelPreparationUiState.InsufficientStorage -> {
                    InsufficientStorageCard(
                        requiredMb = s.requiredMb,
                        availableMb = s.availableMb,
                        onOpenSettings = { viewModel.openStorageSettings(context) },
                        onRetry = { viewModel.retry() }
                    )
                }

                is ModelPreparationUiState.CellularConfirmationRequired -> {
                    CellularWarningCard(
                        totalMb = s.totalMb,
                        onProceed = { viewModel.proceedOnCellular() }
                    )
                }

                is ModelPreparationUiState.Downloading -> {
                    DownloadingCard(progress = s.progress)
                }

                is ModelPreparationUiState.Verifying -> {
                    VerifyingCard(statusText = s.statusText)
                }

                is ModelPreparationUiState.NetworkDisconnected -> {
                    DisconnectedCard(
                        onRetry = { viewModel.retry() }
                    )
                }

                is ModelPreparationUiState.Failed -> {
                    FailedCard(
                        message = s.errorMessage,
                        canRetry = s.canRetry,
                        onRetry = { viewModel.retry() }
                    )
                }

                is ModelPreparationUiState.Success -> {
                    SuccessCard()
                }
            }
        }
    }
}

@Composable
private fun PulsingAssistantIcon(isBusy: Boolean, isSuccess: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val scaleModifier = if (isBusy) Modifier.scale(pulseScale) else Modifier

    val iconColor = when {
        isSuccess -> Color(0xFF10B981) // Green
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = scaleModifier
            .size(96.dp)
            .clip(CircleShape)
            .background(iconColor.copy(alpha = 0.12f))
            .border(2.dp, iconColor.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Memory,
            contentDescription = "Assistant Engine",
            modifier = Modifier.size(52.dp),
            tint = iconColor
        )
    }
}

@Composable
private fun CheckingStorageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "جاري التحقق من مساحة الهاتف والاتصال...",
                style = MaterialTheme.typography.bodyMedium,
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
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
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
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "مساحة الهاتف غير كافية",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "يتطلب تجهيز محرك الذكاء الاصطناعي $requiredMb ميجابايت على الأقل. المساحة الحالية المتاحة: $availableMb ميجابايت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("فتح إعدادات الذاكرة")
                }
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("تحقق مجدداً")
                }
            }
        }
    }
}

@Composable
private fun CellularWarningCard(
    totalMb: Long,
    onProceed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "تحميل عبر باقة الهاتف",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "أنت متصل حالياً عبر باقة الموبايل. حجم التحميل حوالي $totalMb ميجابايت، ويتم لمرة واحدة فقط ليعمل المساعد لاحقاً بدون إنترنت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("متابعة التحميل عبر الباقة")
            }
        }
    }
}

@Composable
private fun DownloadingCard(progress: DownloadProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressFraction,
        animationSpec = tween(durationMillis = 200),
        label = "DownloadProgressBar"
    )

    val downloadedMb = progress.downloadedBytes.toDouble() / (1024 * 1024)
    val totalMb = progress.totalBytes.toDouble() / (1024 * 1024)
    val speedMb = progress.speedBytesPerSec.toDouble() / (1024 * 1024)
    val percent = (animatedProgress * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
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
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جاري التحميل والتجهيز...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f / %.1f ميجابايت", downloadedMb, totalMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val rateText = if (progress.speedBytesPerSec > 50_000L) {
                    val etaText = when {
                        progress.etaSeconds in 1..59 -> "باقي ${progress.etaSeconds} ثانية"
                        progress.etaSeconds in 60..119 -> "باقي دقيقة تقريباً"
                        progress.etaSeconds >= 120 -> "باقي ${progress.etaSeconds / 60} دقائق تقريباً"
                        else -> ""
                    }
                    if (etaText.isNotBlank()) {
                        String.format(Locale.US, "%.1f ميجا/ث • %s", speedMb, etaText)
                    } else {
                        String.format(Locale.US, "%.1f ميجا/ث", speedMb)
                    }
                } else if (progress.downloadedBytes > 0) {
                    "جاري القياس..."
                } else {
                    ""
                }

                if (rateText.isNotBlank()) {
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifyingCard(statusText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                    text = "فحص وتأكيد الملفات",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DisconnectedCard(
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
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
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "انقطع الاتصال بالإنترنت",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "تم تجميد التحميل لحين عودة الشبكة. سيتم استئناف التحميل من النقطة الحالية دون إعادة تحميل الملفات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
private fun FailedCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
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
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "تعذر إكمال التجهيز",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (canRetry) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إعادة المحاولة")
                }
            }
        }
    }
}

@Composable
private fun SuccessCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f))
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
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "تم التجهيز بنجاح!",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "المساعد الذكي جاهز للعمل 100% بدون إنترنت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
