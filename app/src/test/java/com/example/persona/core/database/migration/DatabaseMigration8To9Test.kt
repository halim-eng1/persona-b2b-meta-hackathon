package com.example.persona.core.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DatabaseMigration8To9Test {

    @Test
    fun `migration 8 to 9 alters chat_history, creates conversation_metadata and backfills data`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_8_9.migrate(db)

        // 1. Verify chat_history column additions
        verify { db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `source_class` TEXT NOT NULL DEFAULT 'BUSINESS'") }
        verify { db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `sender_type` TEXT NOT NULL DEFAULT 'CUSTOMER'") }
        verify { db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `delivery_status` TEXT NOT NULL DEFAULT 'SENT'") }
        verify { db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `is_read` INTEGER NOT NULL DEFAULT 1") }

        // 2. Verify chat_history composite index
        verify {
            db.execSQL(match { it.contains("index_chat_history_contact_name_source_class_timestamp") })
        }

        // 3. Verify conversation_metadata table creation with composite PK
        verify {
            db.execSQL(match {
                it.contains("CREATE TABLE IF NOT EXISTS `conversation_metadata`") &&
                    it.contains("PRIMARY KEY(`contact_name`, `source_class`)") &&
                    it.contains("last_message_sender_type")
            })
        }

        // 4. Verify standard indices on conversation_metadata
        verify {
            db.execSQL(match { it.contains("index_conversation_metadata_last_message_timestamp") })
        }
        verify {
            db.execSQL(match { it.contains("index_conversation_metadata_is_pinned_last_message_timestamp") })
        }

        // 5. Verify smart backfill from existing chat_history
        verify {
            db.execSQL(match {
                it.contains("INSERT OR IGNORE INTO `conversation_metadata`") &&
                    it.contains("FROM `chat_history`") &&
                    it.contains("GROUP BY contact_name, source_class")
            })
        }
    }
}
