package com.example.persona

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.navigation.BottomNavBar
import com.example.persona.navigation.PersonaNavGraph
import com.example.persona.navigation.Screen
import com.example.persona.navigation.TopLevelDestination
import com.example.persona.ui.theme.PersonaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionChecker: PermissionChecker

    private var pendingDeepLinkContact by mutableStateOf<Pair<String, String>?>(null)

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !permissionChecker.isManageStorageGranted()) {
            Log.w("MainActivity", "MANAGE_EXTERNAL_STORAGE not granted: merchant has not enabled All Files Access yet")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // P0 Fix: Do NOT skip handleIntent on process death (savedInstanceState != null)
        handleIntent(intent)

        setContent {
            PersonaTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()

                val currentDestination = currentBackStackEntry?.destination
                val showBottomBar = TopLevelDestination.isTopLevelDestination(currentDestination)

                LaunchedEffect(pendingDeepLinkContact) {
                    pendingDeepLinkContact?.let { (contact, source) ->
                        val currentContact = currentBackStackEntry?.arguments?.getString("contactName")
                        val isAlreadyOnSameChat = currentDestination?.hasRoute(Screen.ChatDetail::class) == true &&
                            currentContact == contact

                        if (!isAlreadyOnSameChat) {
                            navController.navigate(Screen.ChatDetail(contact, source)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                        pendingDeepLinkContact = null
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = if (showBottomBar) WindowInsets.systemBars else WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    PersonaNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val contactName = intent?.getStringExtra("open_chat_contact_name")
        val sourceClass = intent?.getStringExtra("open_chat_source_class") ?: "BUSINESS"
        intent?.removeExtra("open_chat_contact_name")
        if (!contactName.isNullOrBlank()) {
            pendingDeepLinkContact = contactName to sourceClass
        }
    }
}
