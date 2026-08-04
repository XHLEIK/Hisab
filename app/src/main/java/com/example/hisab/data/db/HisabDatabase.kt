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

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        AccountEntity::class,
        PendingTransactionEntity::class
    ],
    version = 4,
    exportSchema = false
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
                CategoryEntity(name = "Salary", type = TransactionType.INCOME, iconName = "AccountBalance", colorHex = "#4CAF50", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Outstation Allowance", type = TransactionType.INCOME, iconName = "Flight", colorHex = "#00BCD4", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Freelance", type = TransactionType.INCOME, iconName = "Laptop", colorHex = "#2196F3", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Gift", type = TransactionType.INCOME, iconName = "CardGiftcard", colorHex = "#E91E63", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Other Income", type = TransactionType.INCOME, iconName = "AddCircle", colorHex = "#607D8B", isDefault = true, sortOrder = 4),

                // ── Expense Categories ───────────────────────
                CategoryEntity(name = "Groceries & Utilities", type = TransactionType.EXPENSE, iconName = "ShoppingCart", colorHex = "#4CAF50", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "Restaurant", colorHex = "#FF9800", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Shopping", type = TransactionType.EXPENSE, iconName = "ShoppingBag", colorHex = "#E91E63", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Transport", type = TransactionType.EXPENSE, iconName = "DirectionsCar", colorHex = "#2196F3", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Bills & Recharges", type = TransactionType.EXPENSE, iconName = "Receipt", colorHex = "#FF5722", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Family", type = TransactionType.EXPENSE, iconName = "People", colorHex = "#795548", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Fitness", type = TransactionType.EXPENSE, iconName = "FitnessCenter", colorHex = "#8BC34A", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "Personal Care", type = TransactionType.EXPENSE, iconName = "LocalHospital", colorHex = "#F44336", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "Entertainment", type = TransactionType.EXPENSE, iconName = "Movie", colorHex = "#9C27B0", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "Education", type = TransactionType.EXPENSE, iconName = "School", colorHex = "#3F51B5", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "Travel", type = TransactionType.EXPENSE, iconName = "Flight", colorHex = "#00ACC1", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "Subscriptions", type = TransactionType.EXPENSE, iconName = "Subscriptions", colorHex = "#673AB7", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "EMI", type = TransactionType.EXPENSE, iconName = "AccountBalance", colorHex = "#D32F2F", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "Other Expense", type = TransactionType.EXPENSE, iconName = "MoreHoriz", colorHex = "#607D8B", isDefault = true, sortOrder = 13),

                // ── Transfer Categories ──────────────────────
                CategoryEntity(name = "Savings", type = TransactionType.TRANSFER, iconName = "Savings", colorHex = "#9C27B0", isDefault = true, sortOrder = 0),
                CategoryEntity(name = "Investment", type = TransactionType.TRANSFER, iconName = "TrendingUp", colorHex = "#4CAF50", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Stocks", type = TransactionType.TRANSFER, iconName = "ShowChart", colorHex = "#2196F3", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Fixed Deposit", type = TransactionType.TRANSFER, iconName = "Lock", colorHex = "#FF9800", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Mutual Funds", type = TransactionType.TRANSFER, iconName = "PieChart", colorHex = "#E91E63", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Other Transfer", type = TransactionType.TRANSFER, iconName = "SwapHoriz", colorHex = "#607D8B", isDefault = true, sortOrder = 5)
            )
            categoryDao.insertAll(defaultCategories)
        }
    }

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add bankCode and accountLast4 columns to accounts table
                db.execSQL("ALTER TABLE accounts ADD COLUMN bankCode TEXT DEFAULT NULL;")
                db.execSQL("ALTER TABLE accounts ADD COLUMN accountLast4 TEXT DEFAULT NULL;")

                // 2. Create pending_transactions table
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

                // 3. Move existing 'Investment' category from INCOME to TRANSFER
                db.execSQL("UPDATE categories SET type = 'TRANSFER', colorHex = '#4CAF50' WHERE name = 'Investment' AND type = 'INCOME';")

                // 4. Update existing transactions associated with 'Investment' category to type TRANSFER
                db.execSQL("""
                    UPDATE transactions 
                    SET type = 'TRANSFER' 
                    WHERE categoryId IN (SELECT id FROM categories WHERE name = 'Investment' AND type = 'TRANSFER');
                """.trimIndent())

                // 5. Insert new default Transfer categories if they don't exist
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Savings', 'TRANSFER', 'Savings', '#9C27B0', 1, 0);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Stocks', 'TRANSFER', 'ShowChart', '#2196F3', 1, 2);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fixed Deposit', 'TRANSFER', 'Lock', '#FF9800', 1, 3);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Mutual Funds', 'TRANSFER', 'PieChart', '#E91E63', 1, 4);")
                db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', 'SwapHoriz', '#607D8B', 1, 5);")
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
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
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
            // Seed accounts
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Primary Bank', 'PRIMARY', '#10B981', 1);")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Secondary Bank', 'SECONDARY', '#3B82F6', 0);")
            db.execSQL("INSERT OR IGNORE INTO accounts (name, type, colorHex, isPrimary) VALUES ('Savings', 'SAVINGS', '#F59E0B', 0);")

            // Seed Income Categories
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Salary', 'INCOME', 'AccountBalance', '#4CAF50', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Outstation Allowance', 'INCOME', 'Flight', '#00BCD4', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Freelance', 'INCOME', 'Laptop', '#00E676', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Gift', 'INCOME', 'CardGiftcard', '#E91E63', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Income', 'INCOME', 'AddCircle', '#607D8B', 1, 4);")

            // Seed Expense Categories
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Groceries & Utilities', 'EXPENSE', 'ShoppingCart', '#4CAF50', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Food & Dining', 'EXPENSE', 'Restaurant', '#FF9800', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Shopping', 'EXPENSE', 'ShoppingBag', '#E91E63', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Transport', 'EXPENSE', 'DirectionsCar', '#2196F3', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Bills & Recharges', 'EXPENSE', 'Receipt', '#FF5722', 1, 4);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Family', 'EXPENSE', 'People', '#795548', 1, 5);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fitness', 'EXPENSE', 'FitnessCenter', '#8BC34A', 1, 6);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Personal Care', 'EXPENSE', 'LocalHospital', '#F44336', 1, 7);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Entertainment', 'EXPENSE', 'Movie', '#9C27B0', 1, 8);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Education', 'EXPENSE', 'School', '#3F51B5', 1, 9);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Travel', 'EXPENSE', 'Flight', '#00ACC1', 1, 10);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Subscriptions', 'EXPENSE', 'Subscriptions', '#673AB7', 1, 11);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('EMI', 'EXPENSE', 'AccountBalance', '#D32F2F', 1, 12);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Expense', 'EXPENSE', 'MoreHoriz', '#607D8B', 1, 13);")

            // Seed Transfer Categories
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Savings', 'TRANSFER', 'Savings', '#9C27B0', 1, 0);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Investment', 'TRANSFER', 'TrendingUp', '#4CAF50', 1, 1);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Stocks', 'TRANSFER', 'ShowChart', '#2196F3', 1, 2);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Fixed Deposit', 'TRANSFER', 'Lock', '#FF9800', 1, 3);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Mutual Funds', 'TRANSFER', 'PieChart', '#E91E63', 1, 4);")
            db.execSQL("INSERT OR IGNORE INTO categories (name, type, iconName, colorHex, isDefault, sortOrder) VALUES ('Other Transfer', 'TRANSFER', 'SwapHoriz', '#607D8B', 1, 5);")
        }
    }
}
