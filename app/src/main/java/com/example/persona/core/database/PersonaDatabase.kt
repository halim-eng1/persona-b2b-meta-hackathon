package com.example.persona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.core.database.dao.*
import com.example.persona.core.database.entity.*

/**
 * Room database — version 12.
 *
 * Migration (v11 → v12):
 *  - Added: enterprise telemetry columns to `reply_log` table (gatekeeper_tier, provider_model, latency_ms, prompt_tokens, completion_tokens, decision_reason).
 */
@Database(
    entities = [
        BusinessKnowledgeEntity::class,
        EmbeddingIndexEntity::class,
        FailedQueryEntity::class,
        ReplyLogEntity::class,
        ReplyQueueEntity::class,
        ChatHistoryEntity::class,
        OrderEntity::class,
        ConversationMetadataEntity::class,
        ContactEntity::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PersonaDatabase : RoomDatabase() {
    abstract fun businessKnowledgeDao(): BusinessKnowledgeDao
    abstract fun embeddingIndexDao(): EmbeddingIndexDao
    abstract fun failedQueryDao(): FailedQueryDao
    abstract fun replyLogDao(): ReplyLogDao
    abstract fun replyQueueDao(): ReplyQueueDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun orderDao(): OrderDao
    abstract fun conversationMetadataDao(): ConversationMetadataDao
    abstract fun contactDao(): ContactDao
}
