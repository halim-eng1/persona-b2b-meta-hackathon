package com.example.persona.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

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
        composable<Screen.Home> {
            PlaceholderScreen(name = "Home Screen")
        }
        composable<Screen.Contacts> {
            PlaceholderScreen(name = "Contacts Screen")
        }
        composable<Screen.AuditLog> {
            PlaceholderScreen(name = "Audit Log Screen")
        }
        composable<Screen.Settings> {
            PlaceholderScreen(name = "Settings Screen")
        }
        composable<Screen.Onboarding> {
            PlaceholderScreen(name = "Onboarding Screen")
        }
        composable<Screen.ChatImport> {
            PlaceholderScreen(name = "Chat Import Screen")
        }
        composable<Screen.ChatImportProgress> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ChatImportProgress>()
            PlaceholderScreen(name = "Chat Import Progress for: ${args.contactName}")
        }
        composable<Screen.PermissionSetup> {
            PlaceholderScreen(name = "Permission Setup Screen")
        }
        composable<Screen.ContactDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ContactDetail>()
            PlaceholderScreen(name = "Contact Detail ID: ${args.contactId}")
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
