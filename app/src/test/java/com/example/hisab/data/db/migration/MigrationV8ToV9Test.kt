package com.example.hisab.data.db.migration

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class MigrationV8ToV9Test {

    private companion object {
        const val SCHEMA_DIR = "com.example.hisab.data.db.HisabDatabase"
        val NEW_COLUMNS = mapOf("subtype" to null)
    }

    @Test
    fun `v8 to v9 migration preserves all data and produces Room's expected v9 schema`() {
        val v8 = loadSchema(8)
        val v9 = loadSchema(9)

        val dbFile = File.createTempFile("hisab_v8_fixture", ".db").apply { delete() }
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                createSchema(conn, v8)
                seedRealisticData(conn)

                val before = v8.tableNames().associateWith { snapshot(conn, it) }
                assertTrue(before.values.sumOf { it.size } >= 10)

                conn.createStatement().use { st ->
                    for (s in MigrationSqlV8ToV9.STATEMENTS) st.executeUpdate(s)
                }

                assertSchemaMatches(conn, v9)

                for ((table, rowsBefore) in before) {
                    val originalColumns = v8.columnNamesOf(table)
                    val rowsAfter = snapshot(conn, table, projection = originalColumns)
                    assertEquals("Row count changed in `$table`", rowsBefore.size, rowsAfter.size)
                    assertEquals(
                        "Pre-existing data in `$table` was not preserved",
                        rowsBefore.map { row -> originalColumns.map { row[it] } },
                        rowsAfter.map { row -> originalColumns.map { row[it] } }
                    )
                }

                assertNewColumnDefaults(conn, "transactions", NEW_COLUMNS)

                // Split rows should be insertable and queryable
                conn.createStatement().use { st ->
                    st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, date, notes, createdAt, subtype) VALUES (200.0, 'EXPENSE', 2, 'Primary Bank', 20322, 'split test', 1750001000000, 'SPLIT_REIMBURSEMENT')")
                }
                val count = conn.createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM transactions WHERE subtype='SPLIT_REIMBURSEMENT'").use { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(1, count)
            }
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun `migration statements are individually idempotent`() {
        val v8 = loadSchema(8)
        val v9 = loadSchema(9)
        val dbFile = File.createTempFile("hisab_v8_idempotent", ".db").apply { delete() }
        try {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                createSchema(conn, v8)
                seedRealisticData(conn)
                var reapplied = 0
                repeat(2) {
                    for (s in MigrationSqlV8ToV9.STATEMENTS) {
                        try {
                            conn.createStatement().use { st -> st.executeUpdate(s) }
                            reapplied++
                        } catch (_: Exception) {}
                    }
                }
                assertTrue(reapplied >= MigrationSqlV8ToV9.STATEMENTS.size)
                assertSchemaMatches(conn, v9)
                assertNewColumnDefaults(conn, "transactions", NEW_COLUMNS)
            }
        } finally {
            dbFile.delete()
        }
    }

    private fun assertSchemaMatches(conn: Connection, expected: Schema) {
        for (table in expected.tableNames()) {
            val actualColumns = liveColumns(conn, table)
            assertTrue("Table `$table` missing after migration", actualColumns.isNotEmpty())
            for (col in expected.columnsOf(table)) {
                val actual = actualColumns[col.name] ?: fail("Column `$table`.`${col.name}` missing").let { return }
                assertEquals("Affinity mismatch on `$table`.`${col.name}`", col.affinity, actual.affinity)
                assertEquals("Nullability mismatch on `$table`.`${col.name}`", col.notNull, actual.notNull)
                if (col.defaultValue != null) {
                    assertEquals(
                        "Declared default mismatch on `$table`.`${col.name}`",
                        normalizeDefault(col.defaultValue),
                        normalizeDefault(actual.defaultValue)
                    )
                }
            }
            val actualIndices = liveIndices(conn, table)
            for (idx in expected.indicesOf(table)) {
                val actual = actualIndices[idx.name] ?: fail("Index `${idx.name}` on `$table` missing").let { return }
                assertEquals("Uniqueness mismatch on index `${idx.name}`", idx.unique, actual.unique)
                assertEquals("Column list mismatch on index `${idx.name}`", idx.columns, actual.columns)
            }
        }
    }

    private fun assertNewColumnDefaults(conn: Connection, table: String, expected: Map<String, String?>) {
        val rows = snapshot(conn, table, projection = expected.keys.toList())
        assertTrue("`$table` must hold pre-existing rows", rows.isNotEmpty())
        for (row in rows) {
            for ((column, want) in expected) {
                if (want == null) assertNull("Pre-existing `$table` row should read NULL for new column `$column`", row[column])
                else assertEquals("Pre-existing `$table` row has wrong default for `$column`", want, row[column])
            }
        }
    }

    private fun createSchema(conn: Connection, schema: Schema) {
        conn.createStatement().use { st ->
            for (table in schema.tableNames()) {
                st.executeUpdate(schema.createSqlOf(table))
                for (idx in schema.indicesOf(table)) st.executeUpdate(idx.createSql)
            }
        }
    }

    private fun seedRealisticData(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate("INSERT INTO accounts (name, type, colorHex, isPrimary, bankCode, accountLast4, lastKnownBalance, lastBalanceTimestamp) VALUES ('Primary Bank', 'PRIMARY', '#10B981', 1, 'BOB', '1463', 855.43, 1750000000000)")
            st.executeUpdate("INSERT INTO accounts (name, type, colorHex, isPrimary) VALUES ('Savings', 'SAVINGS', '#F59E0B', 0)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Salary', 'INCOME', '🏦', '#4CAF50', 1, 0)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Groceries & Utilities', 'EXPENSE', '🛒', '#4CAF50', 1, 0)")
            st.executeUpdate("INSERT INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', '🔄', '#607D8B', 1, 5)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, date, notes, createdAt) VALUES (45.0, 'EXPENSE', 2, 'Primary Bank', 20321, 'BOB debit', 1750000200000)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, date, notes, createdAt) VALUES (30.0, 'INCOME', 1, 'Primary Bank', 20321, 'UPI credit', 1750000300000)")
            st.executeUpdate("INSERT INTO transactions (amount, type, categoryId, account, toAccount, date, notes, createdAt) VALUES (5000.0, 'TRANSFER', 3, 'Primary Bank', 'Savings', 20320, '', 1750000400000)")
            st.executeUpdate("INSERT INTO monthly_budgets (categoryId, monthYear, amountLimit) VALUES (2, '2026-08', 8000.0)")
            st.executeUpdate("INSERT INTO recurring_rules (title, amount, type, categoryId, account, toAccount, dayOfMonth, isActive) VALUES ('Rent', 15000.0, 'EXPENSE', 2, 'Primary Bank', NULL, 5, 1)")
            st.executeUpdate("INSERT INTO pending_transactions (amount, type, bankName, rawSmsBody, timestamp) VALUES (45.0, 'DEBIT', 'BOB', 'Rs.45 Dr', 1750000600000)")
        }
    }

    private data class LiveColumn(val name: String, val affinity: String, val notNull: Boolean, val defaultValue: String?)
    private data class LiveIndex(val name: String, val unique: Boolean, val columns: List<String>)

    private fun liveColumns(conn: Connection, table: String): Map<String, LiveColumn> {
        val out = LinkedHashMap<String, LiveColumn>()
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    out[name] = LiveColumn(name, rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"))
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
                while (rs.next()) rows += cols.associateWith { rs.getString(it) }
            }
        }
        return rows
    }

    private fun normalizeDefault(raw: String?): String? {
        var v = raw?.trim() ?: return null
        while (v.length > 1 && v.startsWith("(") && v.endsWith(")")) v = v.substring(1, v.length - 1).trim()
        if (v.length > 1 && v.startsWith("'") && v.endsWith("'")) v = v.substring(1, v.length - 1)
        return v
    }

    private data class ExpectedColumn(val name: String, val affinity: String, val notNull: Boolean, val defaultValue: String?)
    private data class ExpectedIndex(val name: String, val unique: Boolean, val columns: List<String>, val createSql: String)

    private class Schema(private val entities: Map<String, JsonObject>) {
        fun tableNames(): List<String> = entities.keys.toList()
        fun createSqlOf(table: String): String = entities.getValue(table)["createSql"].asString.replace("\${TABLE_NAME}", table)
        fun columnsOf(table: String): List<ExpectedColumn> = entities.getValue(table)["fields"].asJsonArray.map { el ->
            val f = el.asJsonObject
            ExpectedColumn(f["columnName"].asString, f["affinity"].asString, f["notNull"]?.asBoolean ?: false, f["defaultValue"]?.asString)
        }
        fun columnNamesOf(table: String): List<String> = columnsOf(table).map { it.name }
        fun indicesOf(table: String): List<ExpectedIndex> = entities.getValue(table)["indices"]?.asJsonArray?.map { el ->
            val i = el.asJsonObject
            ExpectedIndex(i["name"].asString, i["unique"].asBoolean, i["columnNames"].asJsonArray.map { it.asString }, i["createSql"].asString.replace("\${TABLE_NAME}", table))
        } ?: emptyList()
    }

    private fun loadSchema(version: Int): Schema {
        val json = readSchemaJson(version)
        val db = JsonParser.parseString(json).asJsonObject["database"].asJsonObject
        assertEquals(version, db["version"].asInt)
        val entities = db["entities"].asJsonArray.associate { el ->
            val e = el.asJsonObject
            e["tableName"].asString to e
        }
        return Schema(entities)
    }

    private fun readSchemaJson(version: Int): String {
        javaClass.classLoader?.getResourceAsStream("$SCHEMA_DIR/$version.json")?.use { return it.readBytes().decodeToString() }
        val candidates = listOf(File("schemas/$SCHEMA_DIR/$version.json"), File("app/schemas/$SCHEMA_DIR/$version.json"), File("../app/schemas/$SCHEMA_DIR/$version.json"))
        val found = candidates.firstOrNull { it.isFile }
        assertNotNull("Room's exported schema $version.json was not found. Looked in: ${candidates.joinToString { it.absolutePath }}", found)
        return found!!.readText()
    }
}
