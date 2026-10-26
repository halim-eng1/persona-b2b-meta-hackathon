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
 * The [fallbackToDestructiveMigration] call in [DatabaseModule] handles the upgrade safely
 * for this pre-production codebase (no existing user data to preserve).
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

