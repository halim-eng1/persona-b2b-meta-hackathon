package com.example.persona.core.di

import android.content.Context
import androidx.room.Room
import com.example.persona.core.database.PersonaDatabase
import com.example.persona.core.database.dao.*
import com.example.persona.core.common.Constants
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
        Room.databaseBuilder(context, PersonaDatabase::class.java, Constants.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideBusinessKnowledgeDao(db: PersonaDatabase): BusinessKnowledgeDao =
        db.businessKnowledgeDao()

    @Provides
    @Singleton
    fun provideEmbeddingIndexDao(db: PersonaDatabase): EmbeddingIndexDao =
        db.embeddingIndexDao()

    @Provides
    @Singleton
    fun provideFailedQueryDao(db: PersonaDatabase): FailedQueryDao =
        db.failedQueryDao()

    @Provides
    @Singleton
    fun provideReplyLogDao(db: PersonaDatabase): ReplyLogDao = db.replyLogDao()

    @Provides
    @Singleton
    fun provideReplyQueueDao(db: PersonaDatabase): ReplyQueueDao = db.replyQueueDao()

    @Provides
    @Singleton
    fun provideChatHistoryDao(db: PersonaDatabase): ChatHistoryDao = db.chatHistoryDao()

    @Provides
    @Singleton
    fun provideOrderDao(db: PersonaDatabase): OrderDao = db.orderDao()
}
