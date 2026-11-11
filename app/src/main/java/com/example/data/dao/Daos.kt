package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccountsFlow(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllAccounts(): List<Account>

    @Query("SELECT * FROM accounts WHERE provider = :provider LIMIT 1")
    suspend fun getAccountByProvider(provider: String): Account?

    @Query("SELECT * FROM accounts WHERE accountCode = :code LIMIT 1")
    suspend fun getAccountByCode(code: String): Account?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<Account>)

    @Update
    suspend fun updateAccount(account: Account)

    @Query("UPDATE accounts SET lastReportedBalanceMinor = :balanceMinor, lastBalanceUpdate = :timestamp WHERE provider = :provider")
    suspend fun updateLastReportedBalanceMinor(provider: String, balanceMinor: Long, timestamp: Long)

    @Query("UPDATE accounts SET openingBalanceMinor = :balanceMinor WHERE provider = :provider")
    suspend fun updateOpeningBalanceMinor(provider: String, balanceMinor: Long)

    suspend fun updateLastReportedBalance(provider: String, balance: Double, timestamp: Long) {
        updateLastReportedBalanceMinor(provider, com.example.data.model.Money.fromZmw(balance), timestamp)
    }

    suspend fun updateOpeningBalance(provider: String, balance: Double) {
        updateOpeningBalanceMinor(provider, com.example.data.model.Money.fromZmw(balance))
    }
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionDate BETWEEN :startDate AND :endDate ORDER BY transactionDate DESC")
    fun getTransactionsBetweenFlow(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE provider = :provider AND transactionReference = :reference LIMIT 1")
    suspend fun getTransactionByReference(provider: String, reference: String): Transaction?

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionDate DESC")
    fun getTransactionsNeedingReviewFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionDate DESC")
    suspend fun getTransactionsNeedingReview(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE provider = :provider AND amountMinor = :amountMinor AND transactionDate BETWEEN :startTime AND :endTime LIMIT 1")
    suspend fun findPotentialDuplicate(provider: String, amountMinor: Long, startTime: Long, endTime: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE provider = :oppositeProvider AND amountMinor = :amountMinor AND isInternalTransfer = 0 AND transactionDate BETWEEN :startTime AND :endTime LIMIT 1")
    suspend fun findPotentialOppositeTransfer(oppositeProvider: String, amountMinor: Long, startTime: Long, endTime: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE provider = :oppositeProvider AND (transactionReference = :reference OR description LIKE '%' || :reference || '%') LIMIT 1")
    suspend fun findMatchingTransferByReferenceOrNote(oppositeProvider: String, reference: String): Transaction?

    @Query("SELECT * FROM transactions WHERE provider = :provider AND (transactionReference = :reference OR id = :id) LIMIT 1")
    suspend fun findTransactionForReversal(provider: String, reference: String, id: Long = -1L): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalLines(lines: List<JournalLine>)

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC")
    fun getAllJournalEntriesFlow(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :entryId")
    suspend fun getJournalLinesForEntry(entryId: Long): List<JournalLine>

    @Query("SELECT * FROM journal_lines WHERE accountId = :accountId")
    suspend fun getJournalLinesForAccount(accountId: Long): List<JournalLine>

    @Query("SELECT SUM(debitMinor) FROM journal_lines WHERE accountId = :accountId")
    suspend fun getSumDebitsForAccount(accountId: Long): Long?

    @Query("SELECT SUM(creditMinor) FROM journal_lines WHERE accountId = :accountId")
    suspend fun getSumCreditsForAccount(accountId: Long): Long?

    @Query("SELECT * FROM journal_entries WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getJournalEntryForTransaction(transactionId: Long): JournalEntry?

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun getJournalEntryCount(): Int

    @Query("DELETE FROM journal_entries WHERE transactionId = :transactionId")
    suspend fun deleteJournalForTransaction(transactionId: Long)
}

@Dao
interface TransactionRelationshipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(rel: TransactionRelationship): Long

    @Query("SELECT * FROM transaction_relationships WHERE sourceTransactionId = :txnId OR targetTransactionId = :txnId")
    suspend fun getRelationshipsForTransaction(txnId: Long): List<TransactionRelationship>

    @Query("SELECT * FROM transaction_relationships ORDER BY createdAt DESC")
    fun getAllRelationshipsFlow(): Flow<List<TransactionRelationship>>

    @Update
    suspend fun updateRelationship(rel: TransactionRelationship)
}

@Dao
interface AuditEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(event: AuditEvent): Long

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC")
    fun getAllAuditEventsFlow(): Flow<List<AuditEvent>>

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun getAuditEventCount(): Int

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAuditEvents(limit: Int): List<AuditEvent>
}

@Dao
interface ReconciliationDiscrepancyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscrepancy(discrepancy: ReconciliationDiscrepancy): Long

    @Query("SELECT * FROM reconciliation_discrepancies WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedDiscrepanciesFlow(): Flow<List<ReconciliationDiscrepancy>>

    @Query("SELECT * FROM reconciliation_discrepancies WHERE isResolved = 0 ORDER BY timestamp DESC")
    suspend fun getUnresolvedDiscrepancies(): List<ReconciliationDiscrepancy>

    @Query("UPDATE reconciliation_discrepancies SET isResolved = 1, resolutionNotes = :notes WHERE id = :id")
    suspend fun resolveDiscrepancy(id: Long, notes: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategories(): List<Category>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long
}

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_processing ORDER BY receivedAt DESC")
    fun getAllLogsFlow(): Flow<List<SmsLog>>

    @Query("SELECT * FROM sms_processing ORDER BY receivedAt DESC")
    suspend fun getAllLogs(): List<SmsLog>

    @Query("SELECT * FROM sms_processing WHERE messageHash = :hash LIMIT 1")
    suspend fun getLogByHash(hash: String): SmsLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLog): Long

    @Query("DELETE FROM sms_processing")
    suspend fun deleteAllLogs()
}

@Dao
interface InternalTransferDao {
    @Query("SELECT * FROM internal_transfers ORDER BY createdAt DESC")
    fun getAllTransfersFlow(): Flow<List<InternalTransfer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: InternalTransfer): Long
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE settingKey = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings WHERE settingKey = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)
}
