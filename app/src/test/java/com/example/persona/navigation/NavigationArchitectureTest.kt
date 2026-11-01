package com.example.persona.navigation

import com.example.persona.ui.components.formatBadgeCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationArchitectureTest {

    @Test
    fun testScreenDestinationsSerializable() {
        val home = Screen.Home
        val chats = Screen.Chats
        val orders = Screen.Orders
        val knowledge = Screen.KnowledgeManagement
        val contacts = Screen.Contacts
        val audit = Screen.AuditLog
        val settings = Screen.Settings
        val onboarding = Screen.Onboarding
        val permission = Screen.PermissionSetup
        val featureOnboarding = Screen.FeatureOnboarding
        val importGraph = Screen.ChatImportGraph
        val importLeaf = Screen.ChatImport
        val progress = Screen.ChatImportProgress("Customer")
        val chatDetail = Screen.ChatDetail("Ahmed", "BUSINESS")

        assertNotNull(home)
        assertNotNull(chats)
        assertNotNull(orders)
        assertNotNull(knowledge)
        assertNotNull(contacts)
        assertNotNull(audit)
        assertNotNull(settings)
        assertNotNull(onboarding)
        assertNotNull(permission)
        assertNotNull(featureOnboarding)
        assertNotNull(importGraph)
        assertNotNull(importLeaf)
        assertEquals("Customer", progress.contactName)
        assertEquals("Ahmed", chatDetail.contactName)
        assertEquals("BUSINESS", chatDetail.sourceClass)
    }

    @Test
    fun testChatDetailDefaults() {
        val chatDetail = Screen.ChatDetail(contactName = "Mahmoud")
        assertEquals("Mahmoud", chatDetail.contactName)
        assertEquals("BUSINESS", chatDetail.sourceClass)
    }

    @Test
    fun testTopLevelDestinationsResourceLabels() {
        assertEquals(com.example.persona.R.string.nav_home, TopLevelDestination.HOME.labelResId)
        assertEquals(com.example.persona.R.string.nav_conversations, TopLevelDestination.CHATS.labelResId)
        assertEquals(com.example.persona.R.string.nav_orders, TopLevelDestination.ORDERS.labelResId)
        assertEquals(com.example.persona.R.string.nav_knowledge, TopLevelDestination.KNOWLEDGE.labelResId)
    }

    @Test
    fun testTopLevelDestinationsScreens() {
        assertEquals(Screen.Home, TopLevelDestination.HOME.screen)
        assertEquals(Screen.Chats, TopLevelDestination.CHATS.screen)
        assertEquals(Screen.Orders, TopLevelDestination.ORDERS.screen)
        assertEquals(Screen.KnowledgeManagement, TopLevelDestination.KNOWLEDGE.screen)
    }

    @Test
    fun testActiveDestinationsContainAllTabs() {
        val active = TopLevelDestination.activeDestinations
        assertTrue(active.contains(TopLevelDestination.HOME))
        assertTrue(active.contains(TopLevelDestination.CHATS))
        assertTrue(active.contains(TopLevelDestination.ORDERS))
        assertTrue(active.contains(TopLevelDestination.KNOWLEDGE))
    }

    @Test
    fun testBadgeFormatting() {
        assertNull(formatBadgeCount(0))
        assertNull(formatBadgeCount(-1))
        assertEquals("1", formatBadgeCount(1))
        assertEquals("12", formatBadgeCount(12))
        assertEquals("99", formatBadgeCount(99))
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(999))
    }
}
