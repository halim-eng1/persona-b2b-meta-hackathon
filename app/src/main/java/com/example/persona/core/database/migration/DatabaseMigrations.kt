package com.example.persona.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Enterprise database migrations for [com.example.persona.core.database.PersonaDatabase].
 */
object DatabaseMigrations {

    /**
     * Migration from Database v7 to v8.
     * Alters the `linked_contacts` table by adding:
     * - `total_orders_count` INTEGER NOT NULL DEFAULT 0
     * - `lifetime_spent_cents` INTEGER NOT NULL DEFAULT 0
     * - `notes` TEXT (nullable)
     * - `tags` TEXT NOT NULL DEFAULT '[]'
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `linked_contacts` ADD COLUMN `total_orders_count` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `linked_contacts` ADD COLUMN `lifetime_spent_cents` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `linked_contacts` ADD COLUMN `notes` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `linked_contacts` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * Migration from Database v8 to v9.
     * Adds `conversation_metadata` table for WhatsApp Live Inbox & CRM tracking.
     * Extends `chat_history` with CRM telemetry: `source_class`, `sender_type`, `delivery_status`, `is_read`.
     * Adds compound indices on `chat_history` for performant inbox querying.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create conversation_metadata table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversation_metadata` (
                    `contact_name` TEXT NOT NULL,
                    `source_class` TEXT NOT NULL,
                    `unread_count` INTEGER NOT NULL DEFAULT 0,
                    `last_message_text` TEXT NOT NULL DEFAULT '',
                    `last_message_sender_type` TEXT NOT NULL DEFAULT 'CUSTOMER',
                    `last_message_timestamp` INTEGER NOT NULL DEFAULT 0,
                    `last_notification_key` TEXT,
                    `is_pinned` INTEGER NOT NULL DEFAULT 0,
                    `is_ai_paused` INTEGER NOT NULL DEFAULT 0,
                    `notes` TEXT,
                    `tags` TEXT NOT NULL DEFAULT '[]',
                    PRIMARY KEY(`contact_name`, `source_class`)
                )
                """.trimIndent()
            )

            // Indices for conversation_metadata
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_metadata_last_message_timestamp` " +
                    "ON `conversation_metadata` (`last_message_timestamp`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_metadata_is_pinned_last_message_timestamp` " +
                    "ON `conversation_metadata` (`is_pinned`, `last_message_timestamp`)"
            )

            // 2. Extend chat_history with CRM telemetry columns
            db.execSQL(
                "ALTER TABLE `chat_history` ADD COLUMN `source_class` TEXT NOT NULL DEFAULT 'BUSINESS'"
            )
            db.execSQL(
                "ALTER TABLE `chat_history` ADD COLUMN `sender_type` TEXT NOT NULL DEFAULT 'CUSTOMER'"
            )
            db.execSQL(
                "ALTER TABLE `chat_history` ADD COLUMN `delivery_status` TEXT NOT NULL DEFAULT 'SENT'"
            )
            db.execSQL(
                "ALTER TABLE `chat_history` ADD COLUMN `is_read` INTEGER NOT NULL DEFAULT 1"
            )

            // 3. Add composite index on chat_history for inbox lookups
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_history_contact_name_source_class_timestamp` " +
                    "ON `chat_history` (`contact_name`, `source_class`, `timestamp`)"
            )

            // 4. Backfill conversation_metadata from existing chat_history
            db.execSQL(
                """
                INSERT OR IGNORE INTO `conversation_metadata` (
                    `contact_name`,
                    `source_class`,
                    `unread_count`,
                    `last_message_text`,
                    `last_message_sender_type`,
                    `last_message_timestamp`,
                    `last_notification_key`,
                    `is_pinned`,
                    `is_ai_paused`,
                    `tags`
                )
                SELECT
                    ch.contact_name,
                    ch.source_class,
                    0,
                    ch.message_text,
                    ch.sender_type,
                    ch.timestamp,
                    NULL,
                    0,
                    0,
                    '[]'
                FROM `chat_history` ch
                INNER JOIN (
                    SELECT contact_name, source_class, MAX(timestamp) AS max_ts
                    FROM `chat_history`
                    GROUP BY contact_name, source_class
                ) latest ON ch.contact_name = latest.contact_name
                    AND ch.source_class = latest.source_class
                    AND ch.timestamp = latest.max_ts
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from Database v9 to v10.
     * Introduces `contacts_table` for central address book caching and WhatsApp sync.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `contacts_table` (
                    `contact_name` TEXT NOT NULL PRIMARY KEY,
                    `phone_number` TEXT,
                    `profile_picture_uri` TEXT,
                    `last_active_timestamp` INTEGER NOT NULL DEFAULT 0,
                    `tags` TEXT NOT NULL DEFAULT '[]',
                    `notes` TEXT,
                    `is_blocked` INTEGER NOT NULL DEFAULT 0,
                    `custom_fields` TEXT NOT NULL DEFAULT '{}'
                )
                """.trimIndent()
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contacts_table_phone_number` " +
                    "ON `contacts_table` (`phone_number`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contacts_table_last_active_timestamp` " +
                    "ON `contacts_table` (`last_active_timestamp`)"
            )

            // Backfill contacts_table with distinct contacts already found in chat_history
            db.execSQL(
                "INSERT OR IGNORE INTO `contacts_table` (`contact_name`, `last_active_timestamp`) " +
                    "SELECT `contact_name`, MAX(`timestamp`) FROM `chat_history` GROUP BY `contact_name`"
            )
        }
    }

    /**
     * v10 → v11:
     *  - Extends `conversation_metadata` with `is_ai_override_enabled` column for per-chat AI override.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_metadata` ADD COLUMN `is_ai_override_enabled` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v11 → v12:
     *  - Extends `reply_log` with enterprise telemetry columns:
     *    `gatekeeper_tier`, `provider_model`, `latency_ms`, `prompt_tokens`, `completion_tokens`, `decision_reason`.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `gatekeeper_tier` TEXT NOT NULL DEFAULT 'MODEL'")
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `provider_model` TEXT NOT NULL DEFAULT 'Unknown'")
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `latency_ms` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `prompt_tokens` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `completion_tokens` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `reply_log` ADD COLUMN `decision_reason` TEXT DEFAULT NULL")
        }
    }

    /** All migration paths, registered via `.addMigrations(...)` in DatabaseModule. */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12
    )
}
