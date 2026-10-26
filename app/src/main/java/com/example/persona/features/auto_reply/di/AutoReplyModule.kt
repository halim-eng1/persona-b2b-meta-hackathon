package com.example.persona.features.auto_reply.di

import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AutoReplyModule {

    @Binds
    @Singleton
    abstract fun bindReplyGenerationRepository(
        impl: ReplyGenerationRepositoryImpl
    ): ReplyGenerationRepository
}
