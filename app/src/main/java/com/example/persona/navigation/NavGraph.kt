package com.example.persona.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.persona.features.auto_reply.presentation.audit.AuditLogScreen
import com.example.persona.features.chat_import.presentation.ChatImportProgressScreen
import com.example.persona.features.chat_import.presentation.ChatImportScreen
import com.example.persona.features.chat_import.presentation.ChatImportViewModel
import com.example.persona.features.chats.presentation.detail.ChatDetailScreen
import com.example.persona.features.chats.presentation.list.ChatsListScreen
import com.example.persona.features.home.presentation.HomeScreen
import com.example.persona.features.knowledge.presentation.KnowledgeManagementScreen
import com.example.persona.features.onboarding.presentation.PermissionOnboardingScreen
import com.example.persona.features.orders.presentation.OrdersDashboardScreen
import com.example.persona.features.settings.presentation.SettingsScreen

@Composable
fun PersonaNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = modifier
    ) {
        // ── Bottom nav destinations ───────────────────────────────────────────
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToImport = {
                    navController.navigate(Screen.ChatImport)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToAuditLog = {
                    navController.navigate(Screen.AuditLog)
                },
                onNavigateToChat = { contactName, sourceClass ->
                    navController.navigate(Screen.ChatDetail(contactName, sourceClass))
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
            SettingsScreen()
        }

        // ── Full-screen destinations (no bottom bar) ──────────────────────────
        composable<Screen.ChatDetail> {
            ChatDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Onboarding> {
            PlaceholderScreen(name = "Onboarding")
        }

        composable<Screen.ChatImport> { backStackEntry ->
            val viewModel: ChatImportViewModel = hiltViewModel(backStackEntry)
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
                try {
                    navController.getBackStackEntry<Screen.ChatImport>()
                } catch (e: Exception) {
                    null
                }
            }
            val viewModel: ChatImportViewModel = if (parentEntry != null) {
                hiltViewModel(parentEntry)
            } else {
                hiltViewModel()
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

        composable<Screen.PermissionSetup> {
            PermissionOnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.PermissionSetup) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
