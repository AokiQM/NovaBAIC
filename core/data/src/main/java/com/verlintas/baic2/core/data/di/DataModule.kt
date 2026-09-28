package com.verlintas.baic2.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.verlintas.baic2.core.data.db.AgentDao
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.db.ConversationDao
import com.verlintas.baic2.core.data.db.MemoryDao
import com.verlintas.baic2.core.data.db.MessageDao
import com.verlintas.baic2.core.data.db.RunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Baic2Database =
        Room.databaseBuilder(context, Baic2Database::class.java, "baic2.db")
            .addMigrations(Baic2Database.MIGRATION_1_2, Baic2Database.MIGRATION_2_3)
            .build()

    @Provides
    fun provideAgentDao(db: Baic2Database): AgentDao = db.agentDao()

    @Provides
    fun provideConversationDao(db: Baic2Database): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: Baic2Database): MessageDao = db.messageDao()

    @Provides
    fun provideRunDao(db: Baic2Database): RunDao = db.runDao()

    @Provides
    fun provideMemoryDao(db: Baic2Database): MemoryDao = db.memoryDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
