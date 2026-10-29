package com.example.persona.features.settings.di

import com.example.persona.features.settings.data.repository.SecureCredentialsRepositoryImpl
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecureCredentialsRepository(
        impl: SecureCredentialsRepositoryImpl
    ): SecureCredentialsRepository
}
