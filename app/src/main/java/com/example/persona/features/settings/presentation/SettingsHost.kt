package com.example.persona.features.settings.presentation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.persona.features.settings.presentation.components.LocalNavVisibilityScope
import com.example.persona.features.settings.presentation.components.LocalSharedTransitionScope
import com.example.persona.features.settings.presentation.components.LocalUndoHost
import com.example.persona.features.settings.presentation.components.UndoHost
import com.example.persona.features.settings.presentation.model.LocalMessageEditor
import com.example.persona.features.settings.presentation.model.MessageEditorLauncher
import com.example.persona.features.settings.presentation.model.MessageSlot
import com.example.persona.features.settings.presentation.screens.ai.AiEngineScreen
import com.example.persona.features.settings.presentation.screens.ai.ProviderDetailScreen
import com.example.persona.features.settings.presentation.screens.health.HealthDataScreen
import com.example.persona.features.settings.presentation.screens.hours.BlocklistScreen
import com.example.persona.features.settings.presentation.screens.hours.HoursPrivacyScreen
import com.example.persona.features.settings.presentation.screens.hub.SettingsHubScreen
import com.example.persona.features.settings.presentation.screens.messages.MessagesScreen
import com.example.persona.features.settings.presentation.screens.voice.VoiceBehaviorScreen
import com.example.persona.features.settings.presentation.screens.whatsapp.WhatsAppScreen
import com.example.persona.features.settings.presentation.sheets.MessageEditorSheet
import com.example.persona.ui.components.personaScreenBackground

private typealias Slide = AnimatedContentTransitionScope.SlideDirection
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private inline fun <reified T : Any> NavGraphBuilder.settingsDestination(
    noinline content: @Composable (NavBackStackEntry) -> Unit
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavVisibilityScope provides this) {
            content(entry)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsHost(
    onExit: () -> Unit = {},
    startSpoke: SettingsSpoke? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember(viewModel) { SettingsActions(viewModel) }
    val undo = remember(snackbar, scope) { UndoHost(snackbar, scope) }
    var editingSlot by rememberSaveable { mutableStateOf<MessageSlot?>(null) }

    // Re-read storage and notification permissions upon return to foreground
    LifecycleResumeEffect(Unit) {
        viewModel.refreshStoragePermission()
        onPauseOrDispose { }
    }

    // System maintenance feedback channel
    LaunchedEffect(state.maintenanceMessage) {
        state.maintenanceMessage?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearMaintenanceMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .personaScreenBackground()
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                    LocalUndoHost provides undo,
                    LocalMessageEditor provides MessageEditorLauncher { editingSlot = it }
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = startSpoke?.let { SettingsRoute.Spoke(it) }
                            ?: SettingsRoute.Hub,
                        enterTransition = {
                            slideIntoContainer(Slide.Start, tween(340, easing = EmphasizedDecelerate), initialOffset = { it / 6 }) +
                                fadeIn(tween(220, delayMillis = 60))
                        },
                        exitTransition = {
                            slideOutOfContainer(Slide.Start, tween(260, easing = EmphasizedAccelerate), targetOffset = { it / 10 }) +
                                fadeOut(tween(140))
                        },
                        popEnterTransition = {
                            slideIntoContainer(Slide.End, tween(340, easing = EmphasizedDecelerate), initialOffset = { it / 10 }) +
                                fadeIn(tween(220, delayMillis = 60))
                        },
                        popExitTransition = {
                            slideOutOfContainer(Slide.End, tween(260, easing = EmphasizedAccelerate), targetOffset = { it / 6 }) +
                                fadeOut(tween(140))
                        }
                    ) {
                        settingsDestination<SettingsRoute.Hub> {
                            SettingsHubScreen(
                                state = state,
                                actions = actions,
                                onOpen = { spoke, focus -> nav.navigate(SettingsRoute.Spoke(spoke, focus)) },
                                onOpenProvider = { id -> nav.navigate(SettingsRoute.Provider(id)) },
                                onExit = onExit
                            )
                        }

                        settingsDestination<SettingsRoute.Spoke> { entry ->
                            val route = entry.toRoute<SettingsRoute.Spoke>()
                            val back: () -> Unit = {
                                if (!nav.navigateUp()) onExit()
                            }
                            when (route.spoke) {
                                SettingsSpoke.Voice -> VoiceBehaviorScreen(
                                    state = state,
                                    actions = actions.voice,
                                    focus = route.focus,
                                    onBack = back
                                )
                                SettingsSpoke.Messages -> MessagesScreen(
                                    state = state,
                                    actions = actions.messages,
                                    focus = route.focus,
                                    onBack = back
                                )
                                SettingsSpoke.Hours -> HoursPrivacyScreen(
                                    state = state,
                                    actions = actions.hours,
                                    focus = route.focus,
                                    onBack = back,
                                    onOpenBlocklist = { nav.navigate(SettingsRoute.Blocklist) }
                                )
                                SettingsSpoke.WhatsApp -> WhatsAppScreen(
                                    state = state,
                                    actions = actions.whatsApp,
                                    focus = route.focus,
                                    onBack = back
                                )
                                SettingsSpoke.AiEngine -> AiEngineScreen(
                                    state = state,
                                    actions = actions.ai,
                                    focus = route.focus,
                                    onBack = back,
                                    onOpenProvider = { nav.navigate(SettingsRoute.Provider(it)) }
                                )
                                SettingsSpoke.Health -> HealthDataScreen(
                                    state = state,
                                    actions = actions.health,
                                    focus = route.focus,
                                    onBack = back
                                )
                            }
                        }

                        settingsDestination<SettingsRoute.Provider> { entry ->
                            val route = entry.toRoute<SettingsRoute.Provider>()
                            LaunchedEffect(route.id) {
                                actions.ai.selectProvider(route.id)
                            }
                            ProviderDetailScreen(
                                state = state,
                                providerId = route.id,
                                actions = actions.ai,
                                focus = route.focus,
                                onBack = { nav.navigateUp() }
                            )
                        }

                        settingsDestination<SettingsRoute.Blocklist> {
                            BlocklistScreen(
                                numbers = state.blacklistedNumbers,
                                actions = actions.hours,
                                onBack = { nav.navigateUp() }
                            )
                        }
                    }

                    // Shared single message editor across all spokes
                    editingSlot?.let { slot ->
                        MessageEditorSheet(
                            slot = slot,
                            initial = slot.read(state),
                            botName = state.botName,
                            onCommit = { slot.write(actions.messages, it) },
                            onDismiss = { editingSlot = null }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
