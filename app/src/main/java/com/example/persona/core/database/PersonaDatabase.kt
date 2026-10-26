package com.example.persona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.core.database.dao.*
import com.example.persona.core.database.entity.*

/**
 * Room database — version 9.
 *
 * WhatsApp Live Inbox & CRM Migration (v8 → v9):
 *  - Added: conversation_metadata table ([ConversationMetadataEntity]) with composite (contact_name, source_class) PK.
 *  - Extended: chat_history with source_class, sender_type, delivery_status, and is_read columns.
 *  - Added: composite indices on chat_history for (contact_name, source_class, timestamp).
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
        ConversationMetadataEntity::class
    ],
    version = 9,
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
}
