package com.example.persona.features.knowledge.di

import com.example.persona.features.knowledge.data.indexer.OnnxKnowledgeVectorIndexer
import com.example.persona.features.knowledge.domain.indexer.KnowledgeVectorIndexer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {

    @Binds
    @Singleton
    abstract fun bindKnowledgeVectorIndexer(
        impl: OnnxKnowledgeVectorIndexer
    ): KnowledgeVectorIndexer
}
