package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccountDao
import com.example.data.dao.AppSettingDao
import com.example.data.dao.AuditEventDao
import com.example.data.dao.CategoryDao
import com.example.data.dao.InternalTransferDao
import com.example.data.dao.JournalDao
import com.example.data.dao.ReconciliationDiscrepancyDao
import com.example.data.dao.SmsLogDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.TransactionRelationshipDao
import com.example.data.model.Account
import com.example.data.model.AppSetting
import com.example.data.model.AuditEvent
import com.example.data.model.Category
import com.example.data.model.InternalTransfer
import com.example.data.model.JournalEntry
import com.example.data.model.JournalLine
import com.example.data.model.ReconciliationDiscrepancy
import com.example.data.model.SmsLog
import com.example.data.model.Transaction
import com.example.data.model.TransactionRelationship

@Database(
    entities = [
        Account::class,
        Transaction::class,
        JournalEntry::class,
        JournalLine::class,
        TransactionRelationship::class,
        AuditEvent::class,
        ReconciliationDiscrepancy::class,
        Category::class,
        SmsLog::class,
        InternalTransfer::class,
        AppSetting::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun journalDao(): JournalDao
    abstract fun transactionRelationshipDao(): TransactionRelationshipDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun reconciliationDiscrepancyDao(): ReconciliationDiscrepancyDao
    abstract fun categoryDao(): CategoryDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun internalTransferDao(): InternalTransferDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create journal entries and lines
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER,
                        `entryDate` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourceReference` TEXT,
                        `postedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_transactionId` ON `journal_entries` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_entryDate` ON `journal_entries` (`entryDate`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_lines` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `journalEntryId` INTEGER NOT NULL,
                        `accountId` INTEGER NOT NULL,
                        `accountCode` TEXT NOT NULL,
                        `debitMinor` INTEGER NOT NULL,
                        `creditMinor` INTEGER NOT NULL,
                        `description` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_journalEntryId` ON `journal_lines` (`journalEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_accountId` ON `journal_lines` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_accountCode` ON `journal_lines` (`accountCode`)")

                // 2. Create transaction relationships
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_relationships` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `relationshipType` TEXT NOT NULL,
                        `sourceTransactionId` INTEGER NOT NULL,
                        `targetTransactionId` INTEGER,
                        `matchingEvidence` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_relationships_sourceTransactionId` ON `transaction_relationships` (`sourceTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_relationships_targetTransactionId` ON `transaction_relationships` (`targetTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_relationships_relationshipType` ON `transaction_relationships` (`relationshipType`)")

                // 3. Create audit events
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `recordTable` TEXT NOT NULL,
                        `recordId` INTEGER,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_timestamp` ON `audit_events` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_eventType` ON `audit_events` (`eventType`)")

                // 4. Create reconciliation discrepancies
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reconciliation_discrepancies` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `reportedBalanceMinor` INTEGER NOT NULL,
                        `calculatedBalanceMinor` INTEGER NOT NULL,
                        `discrepancyMinor` INTEGER NOT NULL,
                        `transactionReference` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `isResolved` INTEGER NOT NULL,
                        `resolutionNotes` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliation_discrepancies_provider` ON `reconciliation_discrepancies` (`provider`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliation_discrepancies_isResolved` ON `reconciliation_discrepancies` (`isResolved`)")

                // 5. Migrate accounts to include minor units and accounting fields
                try {
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountCode` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("UPDATE `accounts` SET `accountCode` = `provider`")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountType` TEXT NOT NULL DEFAULT 'ASSET'")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `openingBalanceMinor` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `accounts` SET `openingBalanceMinor` = CAST(ROUND(`openingBalance` * 100) AS INTEGER)")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `lastReportedBalanceMinor` INTEGER")
                    db.execSQL("UPDATE `accounts` SET `lastReportedBalanceMinor` = CAST(ROUND(`lastReportedBalance` * 100) AS INTEGER) WHERE `lastReportedBalance` IS NOT NULL")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isReconciled` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `reconciledAt` INTEGER")
                } catch (_: Exception) {
                }

                // 6. Migrate transactions to include minor units
                try {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `amountMinor` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `transactions` SET `amountMinor` = CAST(ROUND(`amount` * 100) AS INTEGER)")
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `feeMinor` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `transactions` SET `feeMinor` = CAST(ROUND(`fee` * 100) AS INTEGER)")
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `balanceAfterMinor` INTEGER")
                    db.execSQL("UPDATE `transactions` SET `balanceAfterMinor` = CAST(ROUND(`balanceAfter` * 100) AS INTEGER) WHERE `balanceAfter` IS NOT NULL")
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reviewReason` TEXT")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_needsReview` ON `transactions` (`needsReview`)")
                } catch (_: Exception) {
                }

                // 7. Migrate internal transfers to include minor units
                try {
                    db.execSQL("ALTER TABLE `internal_transfers` ADD COLUMN `amountMinor` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `internal_transfers` SET `amountMinor` = CAST(ROUND(`amount` * 100) AS INTEGER)")
                } catch (_: Exception) {
                }
            }
        }

        fun getDatabase(context: Context, scope: kotlinx.coroutines.CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "echo_money.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun checkpoint(database: AppDatabase) {
            try {
                val db = database.openHelper.writableDatabase
                val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                try {
                    cursor.moveToFirst()
                } finally {
                    cursor.close()
                }
            } catch (_: Exception) {
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        checkpoint(db)
                        db.close()
                    } catch (_: Throwable) {
                    }
                }
                INSTANCE = null
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                // Default Chart of Accounts
                db.execSQL("INSERT OR IGNORE INTO accounts (provider, accountCode, accountName, accountType, openingBalanceMinor, createdAt) VALUES ('AIRTEL', 'ASSET_AIRTEL', 'Airtel Money Zambia', 'ASSET', 0, $now)")
                db.execSQL("INSERT OR IGNORE INTO accounts (provider, accountCode, accountName, accountType, openingBalanceMinor, createdAt) VALUES ('MTN', 'ASSET_MTN', 'MTN Mobile Money Zambia', 'ASSET', 0, $now)")
                db.execSQL("INSERT OR IGNORE INTO accounts (provider, accountCode, accountName, accountType, openingBalanceMinor, createdAt) VALUES ('CASH', 'ASSET_CASH', 'Cash in Hand', 'ASSET', 0, $now)")

                // Standard Categories
                val categories = listOf(
                    "('Received Money', 'INCOME', 1, 'arrow_downward')",
                    "('Salary & Wages', 'INCOME', 1, 'payments')",
                    "('Business Income', 'INCOME', 1, 'store')",
                    "('Person-to-Person Transfer', 'EXPENSE', 1, 'send')",
                    "('Merchant Payment', 'EXPENSE', 1, 'shopping_cart')",
                    "('Internet & Data Bundles', 'EXPENSE', 1, 'wifi')",
                    "('Airtime', 'EXPENSE', 1, 'phone_android')",
                    "('Cash Withdrawal', 'EXPENSE', 1, 'atm')",
                    "('Utility Bills', 'EXPENSE', 1, 'receipt_long')",
                    "('Internal Account Transfer', 'SPECIAL', 1, 'sync_alt')",
                    "('Transaction Fees', 'EXPENSE', 1, 'money_off')",
                    "('General Expense', 'EXPENSE', 1, 'account_balance_wallet')"
                )
                for (cat in categories) {
                    db.execSQL("INSERT OR IGNORE INTO categories (name, categoryType, isDefault, icon) VALUES $cat")
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val accountDao = database.accountDao()
            val categoryDao = database.categoryDao()

            val defaultAccounts = listOf(
                Account(provider = "AIRTEL", accountName = "Airtel Money Zambia", openingBalance = 0.0),
                Account(provider = "MTN", accountName = "MTN Mobile Money Zambia", openingBalance = 0.0),
                Account(provider = "CASH", accountName = "Cash in Hand", openingBalance = 0.0)
            )
            accountDao.insertAccounts(defaultAccounts)

            val defaultCategories = listOf(
                Category(name = "Received Money", categoryType = "INCOME", icon = "arrow_downward"),
                Category(name = "Salary & Wages", categoryType = "INCOME", icon = "payments"),
                Category(name = "Business Income", categoryType = "INCOME", icon = "store"),
                Category(name = "Person-to-Person Transfer", categoryType = "EXPENSE", icon = "send"),
                Category(name = "Merchant Payment", categoryType = "EXPENSE", icon = "shopping_cart"),
                Category(name = "Internet & Data Bundles", categoryType = "EXPENSE", icon = "wifi"),
                Category(name = "Airtime", categoryType = "EXPENSE", icon = "phone_android"),
                Category(name = "Cash Withdrawal", categoryType = "EXPENSE", icon = "atm"),
                Category(name = "Utility Bills", categoryType = "EXPENSE", icon = "receipt_long"),
                Category(name = "Internal Account Transfer", categoryType = "SPECIAL", icon = "sync_alt"),
                Category(name = "Transaction Fees", categoryType = "EXPENSE", icon = "money_off"),
                Category(name = "General Expense", categoryType = "EXPENSE", icon = "account_balance_wallet")
            )
            categoryDao.insertCategories(defaultCategories)
        }
    }
}
