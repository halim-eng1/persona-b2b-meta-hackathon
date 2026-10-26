package com.example.persona

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.persona.navigation.BottomNavBar
import com.example.persona.navigation.PersonaNavGraph
import com.example.persona.navigation.Screen
import com.example.persona.ui.theme.PersonaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepLinkContact by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            PersonaTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()

                val currentDestination = currentBackStackEntry?.destination
                val showBottomBar = currentDestination?.let { dest ->
                    dest.hasRoute(Screen.Home::class) ||
                    dest.hasRoute(Screen.Chats::class) ||
                    dest.hasRoute(Screen.Orders::class) ||
                    dest.hasRoute(Screen.KnowledgeManagement::class)
                } ?: false

                LaunchedEffect(pendingDeepLinkContact) {
                    pendingDeepLinkContact?.let { (contact, source) ->
                        navController.navigate(Screen.ChatDetail(contact, source))
                        pendingDeepLinkContact = null
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = if (showBottomBar) WindowInsets.systemBars else WindowInsets(0, 0, 0, 0),
                    containerColor = Color(0xFF0F172A),
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
