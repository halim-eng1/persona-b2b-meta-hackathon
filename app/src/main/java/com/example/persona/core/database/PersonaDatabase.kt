package com.example.persona.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.core.database.dao.*
import com.example.persona.core.database.entity.*

@Database(
    entities = [
        ChatMessageEntity::class,
        EmbeddingEntity::class,
        LinkedContactEntity::class,
        ReplyLogEntity::class,
        ReplyQueueEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PersonaDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun linkedContactDao(): LinkedContactDao
    abstract fun replyLogDao(): ReplyLogDao
    abstract fun replyQueueDao(): ReplyQueueDao
}
