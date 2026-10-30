package com.example.persona.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.ui.components.formatBadgeCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backward-compatible holder for bottom navigation tab configuration.
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val hasBadge: Boolean = false
)

@HiltViewModel
class BottomNavViewModel @Inject constructor(
    conversationMetadataDao: ConversationMetadataDao
) : ViewModel() {
    val totalUnreadCount: StateFlow<Int> = conversationMetadataDao.observeTotalUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: BottomNavViewModel = hiltViewModel()
) {
    val unreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()
    val destinations = TopLevelDestination.activeDestinations

    NavigationBar(modifier = modifier) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        destinations.forEach { destination ->
            val isSelected = currentDestination?.hasRoute(destination.screen::class) == true

            NavigationBarItem(
                icon = {
                    if (destination.hasBadge && unreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(text = formatBadgeCount(unreadCount) ?: "")
                                }
                            }
                        ) {
                            Icon(destination.icon, contentDescription = null)
                        }
                    } else {
                        Icon(destination.icon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        text = stringResource(destination.labelResId),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        // P1 Fix: Re-selecting active tab pops to that tab's root destination
                        navController.popBackStack(destination.screen, inclusive = false)
                    } else {
                        // P1 Fix: Dynamic start destination resolution instead of hardcoded Screen.Home
                        navController.navigate(destination.screen) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
