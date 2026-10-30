package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.core.database.entity.ChatHistoryEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages reply deduplication, recent reply hash LRU cache per-contact, and anti-echo repetition prevention.
 * Capacity is increased to a rolling 100 entries per contact to prevent cross-contact evictions.
 */
@Singleton
class AntiEchoGuard @Inject constructor() {

    private val contactReplyCaches = ConcurrentHashMap<String, ConcurrentHashMap<Int, Long>>()
    private val contactReplyQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<Int>>()

    companion object {
        const val ROLLING_CAPACITY = 100
        private const val GLOBAL_KEY = "__global__"

        fun isEchoRepetition(
            candidateReply: String,
            currentUserText: String,
            chatHistory: List<ChatHistoryEntity>,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            val lastUserTurn = chatHistory.firstOrNull { it.isFromUser }
            if (lastUserTurn != null && currentUserText.trim().equals(lastUserTurn.messageText.trim(), ignoreCase = true)) {
                return false
            }

            val twoHoursAgo = nowMs - TimeUnit.HOURS.toMillis(2)
            val lastAssistantTurn = chatHistory.firstOrNull { !it.isFromUser && it.timestamp >= twoHoursAgo }
            return lastAssistantTurn != null && candidateReply.trim().lowercase() == lastAssistantTurn.messageText.trim().lowercase()
        }
    }

    fun addReplyToCache(text: String, contactKey: String = GLOBAL_KEY) {
        val hash = text.hashCode()
        val now = System.currentTimeMillis()
        val key = contactKey.trim().lowercase().ifBlank { GLOBAL_KEY }

        val queue = contactReplyQueues.getOrPut(key) { ConcurrentLinkedQueue() }
        val cache = contactReplyCaches.getOrPut(key) { ConcurrentHashMap() }

        synchronized(queue) {
            queue.remove(hash)
            cache[hash] = now
            queue.add(hash)
            while (queue.size > ROLLING_CAPACITY) {
                val evicted = queue.poll()
                if (evicted != null) {
                    cache.remove(evicted)
                }
            }
        }

        if (key != GLOBAL_KEY) {
            val gQueue = contactReplyQueues.getOrPut(GLOBAL_KEY) { ConcurrentLinkedQueue() }
            val gCache = contactReplyCaches.getOrPut(GLOBAL_KEY) { ConcurrentHashMap() }
            synchronized(gQueue) {
                gQueue.remove(hash)
                gCache[hash] = now
                gQueue.add(hash)
                while (gQueue.size > ROLLING_CAPACITY) {
                    val evicted = gQueue.poll()
                    if (evicted != null) {
                        gCache.remove(evicted)
                    }
                }
            }
        }
    }

    fun getActiveReplyHashes(contactKey: String = GLOBAL_KEY): List<Int> {
        val now = System.currentTimeMillis()
        val expiredThreshold = now - 30_000L // 30 seconds
        val key = contactKey.trim().lowercase().ifBlank { GLOBAL_KEY }

        val queue = contactReplyQueues[key] ?: contactReplyQueues[GLOBAL_KEY] ?: return emptyList()
        val cache = contactReplyCaches[key] ?: contactReplyCaches[GLOBAL_KEY] ?: return emptyList()

        synchronized(queue) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val hash = iterator.next()
                val ts = cache[hash] ?: 0L
                if (ts < expiredThreshold) {
                    iterator.remove()
                    cache.remove(hash)
                }
            }
            return queue.toList()
        }
    }
}
