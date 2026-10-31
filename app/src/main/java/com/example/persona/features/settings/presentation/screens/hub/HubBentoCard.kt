package com.example.persona.features.settings.presentation.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.features.settings.presentation.components.containerTransform
import com.example.persona.features.settings.presentation.model.Attention
import com.example.persona.features.settings.presentation.model.HubCard

@Composable
fun HubBentoCard(
    card: HubCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (card.attention) {
        Attention.None -> scheme.surfaceContainerHigh
        Attention.Warning -> scheme.tertiaryContainer
        Attention.Critical -> scheme.errorContainer
    }
    val contentColor = when (card.attention) {
        Attention.None -> scheme.onSurface
        Attention.Warning -> scheme.onTertiaryContainer
        Attention.Critical -> scheme.onErrorContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = modifier
            .heightIn(min = 120.dp)
            .containerTransform("card-${card.spoke.name}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column {
                Text(
                    text = stringResource(card.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = card.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
