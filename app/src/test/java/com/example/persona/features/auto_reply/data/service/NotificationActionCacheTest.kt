package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NotificationActionCacheTest {

    @Before
    fun setUp() {
        NotificationActionCache.clear()
    }

    @After
    fun tearDown() {
        NotificationActionCache.clear()
    }

    @Test
    fun `put and get by notificationKey retrieves cached action`() {
        val action = mockk<Notification.Action>()
        NotificationActionCache.put("key_1", action, "BUSINESS", "أحمد")

        val retrieved = NotificationActionCache.get("key_1")
        assertNotNull(retrieved)
        assertEquals(action, retrieved)
    }

    @Test
    fun `put and get by compositeKey retrieves cached action with case-insensitive contact matching`() {
        val action = mockk<Notification.Action>()
        NotificationActionCache.put("key_1", action, "BUSINESS", "Ahmed Hassan")

        val retrieved = NotificationActionCache.get("business", "ahmed hassan")
        assertNotNull(retrieved)
        assertEquals(action, retrieved)

        // Trimming whitespace
        val retrievedWithSpaces = NotificationActionCache.get(" BUSINESS ", "  Ahmed Hassan ")
        assertNotNull(retrievedWithSpaces)
        assertEquals(action, retrievedWithSpaces)
    }

    @Test
    fun `compositeCache isolates different source classes for same contact name`() {
        val bizAction = mockk<Notification.Action>()
        val mainAction = mockk<Notification.Action>()

        NotificationActionCache.put("key_biz", bizAction, "BUSINESS", "Mohamed")
        NotificationActionCache.put("key_main", mainAction, "MAIN_APP", "Mohamed")

        assertEquals(bizAction, NotificationActionCache.get("BUSINESS", "Mohamed"))
        assertEquals(mainAction, NotificationActionCache.get("MAIN_APP", "Mohamed"))
    }

    @Test
    fun `remove by notificationKey also clears composite entry`() {
        val action = mockk<Notification.Action>()
        NotificationActionCache.put("key_1", action, "BUSINESS", "User1")

        assertEquals(action, NotificationActionCache.get("BUSINESS", "User1"))

        NotificationActionCache.remove("key_1")
        assertNull(NotificationActionCache.get("key_1"))
        assertNull(NotificationActionCache.get("BUSINESS", "User1"))
    }

    @Test
    fun `cache respects maximum capacity of 200 items`() {
        for (i in 1..220) {
            val action = mockk<Notification.Action>()
            NotificationActionCache.put("key_$i", action, "BUSINESS", "Contact_$i")
        }

        // Oldest 20 items (1..20) must have been evicted
        assertNull(NotificationActionCache.get("key_1"))
        assertNull(NotificationActionCache.get("BUSINESS", "Contact_1"))
        assertNull(NotificationActionCache.get("key_20"))
        assertNull(NotificationActionCache.get("BUSINESS", "Contact_20"))

        // Newest items must be retained
        assertNotNull(NotificationActionCache.get("key_220"))
        assertNotNull(NotificationActionCache.get("BUSINESS", "Contact_220"))
    }

    @Test
    fun `buildCompositeKey creates expected canonical string`() {
        val key = NotificationActionCache.buildCompositeKey("business", " Mohamed Ali ")
        assertEquals("BUSINESS:mohamed ali", key)
    }
}
