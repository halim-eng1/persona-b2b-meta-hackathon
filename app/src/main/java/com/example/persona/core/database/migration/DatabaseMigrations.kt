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
     *
     * Required so devices still running a pre-v8 build upgrade instead of
     * crash-looping: `fallbackToDestructiveMigration()` was removed and only
     * downgrades may wipe, so any pre-8 install needs this explicit path.
     * Both tables were brand-new in v8 (v7 had no such tables), so creating
     * them fresh is the full migration — the DDL mirrors the exported schema
     * JSON (`app/schemas/.../8.json`) exactly.
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
     * Placeholder for the next schema upgrade (v8 → v9).
     *
     * **Must be filled in together with the version bump** in
     * [com.example.persona.core.database.PersonaDatabase] — with the database at
     * version 8 this migration is never invoked, so this is dead wiring until a
     * real post-v8 entity change ships. At that point replace the empty body
     * with the actual DDL, e.g.:
     * ```
     * db.execSQL("ALTER TABLE chat_history ADD COLUMN lastSummarizedAt INTEGER DEFAULT 0 NOT NULL")
     * ```
     *
     * An empty migration body between matching start/end versions is valid only
     * when the exported schema actually does not change.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Future schema alterations for v9 go here.
        }
    }

    /** All migration paths, registered via `.addMigrations(...)` in DatabaseModule. */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_7_8,
        MIGRATION_8_9
    )
}
