package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.core.database.entity.ChatHistoryEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages reply deduplication, recent reply hash LRU cache, and anti-echo repetition prevention.
 */
@Singleton
class AntiEchoGuard @Inject constructor() {

    private val recentReplyCache = ConcurrentHashMap<Int, Long>()
    private val recentReplyQueue = ConcurrentLinkedQueue<Int>()

    fun addReplyToCache(text: String) {
        val hash = text.hashCode()
        val now = System.currentTimeMillis()
        synchronized(this) {
            recentReplyQueue.remove(hash)
            recentReplyCache[hash] = now
            recentReplyQueue.add(hash)
            while (recentReplyQueue.size > 3) {
                val evicted = recentReplyQueue.poll()
                if (evicted != null) {
                    recentReplyCache.remove(evicted)
                }
            }
        }
    }

    fun getActiveReplyHashes(): List<Int> {
        val now = System.currentTimeMillis()
        val expiredThreshold = now - 30_000L // 30 seconds
        synchronized(this) {
            val iterator = recentReplyQueue.iterator()
            while (iterator.hasNext()) {
                val hash = iterator.next()
                val ts = recentReplyCache[hash] ?: 0L
                if (ts < expiredThreshold) {
                    iterator.remove()
                    recentReplyCache.remove(hash)
                }
            }
            return recentReplyQueue.toList()
        }
    }

    companion object {
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
}
