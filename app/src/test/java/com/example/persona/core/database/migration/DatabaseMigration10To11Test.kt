package com.example.persona.core.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DatabaseMigration10To11Test {

    @Test
    fun `migration 10 to 11 adds is_ai_override_enabled column to conversation_metadata`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_10_11.migrate(db)

        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `is_ai_override_enabled` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
