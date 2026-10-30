package com.example.persona.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.persona.core.debug.DebugFlags

/**
 * Single source of truth for bottom navigation destinations.
 * Synchronizes route matching and tab display between MainActivity and BottomNavBar.
 */
enum class TopLevelDestination(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val hasBadge: Boolean = false,
    val isEnabled: Boolean = true
) {
    HOME(
        screen = Screen.Home,
        label = "الرئيسية",
        icon = Icons.Default.Home
    ),
    CHATS(
        screen = Screen.Chats,
        label = "المحادثات",
        icon = Icons.Default.ChatBubble,
        hasBadge = true,
        isEnabled = DebugFlags.ENABLE_CHATS_TAB
    ),
    ORDERS(
        screen = Screen.Orders,
        label = "الطلبات",
        icon = Icons.Default.ShoppingBag
    ),
    KNOWLEDGE(
        screen = Screen.KnowledgeManagement,
        label = "قاعدة المعرفة",
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
