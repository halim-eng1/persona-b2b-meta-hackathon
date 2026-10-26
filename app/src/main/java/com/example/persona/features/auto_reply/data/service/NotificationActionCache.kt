package com.example.persona.features.auto_reply.data.service

import android.app.Notification

/**
 * In-memory LRU cache of Notification.Action objects for DirectReply execution.
 * Thread-safe. Entries are evicted after 5 minutes or when the notification is dismissed.
 */
object NotificationActionCache {
    private data class CachedAction(
        val action: Notification.Action,
        val cachedAt: Long = System.currentTimeMillis()
    )

    private const val MAX_SIZE = 50
    private const val TTL_MS = 5 * 60 * 1000L  // 5 minutes

    private val cache = LinkedHashMap<String, CachedAction>(MAX_SIZE, 0.75f, true)

    @Synchronized
    fun put(notificationKey: String, action: Notification.Action) {
        evictExpired()
        cache[notificationKey] = CachedAction(action)
        if (cache.size > MAX_SIZE) {
            cache.remove(cache.keys.first())
        }
    }

    @Synchronized
    fun get(notificationKey: String): Notification.Action? {
        evictExpired()
        return cache[notificationKey]?.action
    }

    @Synchronized
    fun remove(notificationKey: String) {
        cache.remove(notificationKey)
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeAll { now - it.value.cachedAt > TTL_MS }
    }
}
