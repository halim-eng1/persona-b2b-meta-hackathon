package com.example.persona.features.settings.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import java.util.Locale

private enum class HoursField { Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkingHoursSheet(
    start: String,
    end: String,
    onConfirm: (start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (sh, sm) = remember(start) { parseHm(start.ifBlank { "09:00" }) }
    val (eh, em) = remember(end) { parseHm(end.ifBlank { "22:00" }) }
    val startState = rememberTimePickerState(sh, sm, is24Hour = true)
    val endState = rememberTimePickerState(eh, em, is24Hour = true)
    var editing by rememberSaveable { mutableStateOf(HoursField.Start) }

    fun fmt(s: TimePickerState) = String.format(Locale.ROOT, "%02d:%02d", s.hour, s.minute)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_hours_title),
                style = MaterialTheme.typography.titleLarge
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HoursField.entries.forEachIndexed { i, field ->
                    val isStart = field == HoursField.Start
                    SegmentedButton(
                        selected = editing == field,
                        onClick = { editing = field },
                        shape = SegmentedButtonDefaults.itemShape(i, HoursField.entries.size),
                        label = {
                            val labelRes = if (isStart) R.string.settings_hours_start_label else R.string.settings_hours_end_label
                            Text("${stringResource(labelRes)}: ${fmt(if (isStart) startState else endState)}")
                        }
                    )
                }
            }

            TimePicker(state = if (editing == HoursField.Start) startState else endState)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("09:00" to "17:00", "09:00" to "22:00", "10:00" to "20:00").forEach { (a, b) ->
                    AssistChip(
                        onClick = {
                            applyRange(startState, endState, a, b)
                        },
                        label = { Text("$a – $b") }
                    )
                }
            }

            Button(
                onClick = {
                    onConfirm(fmt(startState), fmt(endState))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

private fun parseHm(t: String): Pair<Int, Int> {
    val parts = t.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 9) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun applyRange(s: TimePickerState, e: TimePickerState, a: String, b: String) {
    parseHm(a).let { (h, m) ->
        s.hour = h
        s.minute = m
    }
    parseHm(b).let { (h, m) ->
        e.hour = h
        e.minute = m
    }
}
