package com.verlintas.baic2.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        RunEntity::class,
        MemoryEntity::class,
        PlanEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class Baic2Database : RoomDatabase() {

    abstract fun agentDao(): AgentDao

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun runDao(): RunDao

    abstract fun memoryDao(): MemoryDao

    abstract fun planDao(): PlanDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_starred ON messages(starred)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        content TEXT NOT NULL,
                        conversationId INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_kind ON memories(kind)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plans (
                        conversationId INTEGER NOT NULL,
                        stepsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(conversationId)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
