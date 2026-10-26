package com.example.persona.core.di

import android.content.Context
import androidx.room.Room
import com.example.persona.core.database.PersonaDatabase
import com.example.persona.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonaDatabase =
        Room.databaseBuilder(context, PersonaDatabase::class.java, "persona_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideChatMessageDao(db: PersonaDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideEmbeddingDao(db: PersonaDatabase): EmbeddingDao = db.embeddingDao()

    @Provides
    @Singleton
    fun provideLinkedContactDao(db: PersonaDatabase): LinkedContactDao = db.linkedContactDao()

    @Provides
    @Singleton
    fun provideReplyLogDao(db: PersonaDatabase): ReplyLogDao = db.replyLogDao()

    @Provides
    @Singleton
    fun provideReplyQueueDao(db: PersonaDatabase): ReplyQueueDao = db.replyQueueDao()
}
