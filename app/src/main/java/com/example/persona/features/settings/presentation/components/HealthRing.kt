package com.example.persona.features.settings.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.ui.theme.PersonaTheme

@Composable
fun HealthRing(
    passedChecks: Int,
    totalChecks: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (totalChecks > 0) passedChecks.toFloat() / totalChecks else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "health_progress")
    val allPassed = passedChecks == totalChecks && totalChecks > 0
    val color = if (allPassed)
        PersonaTheme.statusColors.success
    else if (passedChecks > 0)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 8.dp,
                    modifier = Modifier.size(72.dp)
                )
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    color = color,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "$passedChecks/$totalChecks",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allPassed)
                        stringResource(R.string.settings_health_ring_all_passed_title)
                    else
                        stringResource(R.string.settings_health_ring_attention_needed_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
                Text(
                    text = if (allPassed)
                        stringResource(R.string.settings_health_ring_all_passed_desc)
                    else
                        stringResource(R.string.settings_health_ring_attention_needed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
