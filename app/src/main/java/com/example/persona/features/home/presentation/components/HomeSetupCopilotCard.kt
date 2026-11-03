package com.example.persona.features.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.home.presentation.SetupCopilotStep

/**
 * Dashboard-level Setup Copilot card guiding the merchant through initial configuration.
 *
 * Shown when [step] is non-null (permissions are granted but API key or identity is unconfigured).
 * Tapping the action button navigates the user to Settings to complete that step.
 */
@Composable
fun SetupCopilotCard(
    step: SetupCopilotStep,
    onConfigureApiKey: () -> Unit,
    onCustomizePersona: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (step) {
        SetupCopilotStep.NEEDS_API_KEY -> stringResource(R.string.setup_copilot_api_title)
        SetupCopilotStep.NEEDS_IDENTITY -> stringResource(R.string.setup_copilot_identity_title)
    }
    val stepLabel = when (step) {
        SetupCopilotStep.NEEDS_API_KEY -> stringResource(R.string.setup_copilot_api_step)
        SetupCopilotStep.NEEDS_IDENTITY -> stringResource(R.string.setup_copilot_identity_step)
    }
    val subtitle = when (step) {
        SetupCopilotStep.NEEDS_API_KEY -> stringResource(R.string.setup_copilot_api_subtitle)
        SetupCopilotStep.NEEDS_IDENTITY -> stringResource(R.string.setup_copilot_identity_subtitle)
    }
    val btnLabel = when (step) {
        SetupCopilotStep.NEEDS_API_KEY -> stringResource(R.string.setup_copilot_api_btn)
        SetupCopilotStep.NEEDS_IDENTITY -> stringResource(R.string.setup_copilot_identity_btn)
    }
    val action = when (step) {
        SetupCopilotStep.NEEDS_API_KEY -> onConfigureApiKey
        SetupCopilotStep.NEEDS_IDENTITY -> onCustomizePersona
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stepLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Button(
                onClick = action,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(btnLabel, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

