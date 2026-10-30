package com.example.persona.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, safe Room migration paths for [com.example.persona.core.database.PersonaDatabase].
 *
 * Policy (post hardening):
 *  - `fallbackToDestructiveMigration()` has been REMOVED from [com.example.persona.core.di.DatabaseModule].
 *    Any schema bump without a registered migration now throws a runtime exception
 *    instead of silently wiping merchant orders, knowledge chunks and chat history.
 *  - Only downgrades may fall back to destructive handling
 *    (`fallbackToDestructiveMigrationOnDowngrade()`), since a downgrade cannot be
 *    expressed as a forward migration.
 *
 * Rules for adding a migration:
 *  1. Bump `version` in [com.example.persona.core.database.PersonaDatabase] (e.g. 8 → 9).
 *  2. Write the SQL alterations here — ONLY additive/transformative DDL:
 *     `CREATE TABLE`, `CREATE INDEX`, `ALTER TABLE ... ADD COLUMN` (with a non-null
 *     default), or the Room-recommended recreate-table-with-copy pattern for
 *     column removal/type changes.
 *  3. Register it in [ALL_MIGRATIONS].
 *  4. Let KSP regenerate the schema JSON (exports to `app/schemas/.../<version>.json`)
 *     and commit it alongside the code change.
 */
object DatabaseMigrations {

    /**
     * v7 → v8: introduces `chat_history` and `orders_table`.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`contact_name` TEXT NOT NULL, " +
                    "`message_text` TEXT NOT NULL, " +
                    "`is_from_user` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`embedding` BLOB, " +
                    "`embedding_state` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_history_contact_name_timestamp` " +
                    "ON `chat_history` (`contact_name`, `timestamp`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_history_contact_name` " +
                    "ON `chat_history` (`contact_name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `orders_table` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`contact_name` TEXT NOT NULL, " +
                    "`customer_name_from_llm` TEXT, " +
                    "`delivery_address` TEXT, " +
                    "`order_details` TEXT, " +
                    "`custom_notes` TEXT, " +
                    "`merchant_note` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_orders_table_contact_name_status` " +
                    "ON `orders_table` (`contact_name`, `status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_orders_table_contact_name` " +
                    "ON `orders_table` (`contact_name`)"
            )
        }
    }

    /**
     * v8 → v9: WhatsApp Live Inbox & CRM Schema Migration:
     *  - Adds columns to `chat_history`: `source_class`, `sender_type`, `delivery_status`, `is_read`.
     *  - Adds composite indices on `chat_history`: `(contact_name, source_class, timestamp)` and `(contact_name, source_class)`.
     *  - Creates `conversation_metadata` table with composite PK `(contact_name, source_class)`.
     *  - Creates indices and partial indices on `conversation_metadata`.
     *  - Smart backfill: populates `conversation_metadata` from existing `chat_history` records.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Extend chat_history table
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `source_class` TEXT NOT NULL DEFAULT 'BUSINESS'")
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `sender_type` TEXT NOT NULL DEFAULT 'CUSTOMER'")
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `delivery_status` TEXT NOT NULL DEFAULT 'SENT'")
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `is_read` INTEGER NOT NULL DEFAULT 0")

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_history_contact_name_source_class_timestamp` " +
                    "ON `chat_history` (`contact_name`, `source_class`, `timestamp`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_history_contact_name_source_class` " +
                    "ON `chat_history` (`contact_name`, `source_class`)"
            )

            // 2. Create conversation_metadata table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversation_metadata` (" +
                    "`contact_name` TEXT NOT NULL, " +
                    "`source_class` TEXT NOT NULL DEFAULT 'BUSINESS', " +
                    "`is_ai_paused` INTEGER NOT NULL DEFAULT 0, " +
                    "`needs_human_attention` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_notification_key` TEXT, " +
                    "`last_message_text` TEXT, " +
                    "`last_message_sender_type` TEXT NOT NULL DEFAULT 'CUSTOMER', " +
                    "`last_message_timestamp` INTEGER NOT NULL DEFAULT 0, " +
                    "`unread_count` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`contact_name`, `source_class`))"
            )

            // 3. Create indices on conversation_metadata
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_metadata_last_message_timestamp` " +
                    "ON `conversation_metadata` (`last_message_timestamp`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_metadata_unread_count` " +
                    "ON `conversation_metadata` (`unread_count`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_metadata_needs_human_attention` " +
                    "ON `conversation_metadata` (`needs_human_attention`)"
            )

            // 4. Smart backfill from existing chat_history rows
            db.execSQL(
                "INSERT OR IGNORE INTO `conversation_metadata` (" +
                    "`contact_name`, `source_class`, `is_ai_paused`, `needs_human_attention`, " +
                    "`last_notification_key`, `last_message_text`, `last_message_sender_type`, `last_message_timestamp`, `unread_count`" +
                ") SELECT " +
                    "`contact_name`, " +
                    "'BUSINESS' AS `source_class`, " +
                    "0 AS `is_ai_paused`, " +
                    "0 AS `needs_human_attention`, " +
                    "NULL AS `last_notification_key`, " +
                    "`message_text` AS `last_message_text`, " +
                    "'CUSTOMER' AS `last_message_sender_type`, " +
                    "MAX(`timestamp`) AS `last_message_timestamp`, " +
                    "0 AS `unread_count` " +
                "FROM `chat_history` " +
                "GROUP BY `contact_name`"
            )
        }
    }

    /**
     * v9 → v10:
     *  - Extends `orders_table` with `customer_phone`, `total_amount`, `shipping_fee`, `cancellation_reason`, `governorate`.
     *  - Extends `chat_history` with `media_file_path`.
     *  - Creates `contacts_table` with indices for CRM contact management.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Extend orders_table
            db.execSQL("ALTER TABLE `orders_table` ADD COLUMN `customer_phone` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `orders_table` ADD COLUMN `total_amount` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `orders_table` ADD COLUMN `shipping_fee` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `orders_table` ADD COLUMN `cancellation_reason` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `orders_table` ADD COLUMN `governorate` TEXT DEFAULT NULL")

            // 2. Extend chat_history
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `media_file_path` TEXT DEFAULT NULL")

            // 3. Create contacts_table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `contacts_table` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`contact_name` TEXT NOT NULL, " +
                    "`phone_number` TEXT NOT NULL DEFAULT '', " +
                    "`notes` TEXT NOT NULL DEFAULT '', " +
                    "`tag` TEXT NOT NULL DEFAULT 'REGULAR', " +
                    "`total_orders` INTEGER NOT NULL DEFAULT 0, " +
                    "`total_spent` REAL NOT NULL DEFAULT 0.0, " +
                    "`last_active_timestamp` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_table_contact_name` " +
                    "ON `contacts_table` (`contact_name`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contacts_table_phone_number` " +
                    "ON `contacts_table` (`phone_number`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contacts_table_tag` " +
                    "ON `contacts_table` (`tag`)"
            )

            // 4. Backfill contacts_table from existing conversations
            db.execSQL(
                "INSERT OR IGNORE INTO `contacts_table` (`contact_name`, `last_active_timestamp`) " +
                    "SELECT `contact_name`, MAX(`timestamp`) FROM `chat_history` GROUP BY `contact_name`"
            )
        }
    }

    /** All migration paths, registered via `.addMigrations(...)` in DatabaseModule. */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10
    )
}
