package com.example.persona.features.auto_reply.data.service

import android.app.Notification

/**
 * In-memory LRU cache of Notification.Action objects for DirectReply execution.
 * Thread-safe with dual-indexing:
 *  1. Primary: exact [notificationKey] lookup (used by AutoReplyOrchestrator).
 *  2. Secondary: composite (sourceClass, contactName) lookup (used as fallback by manual reply CRM).
 *
 * Capacity: 200 items. TTL: 24 hours.
 * Actions are NOT evicted on notification dismiss; eviction is strictly TTL/LRU-driven
 * to enable manual reply dispatch even after notification banners are swiped away.
 */
object NotificationActionCache {
    private data class CachedAction(
        val action: Notification.Action,
        val notificationKey: String,
        val compositeKey: String,
        val cachedAt: Long = System.currentTimeMillis()
    )

    private const val MAX_SIZE = 200
    private const val TTL_MS = 24 * 60 * 60 * 1000L  // 24 hours

    private val keyCache = LinkedHashMap<String, CachedAction>(MAX_SIZE, 0.75f, true)
    private val compositeCache = HashMap<String, CachedAction>(MAX_SIZE)

    internal fun buildCompositeKey(sourceClass: String, contactName: String): String =
        "${sourceClass.trim().uppercase()}:${contactName.trim().lowercase()}"

    @Synchronized
    fun put(
        notificationKey: String,
        action: Notification.Action,
        sourceClass: String? = null,
        contactName: String? = null
    ) {
        evictExpired()
        val compositeKey = if (sourceClass != null && contactName != null) {
            buildCompositeKey(sourceClass, contactName)
        } else {
            ""
        }
        val cached = CachedAction(
            action = action,
            notificationKey = notificationKey,
            compositeKey = compositeKey
        )
        keyCache[notificationKey] = cached
        if (compositeKey.isNotEmpty()) {
            compositeCache[compositeKey] = cached
        }

        while (keyCache.size > MAX_SIZE) {
            val oldestKey = keyCache.keys.firstOrNull() ?: break
            val oldest = keyCache.remove(oldestKey)
            if (oldest != null && oldest.compositeKey.isNotEmpty()) {
                if (compositeCache[oldest.compositeKey] == oldest) {
                    compositeCache.remove(oldest.compositeKey)
                }
            }
        }
    }

    /** Overload for backwards compatibility. */
    @Synchronized
    fun put(notificationKey: String, action: Notification.Action) {
        put(notificationKey, action, null, null)
    }

    @Synchronized
    fun get(notificationKey: String): Notification.Action? {
        evictExpired()
        return keyCache[notificationKey]?.action
    }

    @Synchronized
    fun get(sourceClass: String, contactName: String): Notification.Action? {
        evictExpired()
        val compositeKey = buildCompositeKey(sourceClass, contactName)
        return compositeCache[compositeKey]?.action
    }

    /** Retained for explicit clearing if needed; not called on notification dismissal. */
    @Synchronized
    fun remove(notificationKey: String) {
        val removed = keyCache.remove(notificationKey)
        if (removed != null && removed.compositeKey.isNotEmpty()) {
            if (compositeCache[removed.compositeKey] == removed) {
                compositeCache.remove(removed.compositeKey)
            }
        }
    }

    @Synchronized
    fun clear() {
        keyCache.clear()
        compositeCache.clear()
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<String>()
        for ((key, item) in keyCache) {
            if (now - item.cachedAt > TTL_MS) {
                expired.add(key)
            }
        }
        for (key in expired) {
            val item = keyCache.remove(key)
            if (item != null && item.compositeKey.isNotEmpty()) {
                if (compositeCache[item.compositeKey] == item) {
                    compositeCache.remove(item.compositeKey)
                }
            }
        }
    }
}
