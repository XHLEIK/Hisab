package com.example.hisab.data.db.migration

object MigrationSqlV8ToV9 {

    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE transactions ADD COLUMN subtype TEXT DEFAULT NULL;"
    )
}
