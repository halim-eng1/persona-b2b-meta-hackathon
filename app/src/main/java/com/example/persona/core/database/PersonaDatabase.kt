package com.example.persona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.core.database.dao.*
import com.example.persona.core.database.entity.*

/**
 * Room database — version 8.
 *
 * Commerce Engine Migration (v7 → v8) — DESTRUCTIVE:
 *  - Added: chat_history table ([ChatHistoryEntity]) with composite (contactName, timestamp) index.
 *  - Added: orders_table ([OrderEntity]) with composite (contactName, status) index.
 *  - Added: EmbeddingJobState column on chat_history for background embedding state machine.
 *
 * Migration policy (post hardening): upgrades require explicit registered
 * migrations (see [com.example.persona.core.database.migration.DatabaseMigrations]);
 * only downgrades fall back to destructive handling. The v7 → v8 upgrade used
 * the pre-hardening destructive fallback and had no existing user data to preserve.
 */
@Database(
    entities = [
        BusinessKnowledgeEntity::class,
        EmbeddingIndexEntity::class,
        FailedQueryEntity::class,
        ReplyLogEntity::class,
        ReplyQueueEntity::class,
        ChatHistoryEntity::class,
        OrderEntity::class
    ],
    version = 8,
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
}

