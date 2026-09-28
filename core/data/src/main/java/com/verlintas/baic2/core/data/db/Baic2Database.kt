package com.verlintas.baic2.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        RunEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class Baic2Database : RoomDatabase() {

    abstract fun agentDao(): AgentDao

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun runDao(): RunDao
}
