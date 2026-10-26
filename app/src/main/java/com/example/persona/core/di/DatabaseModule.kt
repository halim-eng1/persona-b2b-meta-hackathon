package com.example.persona.core.di

import android.content.Context
import androidx.room.Room
import com.example.persona.core.database.PersonaDatabase
import com.example.persona.core.database.dao.*
import com.example.persona.core.database.migration.DatabaseMigrations
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

    /**
     * Migration hardening (formerly used [fallbackToDestructiveMigration]):
     *  - Upgrades require an explicit migration ([DatabaseMigrations.ALL_MIGRATIONS]).
     *    An unregistered upgrade now fails fast with a schema-validation exception
     *    instead of silently wiping orders / knowledge chunks / chat history.
     *  - Downgrades only: destructive fallback is permitted, since a downgrade
     *    cannot be expressed as a forward migration.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonaDatabase =
        Room.databaseBuilder(context, PersonaDatabase::class.java, Constants.DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
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
