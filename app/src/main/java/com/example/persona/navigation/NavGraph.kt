package com.example.persona.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.persona.features.auto_reply.presentation.audit.AuditLogScreen
import com.example.persona.features.chat_import.presentation.ChatImportProgressScreen
import com.example.persona.features.chat_import.presentation.ChatImportScreen
import com.example.persona.features.chat_import.presentation.ChatImportViewModel
import com.example.persona.features.chats.presentation.detail.ChatDetailScreen
import com.example.persona.features.chats.presentation.list.ChatsListScreen
import com.example.persona.features.contacts.presentation.ContactDirectoryScreen
import com.example.persona.features.home.presentation.HomeScreen
import com.example.persona.features.knowledge.presentation.KnowledgeManagementScreen
import com.example.persona.features.onboarding.presentation.PermissionOnboardingScreen
import com.example.persona.features.onboarding.presentation.model_download.ModelPreparationScreen
import com.example.persona.features.orders.presentation.OrdersDashboardScreen
import com.example.persona.features.settings.presentation.SettingsScreen

@Composable
fun PersonaNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Screen = Screen.Home
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // ── Bottom nav destinations ───────────────────────────────────────────
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToImport = {
                    navController.navigate(Screen.ChatImportGraph)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToAuditLog = {
                    navController.navigate(Screen.AuditLog)
                },
                onNavigateToChat = { contactName, sourceClass ->
                    navController.navigate(Screen.ChatDetail(contactName, sourceClass))
                },
                onNavigateToOrders = {
                    navController.navigate(Screen.Orders)
                },
                onNavigateToKnowledge = {
                    navController.navigate(Screen.KnowledgeManagement)
                },
                onNavigateToContacts = {
                    navController.navigate(Screen.Contacts)
                }
            )
        }

        composable<Screen.Chats> {
            ChatsListScreen(
                onNavigateToChat = { contactName, sourceClass ->
                    navController.navigate(Screen.ChatDetail(contactName, sourceClass))
                }
            )
        }

        composable<Screen.Orders> {
            OrdersDashboardScreen(
                onNavigateToChat = { contactName, sourceClass ->
                    navController.navigate(Screen.ChatDetail(contactName, sourceClass))
                }
            )
        }

        composable<Screen.KnowledgeManagement> {
            KnowledgeManagementScreen()
        }

        composable<Screen.AuditLog> {
            AuditLogScreen()
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onExit = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home) {
                            popUpTo(Screen.Home) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        // ── CRM Contacts Directory ────────────────────────────────────────────
        composable<Screen.Contacts> {
            ContactDirectoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { contact, source ->
                    navController.navigate(Screen.ChatDetail(contact, source))
                }
            )
        }

        // ── Full-screen destinations (no bottom bar) ──────────────────────────
        composable<Screen.ChatDetail> {
            ChatDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Mandatory Permission / Onboarding Flow ───────────────────────────
        composable<Screen.ModelPreparation> {
            ModelPreparationScreen(
                onComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.ModelPreparation) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Onboarding> {
            PermissionOnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.PermissionSetup> {
            PermissionOnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.PermissionSetup) { inclusive = true }
                    }
                }
            )
        }

        // ── Nested Chat Import Flow ───────────────────────────────────────────
        navigation<Screen.ChatImportGraph>(startDestination = Screen.ChatImport) {
            composable<Screen.ChatImport> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    runCatching { navController.getBackStackEntry<Screen.ChatImportGraph>() }.getOrNull()
                }
                val viewModel: ChatImportViewModel = if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel(backStackEntry)
                }
                ChatImportScreen(
                    viewModel = viewModel,
                    onImportStarted = {
                        navController.navigate(Screen.ChatImportProgress(""))
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings)
                    }
                )
            }

            composable<Screen.ChatImportProgress> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    runCatching { navController.getBackStackEntry<Screen.ChatImportGraph>() }.getOrNull()
                        ?: runCatching { navController.getBackStackEntry<Screen.ChatImport>() }.getOrNull()
                }
                val viewModel: ChatImportViewModel = if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel(backStackEntry)
                }
                ChatImportProgressScreen(
                    viewModel = viewModel,
                    onComplete = {
                        navController.navigate(Screen.Home) {
                            popUpTo(Screen.Home) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
