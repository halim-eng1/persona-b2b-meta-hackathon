package com.example.persona.features.settings.presentation.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R

@Immutable
data class DayOption(
    val code: String,
    @StringRes val nameRes: Int,
    @StringRes val shortRes: Int
)

val ALL_DAYS_DIAL = listOf(
    DayOption("SATURDAY", R.string.settings_hours_sat, R.string.day_sat_short),
    DayOption("SUNDAY", R.string.settings_hours_sun, R.string.day_sun_short),
    DayOption("MONDAY", R.string.settings_hours_mon, R.string.day_mon_short),
    DayOption("TUESDAY", R.string.settings_hours_tue, R.string.day_tue_short),
    DayOption("WEDNESDAY", R.string.settings_hours_wed, R.string.day_wed_short),
    DayOption("THURSDAY", R.string.settings_hours_thu, R.string.day_thu_short),
    DayOption("FRIDAY", R.string.settings_hours_fri, R.string.day_fri_short)
)

@Composable
fun DayDial(
    selectedDays: Set<String>,
    onDaysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    days: List<DayOption> = ALL_DAYS_DIAL
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val isSelected = day.code in selectedDays
            val containerColor by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "day_color_${day.code}"
            )
            val textColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
            val fullName = stringResource(day.nameRes)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { checked ->
                            val updated = if (checked) selectedDays + day.code else selectedDays - day.code
                            onDaysChange(updated)
                        }
                    )
                    .semantics { contentDescription = fullName },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(day.shortRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor
                )
            }
        }
    }
}
