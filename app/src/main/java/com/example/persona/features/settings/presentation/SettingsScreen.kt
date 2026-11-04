package com.example.persona.features.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
    startSpoke: SettingsSpoke? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    SettingsHost(
        onExit = onExit,
        startSpoke = startSpoke,
        viewModel = viewModel,
        modifier = modifier
    )
}
