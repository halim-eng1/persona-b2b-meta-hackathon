package com.example.persona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonaTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                
                val currentDestination = currentBackStackEntry?.destination
                val showBottomBar = currentDestination?.let { dest ->
                    dest.hasRoute(Screen.Home::class) ||
                    dest.hasRoute(Screen.Contacts::class) ||
                    dest.hasRoute(Screen.AuditLog::class) ||
                    dest.hasRoute(Screen.Settings::class)
                } ?: false

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
}