package com.example.hisab.data.db.migration

/**
 * The v7 -> v8 migration statements, held as data rather than inline in the [androidx.room.migration.Migration]
 * body so that the production migration and its proof test execute *byte-identical* SQL.
 *
 * `HisabDatabase` is built with `fallbackToDestructiveMigration(true)`, which makes a mistake here
 * unrecoverable for real users. `MigrationV7ToV8Test` therefore runs these exact statements against
 * a populated v7 fixture on a pure-JVM SQLite driver and asserts the result matches Room's own
 * generated `schemas/.../8.json` — no device required, and no hand-written schema expectation that
 * could drift from the entities.
 *
 * Every statement must be idempotent (`IF NOT EXISTS`, or guarded by the caller's per-statement
 * catch) because a partially-applied upgrade may be retried.
 */
object MigrationSqlV7ToV8 {

    /**
     * Provenance + notification-bookkeeping columns, and the UNIQUE indices that make Room the sole
     * dedup authority (INV-2).
     *
     * Historic rows are deliberately left with `sourceMessageHash = NULL`: SQLite permits unlimited
     * NULLs in a UNIQUE index, so nothing collides. Backfilling hashes for old rows is prohibited —
     * it would invent identities for messages that were never claimed.
     */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE pending_transactions ADD COLUMN sourceMessageHash TEXT DEFAULT NULL;",
        "ALTER TABLE pending_transactions ADD COLUMN source TEXT DEFAULT NULL;",
        "ALTER TABLE pending_transactions ADD COLUMN confidence TEXT DEFAULT NULL;",
        "ALTER TABLE pending_transactions ADD COLUMN referenceNumber TEXT DEFAULT NULL;",
        "ALTER TABLE pending_transactions ADD COLUMN notificationPostedAt INTEGER DEFAULT NULL;",
        "ALTER TABLE pending_transactions ADD COLUMN notificationAttempts INTEGER NOT NULL DEFAULT 0;",
        "ALTER TABLE transactions ADD COLUMN sourceMessageHash TEXT DEFAULT NULL;",
        "ALTER TABLE transactions ADD COLUMN source TEXT DEFAULT NULL;",
        "ALTER TABLE transactions ADD COLUMN confidence TEXT DEFAULT NULL;",
        "ALTER TABLE transactions ADD COLUMN referenceNumber TEXT DEFAULT NULL;",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_transactions_sourceMessageHash " +
            "ON pending_transactions(sourceMessageHash);",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceMessageHash " +
            "ON transactions(sourceMessageHash);"
    )
}
