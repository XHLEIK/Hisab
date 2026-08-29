package com.example.hisab.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.hisab.data.db.dao.AccountDao
import com.example.hisab.data.db.dao.BudgetDao
import com.example.hisab.data.db.dao.CategoryDao
import com.example.hisab.data.db.dao.RecurringRuleDao
import com.example.hisab.data.db.dao.TransactionDao
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.BudgetEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.db.entity.RecurringRuleEntity
import com.example.hisab.data.db.entity.TransactionEntity
import com.example.hisab.data.model.TransactionType

import androidx.room.migration.Migration

import com.example.hisab.data.db.dao.PendingTransactionDao
import com.example.hisab.data.db.entity.PendingTransactionEntity
import com.example.hisab.data.db.migration.MigrationSqlV7ToV8
import com.example.hisab.data.db.migration.MigrationSqlV8ToV9

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        AccountEntity::class,
        PendingTransactionEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HisabDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun accountDao(): AccountDao
    abstract fun pendingTransactionDao(): PendingTransactionDao

    suspend fun ensureDefaults() {
        val accountDao = accountDao()
        val categoryDao = categoryDao()

        if (accountDao.getCount() == 0) {
            val defaultAccounts = listOf(
                AccountEntity(name = "Primary Bank", type = "PRIMARY", colorHex = "#10B981", isPrimary = true),
                AccountEntity(name = "Secondary Bank", type = "SECONDARY", colorHex = "#3B82F6", isPrimary = false),
                AccountEntity(name = "Savings", type = "SAVINGS", colorHex = "#F59E0B", isPrimary = false)
            )
            accountDao.insertAll(defaultAccounts)
        }

        if (categoryDao.getCount() == 0) {
            val defaultCategories = listOf(
                // ── Income Categories ────────────────────────
                CategoryEntity(name = "Salary", type = TransactionType.INCOME, iconName = "🏦", colorHex = "#4CAF50", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Outstation Allowance", type = TransactionType.INCOME, iconName = "✈️", colorHex = "#00BCD4", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Freelance", type = TransactionType.INCOME, iconName = "💻", colorHex = "#2196F3", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Gift", type = TransactionType.INCOME, iconName = "🎁", colorHex = "#E91E63", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Other Income", type = TransactionType.INCOME, iconName = "➕", colorHex = "#607D8B", isDefault = true, sortOrder = 4),

                // ── Expense Categories ───────────────────────
                CategoryEntity(name = "Groceries & Utilities", type = TransactionType.EXPENSE, iconName = "🛒", colorHex = "#4CAF50", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "🍽️", colorHex = "#FF9800", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Shopping", type = TransactionType.EXPENSE, iconName = "🛍️", colorHex = "#E91E63", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Transport", type = TransactionType.EXPENSE, iconName = "🚗", colorHex = "#2196F3", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Bills & Recharges", type = TransactionType.EXPENSE, iconName = "🧾", colorHex = "#FF5722", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Family", type = TransactionType.EXPENSE, iconName = "👥", colorHex = "#795548", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Fitness", type = TransactionType.EXPENSE, iconName = "💪", colorHex = "#8BC34A", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "Personal Care", type = TransactionType.EXPENSE, iconName = "🏥", colorHex = "#F44336", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "Entertainment", type = TransactionType.EXPENSE, iconName = "🎬", colorHex = "#9C27B0", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "Education", type = TransactionType.EXPENSE, iconName = "🎓", colorHex = "#3F51B5", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "Travel", type = TransactionType.EXPENSE, iconName = "✈️", colorHex = "#00ACC1", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "Subscriptions", type = TransactionType.EXPENSE, iconName = "📱", colorHex = "#673AB7", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "EMI", type = TransactionType.EXPENSE, iconName = "🏦", colorHex = "#D32F2F", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "Other Expense", type = TransactionType.EXPENSE, iconName = "📌", colorHex = "#607D8B", isDefault = true, sortOrder = 13),

                // ── Transfer Categories ──────────────────────
                CategoryEntity(name = "Savings", type = TransactionType.TRANSFER, iconName = "🐷", colorHex = "#9C27B0", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Investment", type = TransactionType.TRANSFER, iconName = "📊", colorHex = "#4CAF50", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Stocks", type = TransactionType.TRANSFER, iconName = "📈", colorHex = "#2196F3", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Fixed Deposit", type = TransactionType.TRANSFER, iconName = "🔒", colorHex = "#FF9800", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Mutual Funds", type = TransactionType.TRANSFER, iconName = "🥧", colorHex = "#E91E63", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Other Transfer", type = TransactionType.TRANSFER, iconName = "🔄", colorHex = "#607D8B", isDefault = true, sortOrder = 5)
            )
            categoryDao.insertAll(defaultCategories)
        }
    }

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN bankCode TEXT DEFAULT NULL;")
                db.execSQL("ALTER TABLE accounts ADD COLUMN accountLast4 TEXT DEFAULT NULL;")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        bankName TEXT NOT NULL,
                        accountLast4 TEXT,
                        merchantOrPayee TEXT,
                        rawSmsBody TEXT NOT NULL,
                        senderHeader TEXT,
                        timestamp INTEGER NOT NULL
                    );
                """.trimIndent())

                db.execSQL("UPDATE categories SET type = 'TRANSFER', colorHex = '#4CAF50' WHERE name = 'Investment' AND type = 'INCOME';")

                db.execSQL("""
                    UPDATE transactions 
                    SET type = 'TRANSFER' 
                    WHERE categoryId IN (SELECT id FROM categories WHERE name = 'Investment' AND type = 'TRANSFER');
                """.trimIndent())

                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Savings', 'TRANSFER', '🐷', '#9C27B0', 1, 0);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Stocks', 'TRANSFER', '📈', '#2196F3', 1, 2);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fixed Deposit', 'TRANSFER', '🔒', '#FF9800', 1, 3);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Mutual Funds', 'TRANSFER', '🥧', '#E91E63', 1, 4);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', '🔄', '#607D8B', 1, 5);")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE accounts ADD COLUMN bankCode TEXT DEFAULT NULL;") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE accounts ADD COLUMN accountLast4 TEXT DEFAULT NULL;") } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        bankName TEXT NOT NULL,
                        accountLast4 TEXT,
                        merchantOrPayee TEXT,
                        rawSmsBody TEXT NOT NULL,
                        senderHeader TEXT,
                        timestamp INTEGER NOT NULL
                    );
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE pending_transactions ADD COLUMN endingBalance REAL DEFAULT NULL;")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE accounts ADD COLUMN lastKnownBalance REAL DEFAULT NULL;")
                    db.execSQL("ALTER TABLE accounts ADD COLUMN lastBalanceTimestamp INTEGER DEFAULT NULL;")
                } catch (_: Exception) {}

                // Migrate legacy icon names to direct Unicode emojis
                val iconEmojiMap = mapOf(
                    "AccountBalance" to "🏦",
                    "Flight" to "✈️",
                    "Laptop" to "💻",
                    "CardGiftcard" to "🎁",
                    "AddCircle" to "➕",
                    "ShoppingCart" to "🛒",
                    "Restaurant" to "🍽️",
                    "ShoppingBag" to "🛍️",
                    "DirectionsCar" to "🚗",
                    "Receipt" to "🧾",
                    "People" to "👥",
                    "FitnessCenter" to "💪",
                    "LocalHospital" to "🏥",
                    "Movie" to "🎬",
                    "School" to "🎓",
                    "Subscriptions" to "📱",
                    "MoreHoriz" to "📌",
                    "Savings" to "🐷",
                    "TrendingUp" to "📊",
                    "ShowChart" to "📈",
                    "Lock" to "🔒",
                    "PieChart" to "🥧",
                    "SwapHoriz" to "🔄",
                    "Coffee" to "☕",
                    "Fastfood" to "🍔",
                    "TwoWheeler" to "🏍️",
                    "LocalGasStation" to "⛽",
                    "DirectionsBus" to "🚌",
                    "Wifi" to "📶",
                    "ElectricalServices" to "⚡",
                    "WaterDrop" to "💧",
                    "Work" to "💼",
                    "Payments" to "💵",
                    "CreditCard" to "💳",
                    "AccountBalanceWallet" to "👛",
                    "AutoGraph" to "✨",
                    "ChildCare" to "👶",
                    "Pets" to "🐾",
                    "Build" to "🔧"
                )

                for ((icon, emoji) in iconEmojiMap) {
                    db.execSQL("UPDATE categories SET iconName = '$emoji' WHERE iconName = '$icon';")
                }
            }
        }

        /**
         * Schema v8: provenance + identity columns on both transaction tables.
         *
         * Statements live in [MigrationSqlV7ToV8] so `MigrationV7ToV8Test` proves this exact SQL
         * against a populated v7 fixture. Per-statement catches match the defensive style of
         * MIGRATION_6_7 and keep a partially-applied retry from aborting the whole upgrade —
         * every statement is individually idempotent.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (statement in MigrationSqlV7ToV8.STATEMENTS) {
                    try {
                        db.execSQL(statement)
                    } catch (_: Exception) {}
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (statement in MigrationSqlV8ToV9.STATEMENTS) {
                    try {
                        db.execSQL(statement)
                    } catch (_: Exception) {}
                }
            }
        }

        @Volatile
        private var INSTANCE: HisabDatabase? = null

        fun getDatabase(context: Context): HisabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HisabDatabase::class.java,
                    "hisab_database"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(SeedDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedDatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Primary Bank', 'PRIMARY', '#10B981', 1);")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Secondary Bank', 'SECONDARY', '#3B82F6', 0);")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Savings', 'SAVINGS', '#F59E0B', 0);")

            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Salary', 'INCOME', '🏦', '#4CAF50', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Outstation Allowance', 'INCOME', '✈️', '#00BCD4', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Freelance', 'INCOME', '💻', '#00E676', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Gift', 'INCOME', '🎁', '#E91E63', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Income', 'INCOME', '➕', '#607D8B', 1, 4);")

            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Groceries & Utilities', 'EXPENSE', '🛒', '#4CAF50', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Food & Dining', 'EXPENSE', '🍽️', '#FF9800', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Shopping', 'EXPENSE', '🛍️', '#E91E63', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Transport', 'EXPENSE', '🚗', '#2196F3', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Bills & Recharges', 'EXPENSE', '🧾', '#FF5722', 1, 4);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Family', 'EXPENSE', '👥', '#795548', 1, 5);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fitness', 'EXPENSE', '💪', '#8BC34A', 1, 6);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Personal Care', 'EXPENSE', '🏥', '#F44336', 1, 7);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Entertainment', 'EXPENSE', '🎬', '#9C27B0', 1, 8);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Education', 'EXPENSE', '🎓', '#3F51B5', 1, 9);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Travel', 'EXPENSE', '✈️', '#00ACC1', 1, 10);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Subscriptions', 'EXPENSE', '📱', '#673AB7', 1, 11);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('EMI', 'EXPENSE', '🏦', '#D32F2F', 1, 12);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Expense', 'EXPENSE', '📌', '#607D8B', 1, 13);")

            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Savings', 'TRANSFER', '🐷', '#9C27B0', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Investment', 'TRANSFER', '📊', '#4CAF50', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Stocks', 'TRANSFER', '📈', '#2196F3', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fixed Deposit', 'TRANSFER', '🔒', '#FF9800', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Mutual Funds', 'TRANSFER', '🥧', '#E91E63', 1, 4);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', '🔄', '#607D8B', 1, 5);")
        }
    }
}
