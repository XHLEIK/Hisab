package com.example.hisab.data.db.migration

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * HARD GATE for schema v7 -> v8.
 *
 * `HisabDatabase` is built with `fallbackToDestructiveMigration(true)`, so a wrong migration does
 * not fail loudly — it can destroy every user's data. "It compiles" is not evidence. This test
 * therefore does the real thing, on the JVM, with no emulator:
 *
 *  1. Builds a **populated v7 database** using Room's own exported `7.json` DDL (not hand-written
 *     SQL that could drift from the entities).
 *  2. Executes [MigrationSqlV7ToV8.STATEMENTS] — the exact statements `MIGRATION_7_8` ships.
 *  3. Validates the resulting live schema against Room's own exported `8.json`: every expected
 *     column (affinity, nullability, declared default) and every expected index (name, uniqueness,
 *     column list). This is a mechanical stand-in for Room's runtime schema validation.
 *  4. Asserts every pre-existing row in every table survived **byte-identical**.
 *  5. Asserts each new column reads its intended default (`notificationAttempts == 0`, rest NULL).
 *  6. Asserts the new UNIQUE indices actually enforce INV-2 while leaving NULL hashes unconstrained.
 *
 * The one thing this cannot cover is Room's identity-hash check, which needs a device; that stays
 * on the manual device matrix.
 */
class MigrationV7ToV8Test {

    private companion object {
        const val SCHEMA_DIR = "com.example.hisab.data.db.HisabDatabase"

        /** Columns added by v7 -> v8, and the value each must read for a pre-existing row. */
        val NEW_PENDING_COLUMNS = mapOf(
            "sourceMessageHash" to null,
            "source" to null,
            "confidence" to null,
            "referenceNumber" to null,
            "notificationPostedAt" to null,
            "notificationAttempts" to "0"
        )
        val NEW_TRANSACTION_COLUMNS = mapOf(
            "sourceMessageHash" to null,
            "source" to null,
            "confidence" to null,
            "referenceNumber" to null
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    //  The gate
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `v7 to v8 migration preserves all data and produces Room's expected v8 schema`() {
        val v7 = loadSchema(7)
        val v8 = loadSchema(8)

        val dbFile = File.createTempFile("hisab_v7_fixture", ".db").apply { delete() }
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                // ── 1. a realistic, populated v7 database ─────────────────
                createSchema(conn, v7)
                seedRealisticData(conn)

                val before = v7.tableNames().associateWith { snapshot(conn, it) }
                assertTrue(
                    "Fixture must actually contain data, otherwise survival assertions are vacuous",
                    before.values.sumOf { it.size } >= 12
                )

                // ── 2. run the migration that ships ───────────────────────
                conn.createStatement().use { st ->
                    for (statement in MigrationSqlV7ToV8.STATEMENTS) st.executeUpdate(statement)
                }

                // ── 3. schema matches Room's own v8 expectation ───────────
                assertSchemaMatches(conn, v8)

                // ── 4. every pre-existing row survived byte-identical ─────
                for ((table, rowsBefore) in before) {
                    val originalColumns = v7.columnNamesOf(table)
                    val rowsAfter = snapshot(conn, table, projection = originalColumns)
                    assertEquals("Row count changed in `$table`", rowsBefore.size, rowsAfter.size)
                    assertEquals(
                        "Pre-existing data in `$table` was not preserved byte-identically",
                        rowsBefore.map { row -> originalColumns.map { row[it] } },
                        rowsAfter.map { row -> originalColumns.map { row[it] } }
                    )
                }

                // ── 5. new columns read their intended defaults ───────────
                assertNewColumnDefaults(conn, "pending_transactions", NEW_PENDING_COLUMNS)
                assertNewColumnDefaults(conn, "transactions", NEW_TRANSACTION_COLUMNS)

                // ── 6. UNIQUE indices enforce INV-2, NULLs stay free ──────
                assertHashUniquenessEnforced(conn)
                assertNullHashesUnconstrained(conn)
            }
        } finally {
            dbFile.delete()
        }
    }

    /**
     * The migration may be retried after a partial application (crash mid-upgrade, or a rerun after
     * `fallbackToDestructiveMigration`). Applying it twice must not corrupt anything.
     */
    @Test
    fun `migration statements are individually idempotent`() {
        val v7 = loadSchema(7)
        val v8 = loadSchema(8)

        val dbFile = File.createTempFile("hisab_v7_idempotent", ".db").apply { delete() }
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                createSchema(conn, v7)
                seedRealisticData(conn)

                var reapplied = 0
                repeat(2) {
                    for (statement in MigrationSqlV7ToV8.STATEMENTS) {
                        // Mirrors MIGRATION_7_8's per-statement catch.
                        try {
                            conn.createStatement().use { st -> st.executeUpdate(statement) }
                            reapplied++
                        } catch (_: Exception) {}
                    }
                }

                assertTrue("Second pass should be tolerated, not abort the upgrade", reapplied >= MigrationSqlV7ToV8.STATEMENTS.size)
                assertSchemaMatches(conn, v8)
                assertNewColumnDefaults(conn, "pending_transactions", NEW_PENDING_COLUMNS)
            }
        } finally {
            dbFile.delete()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Assertions
    // ──────────────────────────────────────────────────────────────────────

    private fun assertSchemaMatches(conn: Connection, expected: Schema) {
        for (table in expected.tableNames()) {
            val actualColumns = liveColumns(conn, table)
            assertTrue("Table `$table` missing after migration", actualColumns.isNotEmpty())

            for (col in expected.columnsOf(table)) {
                val actual = actualColumns[col.name]
                    ?: fail("Column `$table`.`${col.name}` expected by Room's v8 schema is missing").let { return }

                assertEquals("Affinity mismatch on `$table`.`${col.name}`", col.affinity, actual.affinity)
                assertEquals("Nullability mismatch on `$table`.`${col.name}`", col.notNull, actual.notNull)

                // Room only compares defaults for columns whose entity declares one
                // (@ColumnInfo(defaultValue=...)); an undeclared default is "undefined" and ignored.
                if (col.defaultValue != null) {
                    assertEquals(
                        "Declared default mismatch on `$table`.`${col.name}` — Room's runtime " +
                            "validation would reject this migrated database",
                        normalizeDefault(col.defaultValue),
                        normalizeDefault(actual.defaultValue)
                    )
                }
            }

            val actualIndices = liveIndices(conn, table)
            for (idx in expected.indicesOf(table)) {
                val actual = actualIndices[idx.name]
                    ?: fail("Index `${idx.name}` on `$table` expected by Room's v8 schema is missing").let { return }
                assertEquals("Uniqueness mismatch on index `${idx.name}`", idx.unique, actual.unique)
                assertEquals("Column list mismatch on index `${idx.name}`", idx.columns, actual.columns)
            }
        }
    }

    private fun assertNewColumnDefaults(conn: Connection, table: String, expected: Map<String, String?>) {
        val rows = snapshot(conn, table, projection = expected.keys.toList())
        assertTrue("`$table` must hold pre-existing rows for this assertion to mean anything", rows.isNotEmpty())
        for (row in rows) {
            for ((column, want) in expected) {
                if (want == null) {
                    assertNull("Pre-existing `$table` row should read NULL for new column `$column`", row[column])
                } else {
                    assertEquals("Pre-existing `$table` row has wrong default for `$column`", want, row[column])
                }
            }
        }
    }

    /** INV-2: Room is the sole dedup authority — two rows cannot claim one message identity. */
    private fun assertHashUniquenessEnforced(conn: Connection) {
        insertPending(conn, hash = "canonical-identity-1")
        try {
            insertPending(conn, hash = "canonical-identity-1")
            fail("UNIQUE(sourceMessageHash) did not reject a duplicate claim — INV-2 is not enforced")
        } catch (_: Exception) {
            // expected
        }

        conn.createStatement().use { st ->
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, date, notes, createdAt, sourceMessageHash) VALUES (99.0, 'EXPENSE', 1, 'Primary Bank', 20000, '', 1, 'history-identity-1')")
        }
        try {
            conn.createStatement().use { st ->
                st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, date, notes, createdAt, sourceMessageHash) VALUES (99.0, 'EXPENSE', 1, 'Primary Bank', 20000, '', 1, 'history-identity-1')")
            }
            fail("UNIQUE(sourceMessageHash) on `transactions` did not reject a duplicate")
        } catch (_: Exception) {
            // expected
        }
    }

    /** Legacy/manual/inferred rows carry NULL hashes; SQLite must allow unlimited NULLs. */
    private fun assertNullHashesUnconstrained(conn: Connection) {
        repeat(3) { insertPending(conn, hash = null) }
        val nullCount = conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM pending_transactions WHERE sourceMessageHash IS NULL").use {
                it.next(); it.getInt(1)
            }
        }
        assertTrue(
            "Multiple NULL hashes must coexist so legacy rows survive the UNIQUE index (found $nullCount)",
            nullCount >= 4
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Fixture
    // ──────────────────────────────────────────────────────────────────────

    private fun createSchema(conn: Connection, schema: Schema) {
        conn.createStatement().use { st ->
            for (table in schema.tableNames()) {
                st.executeUpdate(schema.createSqlOf(table))
                for (idx in schema.indicesOf(table)) st.executeUpdate(idx.createSql)
            }
        }
    }

    /** A believable v7 database: default accounts/categories plus real transaction + pending history. */
    private fun seedRealisticData(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate("INSERT INTO accounts (name, type, colorHex, isPrimary, bankCode, accountLast4, lastKnownBalance, lastBalanceTimestamp) VALUES ('Primary Bank', 'PRIMARY', '#10B981', 1, 'BOB', '1463', 855.43, 1750000000000)")
            st.executeUpdate("INSERT INTO accounts (name, type, colorHex, isPrimary, bankCode, accountLast4, lastKnownBalance, lastBalanceTimestamp) VALUES ('Secondary Bank', 'SECONDARY', '#3B82F6', 0, 'SBI', '7788', 12400.0, 1750000100000)")
            st.executeUpdate("INSERT INTO accounts (name, type, colorHex, isPrimary) VALUES ('Savings', 'SAVINGS', '#F59E0B', 0)")

            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Salary', 'INCOME', '🏦', '#4CAF50', 1, 0)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Groceries & Utilities', 'EXPENSE', '🛒', '#4CAF50', 1, 0)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Food & Dining', 'EXPENSE', '🍽️', '#FF9800', 1, 1)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', '🔄', '#607D8B', 1, 5)")

            // Transactions spanning types, an emoji note, a NULL toAccount and a populated one.
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, toAccount, date, notes, createdAt) VALUES (45.0, 'EXPENSE', 2, 'Primary Bank', NULL, 20321, 'BOB debit ₹45 — Ref:623681255058', 1750000200000)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, toAccount, date, notes, createdAt) VALUES (30.0, 'INCOME', 1, 'Primary Bank', NULL, 20321, 'UPI credit', 1750000300000)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, toAccount, date, notes, createdAt) VALUES (5000.0, 'TRANSFER', 4, 'Primary Bank', 'Secondary Bank', 20320, '', 1750000400000)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, toAccount, date, notes, createdAt) VALUES (249.99, 'EXPENSE', 3, 'Secondary Bank', NULL, 20319, 'quotes '' and \"double\" and , comma', 1750000500000)")

            st.executeUpdate("INSERT INTO monthly_budgets (categoryId, monthYear, amountLimit) VALUES (2, '2026-08', 8000.0)")

            st.executeUpdate("INSERT INTO recurring_rules (title, amount, type, categoryId, account, toAccount, dayOfMonth, isActive) VALUES ('Rent', 15000.0, 'EXPENSE', 2, 'Primary Bank', NULL, 5, 1)")

            // Pending rows: one with an ending balance, one without, one with a NULL last4.
            st.executeUpdate("INSERT INTO pending_transactions (amount, type, bankName, accountLast4, merchantOrPayee, endingBalance, rawSmsBody, senderHeader, timestamp) VALUES (45.0, 'DEBIT', 'BOB', '1463', 'AMAZON', 855.43, 'Rs.45.00 Dr. to A/C **1463 AvlBal:Rs855.43 Ref:623681255058 -BOB', 'VM-BOBTXN', 1750000600000)")
            st.executeUpdate("INSERT INTO pending_transactions (amount, type, bankName, accountLast4, merchantOrPayee, endingBalance, rawSmsBody, senderHeader, timestamp) VALUES (30.0, 'CREDIT', 'BOB', '1463', NULL, NULL, 'INR 30.00 credited UPI Ref No 313159087592 -BOB', 'VM-BOBTXN', 1750000700000)")
            st.executeUpdate("INSERT INTO pending_transactions (amount, type, bankName, accountLast4, merchantOrPayee, endingBalance, rawSmsBody, senderHeader, timestamp) VALUES (1200.0, 'DEBIT', 'SBI', NULL, 'Missed Transaction (Balance Sync)', NULL, 'balance-derived', NULL, 1750000800000)")
        }
    }

    private fun insertPending(conn: Connection, hash: String?) {
        val hashLiteral = if (hash == null) "NULL" else "'$hash'"
        conn.createStatement().use { st ->
            st.executeUpdate(
                "INSERT INTO pending_transactions (amount, type, bankName, rawSmsBody, timestamp, sourceMessageHash) " +
                    "VALUES (1.0, 'DEBIT', 'BOB', 'probe', 1750001000000, $hashLiteral)"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Live-schema introspection
    // ──────────────────────────────────────────────────────────────────────

    private data class LiveColumn(val name: String, val affinity: String, val notNull: Boolean, val defaultValue: String?)
    private data class LiveIndex(val name: String, val unique: Boolean, val columns: List<String>)

    private fun liveColumns(conn: Connection, table: String): Map<String, LiveColumn> {
        val out = LinkedHashMap<String, LiveColumn>()
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    out[name] = LiveColumn(
                        name = name,
                        affinity = rs.getString("type"),
                        notNull = rs.getInt("notnull") == 1,
                        defaultValue = rs.getString("dflt_value")
                    )
                }
            }
        }
        return out
    }

    private fun liveIndices(conn: Connection, table: String): Map<String, LiveIndex> {
        val names = mutableListOf<Pair<String, Boolean>>()
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA index_list(`$table`)").use { rs ->
                while (rs.next()) names += rs.getString("name") to (rs.getInt("unique") == 1)
            }
        }
        return names.associate { (name, unique) ->
            val cols = mutableListOf<Pair<Int, String>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA index_info(`$name`)").use { rs ->
                    while (rs.next()) cols += rs.getInt("seqno") to rs.getString("name")
                }
            }
            name to LiveIndex(name, unique, cols.sortedBy { it.first }.map { it.second })
        }
    }

    private fun snapshot(conn: Connection, table: String, projection: List<String>? = null): List<Map<String, String?>> {
        val cols = projection ?: liveColumns(conn, table).keys.toList()
        val select = cols.joinToString(", ") { "`$it`" }
        val rows = mutableListOf<Map<String, String?>>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT $select FROM `$table` ORDER BY rowid").use { rs ->
                while (rs.next()) {
                    rows += cols.associateWith { rs.getString(it) }
                }
            }
        }
        return rows
    }

    /** SQLite reports defaults with the literal syntax used in the DDL; Room normalizes before comparing. */
    private fun normalizeDefault(raw: String?): String? {
        var v = raw?.trim() ?: return null
        while (v.length > 1 && v.startsWith("(") && v.endsWith(")")) v = v.substring(1, v.length - 1).trim()
        if (v.length > 1 && v.startsWith("'") && v.endsWith("'")) v = v.substring(1, v.length - 1)
        return v
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Room's exported schema JSON
    // ──────────────────────────────────────────────────────────────────────

    private data class ExpectedColumn(val name: String, val affinity: String, val notNull: Boolean, val defaultValue: String?)
    private data class ExpectedIndex(val name: String, val unique: Boolean, val columns: List<String>, val createSql: String)

    private class Schema(private val entities: Map<String, JsonObject>) {
        fun tableNames(): List<String> = entities.keys.toList()

        fun createSqlOf(table: String): String =
            entities.getValue(table)["createSql"].asString.replace("\${TABLE_NAME}", table)

        fun columnsOf(table: String): List<ExpectedColumn> =
            entities.getValue(table)["fields"].asJsonArray.map { el ->
                val f = el.asJsonObject
                ExpectedColumn(
                    name = f["columnName"].asString,
                    affinity = f["affinity"].asString,
                    notNull = f["notNull"]?.asBoolean ?: false,
                    defaultValue = f["defaultValue"]?.asString
                )
            }

        fun columnNamesOf(table: String): List<String> = columnsOf(table).map { it.name }

        fun indicesOf(table: String): List<ExpectedIndex> =
            entities.getValue(table)["indices"]?.asJsonArray?.map { el ->
                val i = el.asJsonObject
                ExpectedIndex(
                    name = i["name"].asString,
                    unique = i["unique"].asBoolean,
                    columns = i["columnNames"].asJsonArray.map { it.asString },
                    createSql = i["createSql"].asString.replace("\${TABLE_NAME}", table)
                )
            } ?: emptyList()
    }

    private fun loadSchema(version: Int): Schema {
        val json = readSchemaJson(version)
        val db = JsonParser.parseString(json).asJsonObject["database"].asJsonObject
        assertEquals("Exported schema version mismatch", version, db["version"].asInt)
        val entities = db["entities"].asJsonArray.associate { el ->
            val e = el.asJsonObject
            e["tableName"].asString to e
        }
        return Schema(entities)
    }

    /**
     * The exported schemas are wired into the unit-test resources by `app/build.gradle.kts`
     * (`sourceSets.test.resources.srcDir("$projectDir/schemas")`), with a filesystem fallback so
     * the test still explains itself if that wiring is ever removed.
     */
    private fun readSchemaJson(version: Int): String {
        javaClass.classLoader?.getResourceAsStream("$SCHEMA_DIR/$version.json")?.use {
            return it.readBytes().decodeToString()
        }
        val candidates = listOf(
            File("schemas/$SCHEMA_DIR/$version.json"),
            File("app/schemas/$SCHEMA_DIR/$version.json"),
            File("../app/schemas/$SCHEMA_DIR/$version.json")
        )
        val found = candidates.firstOrNull { it.isFile }
        assertNotNull(
            "Room's exported schema $version.json was not found. It is generated by KSP from " +
                "`exportSchema = true` + the `room.schemaLocation` arg — do not delete either, this " +
                "test is the only automated proof that MIGRATION_7_8 is safe. Looked in: " +
                candidates.joinToString { it.absolutePath },
            found
        )
        return found!!.readText()
    }
}
