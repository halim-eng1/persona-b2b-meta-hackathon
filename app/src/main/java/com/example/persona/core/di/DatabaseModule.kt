package com.example.persona.core.di

import android.content.Context
import androidx.room.Room
import com.example.persona.core.database.PersonaDatabase
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
}
