package com.example.persona.features.settings.presentation.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.SettingsActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.features.settings.presentation.model.buildHubCards
import com.example.persona.features.settings.presentation.model.nextBestActions
import com.example.persona.features.settings.presentation.screens.search.SettingsSearchOverlay
import com.example.persona.features.settings.presentation.sheets.AppPreferencesSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onOpen: (SettingsSpoke, SettingKey?) -> Unit,
    onOpenProvider: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    val cards = remember(state) { buildHubCards(state, resources) }
    val setup = remember(state) { nextBestActions(state) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searching = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.settings_search)
                        )
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (setup.isNotEmpty()) {
                    item(key = "setup", span = { GridItemSpan(maxLineSpan) }) {
                        SetupCopilotCard(
                            steps = setup,
                            onStepClick = { onOpen(it.spoke, it.focus) }
                        )
                    }
                }

                item(key = "quick", span = { GridItemSpan(maxLineSpan) }) {
                    QuickSwitchesRow(state = state, actions = actions)
                }

                items(cards, key = { it.spoke.name }) { card ->
                    HubBentoCard(
                        card = card,
                        onClick = { onOpen(card.spoke, null) }
                    )
                }

                item(key = "prefs", span = { GridItemSpan(maxLineSpan) }) {
                    SettingsGroup(title = null) {
                        NavRow(
                            title = stringResource(R.string.settings_app_preferences),
                            summary = stringResource(R.string.settings_language_theme_summary),
                            icon = Icons.Default.Tune,
                            onClick = { showPreferences = true }
                        )
                    }
                }
            }
        }
    }

    if (showPreferences) {
        AppPreferencesSheet(
            appLanguage = state.appLanguage,
            themeMode = state.themeMode,
            actions = actions.app,
            onDismiss = { showPreferences = false }
        )
    }

    if (searching) {
        SettingsSearchOverlay(
            onPick = { entry ->
                searching = false
                if (entry.provider != null) {
                    onOpenProvider(entry.provider)
                } else {
                    onOpen(entry.spoke, entry.key)
                }
            },
            onClose = { searching = false }
        )
    }
}
