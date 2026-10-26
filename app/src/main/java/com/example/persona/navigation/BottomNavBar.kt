package com.example.persona.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.debug.DebugFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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

    val items = buildList {
        add(BottomNavItem(Screen.Home, "Home", Icons.Default.Home))
        if (DebugFlags.ENABLE_CHATS_TAB) {
            add(BottomNavItem(Screen.Chats, "Chats", Icons.Default.ChatBubble, hasBadge = true))
        }
        add(BottomNavItem(Screen.Orders, "Orders", Icons.Default.ShoppingBag))
        add(BottomNavItem(Screen.KnowledgeManagement, "Knowledge", Icons.Default.Info))
    }

    NavigationBar(modifier = modifier) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val isSelected = currentDestination?.hasRoute(item.screen::class) == true

            NavigationBarItem(
                icon = {
                    if (item.hasBadge && unreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                                }
                            }
                        ) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.screen) {
                        popUpTo(Screen.Home) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
