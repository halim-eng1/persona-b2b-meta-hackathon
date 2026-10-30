package com.example.persona.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.persona.R
import com.example.persona.core.debug.DebugFlags

/**
 * Single source of truth for bottom navigation destinations.
 * Synchronizes route matching and tab display between MainActivity and BottomNavBar.
 */
enum class TopLevelDestination(
    val screen: Screen,
    @StringRes val labelResId: Int,
    val icon: ImageVector,
    val hasBadge: Boolean = false,
    val isEnabled: Boolean = true
) {
    HOME(
        screen = Screen.Home,
        labelResId = R.string.nav_home,
        icon = Icons.Default.Home
    ),
    CHATS(
        screen = Screen.Chats,
        labelResId = R.string.nav_conversations,
        icon = Icons.Default.ChatBubble,
        hasBadge = true,
        isEnabled = DebugFlags.ENABLE_CHATS_TAB
    ),
    ORDERS(
        screen = Screen.Orders,
        labelResId = R.string.nav_orders,
        icon = Icons.Default.ShoppingBag
    ),
    KNOWLEDGE(
        screen = Screen.KnowledgeManagement,
        labelResId = R.string.nav_knowledge,
        icon = Icons.Default.Info
    );

    companion object {
        /** Returns all currently active top-level destinations based on runtime flags. */
        val activeDestinations: List<TopLevelDestination>
            get() = entries.filter { it.isEnabled }

        /** Checks if a navigation destination matches any active top-level screen. */
        fun isTopLevelDestination(destination: NavDestination?): Boolean {
            if (destination == null) return false
            return activeDestinations.any { destination.hasRoute(it.screen::class) }
        }
    }
}
