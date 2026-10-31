package com.example.persona.core.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DatabaseMigration11To12Test {

    @Test
    fun `migration 11 to 12 adds telemetry columns to reply_log`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_11_12.migrate(db)

        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `gatekeeper_tier` TEXT NOT NULL DEFAULT 'MODEL'")
        }
        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `provider_model` TEXT NOT NULL DEFAULT 'Unknown'")
        }
        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `latency_ms` INTEGER NOT NULL DEFAULT 0")
        }
        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `prompt_tokens` INTEGER NOT NULL DEFAULT 0")
        }
        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `completion_tokens` INTEGER NOT NULL DEFAULT 0")
        }
        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `decision_reason` TEXT DEFAULT NULL")
        }
    }
}

