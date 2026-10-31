package com.example.persona.features.settings.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.persona.R
import kotlinx.coroutines.delay

@Composable
fun DraftSaveTextField(
    value: String,
    onSave: (String) -> Unit,
    label: String,
    fallbackDefault: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    var showSaved by remember { mutableStateOf(false) }
    val isDirty = draft.trim() != value.trim()

    val handleSave = {
        val trimmed = draft.trim()
        val toSave = trimmed.ifEmpty { fallbackDefault }
        draft = toSave
        onSave(toSave)
        showSaved = true
    }

    LaunchedEffect(showSaved) {
        if (showSaved) {
            delay(1500)
            showSaved = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { handleSave() }),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    SavedIndicator(visible = showSaved)
                    if (isDirty) {
                        IconButton(onClick = handleSave) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_save),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = isDirty) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = handleSave,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}
