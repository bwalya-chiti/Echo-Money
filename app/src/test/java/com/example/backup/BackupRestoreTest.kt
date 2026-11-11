package com.example.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Transaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

    private lateinit var context: Context
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backupManager = BackupManager(context)
        AppDatabase.closeDatabase()
        context.deleteDatabase("echo_money.db")
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        context.deleteDatabase("echo_money.db")
    }

    @Test
    fun `crypto manager encrypt and decrypt round trip`() {
        val originalText = "Echo Money Secret Financial Ledger Payload 2026"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val password = "StrongPassword#2026"
        val salt = CryptoManager.generateSalt()
        val iv = CryptoManager.generateIv()

        val encrypted = CryptoManager.encrypt(originalBytes, password, salt, iv, iterations = 1000)
        assertFalse(originalBytes.contentEquals(encrypted))

        val decrypted = CryptoManager.decrypt(encrypted, password, salt, iv, iterations = 1000)
        assertEquals(originalText, String(decrypted, Charsets.UTF_8))
    }

    @Test(expected = Exception::class)
    fun `crypto manager wrong password fails decryption`() {
        val originalBytes = "Data to protect".toByteArray(Charsets.UTF_8)
        val salt = CryptoManager.generateSalt()
        val iv = CryptoManager.generateIv()

        val encrypted = CryptoManager.encrypt(originalBytes, "CorrectPass123", salt, iv, iterations = 1000)
        // Decrypt with incorrect password should throw
        CryptoManager.decrypt(encrypted, "WrongPass456", salt, iv, iterations = 1000)
    }

    @Test(expected = Exception::class)
    fun `crypto manager tampered payload fails decryption`() {
        val originalBytes = "Uncompromised Data".toByteArray(Charsets.UTF_8)
        val password = "ValidPassword"
        val salt = CryptoManager.generateSalt()
        val iv = CryptoManager.generateIv()

        val encrypted = CryptoManager.encrypt(originalBytes, password, salt, iv, iterations = 1000)
        // Tamper with one byte
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2].toInt() xor 0xFF).toByte()

        CryptoManager.decrypt(encrypted, password, salt, iv, iterations = 1000)
    }

    @Test
    fun `backup creation and password verification inspection`() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val txDao = db.transactionDao()
        val accDao = db.accountDao()

        // Populate sample transactions
        txDao.insertTransaction(
            Transaction(
                provider = "AIRTEL",
                transactionReference = "MP261101.1131.K33436",
                transactionType = "OUTGOING_TRANSFER",
                amount = 145.0,
                status = "SUCCESSFUL"
            )
        )
        txDao.insertTransaction(
            Transaction(
                provider = "MTN",
                transactionReference = "10978934053",
                transactionType = "CASH_WITHDRAWAL",
                amount = 40.0,
                status = "SUCCESSFUL"
            )
        )
        accDao.updateLastReportedBalance("AIRTEL", 150.0, System.currentTimeMillis())

        val password = "VaultKey9988!"
        val backupResult = backupManager.createBackup(password = password, isAuto = false)
        assertTrue("Backup creation should succeed", backupResult.isSuccess)

        val backupFile = backupResult.getOrThrow()
        assertTrue("Backup file must exist", backupFile.exists())
        assertTrue("Backup file size must be > 0", backupFile.length() > 0)

        // 1. Inspect with wrong password
        FileInputStream(backupFile).use { fis ->
            val inspectWrong = backupManager.inspectBackup(fis, "WrongPassword")
            assertTrue("Inspection with wrong password should fail", inspectWrong.isFailure)
        }

        // 2. Inspect with correct password
        FileInputStream(backupFile).use { fis ->
            val inspectCorrect = backupManager.inspectBackup(fis, password)
            assertTrue("Inspection with correct password should succeed", inspectCorrect.isSuccess)
            val info = inspectCorrect.getOrThrow()
            assertEquals("ECHO MONEY", info.manifest.app)
            assertEquals(2, info.summary.totalTransactions)
        }
    }

    @Test
    fun `full restore round-trip preserves transactions and accounts`() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val txDao = db.transactionDao()

        txDao.deleteAllTransactions()

        txDao.insertTransaction(
            Transaction(
                provider = "AIRTEL",
                transactionReference = "LN261101.1131.D86835",
                transactionType = "LOAN_DISBURSEMENT",
                direction = "CREDIT",
                amount = 148.0,
                status = "SUCCESSFUL",
                description = "NaSova loan credit"
            )
        )

        val password = "MySecurePassword2026"
        val backupResult = backupManager.createBackup(password = password)
        val backupFile = backupResult.getOrThrow()

        // Modify DB to clear transactions
        txDao.deleteAllTransactions()
        assertEquals(0, txDao.getAllTransactions().size)

        // Perform full restore
        FileInputStream(backupFile).use { fis ->
            val restoreResult = backupManager.restoreBackup(fis, password, RestoreMode.FULL_REPLACE)
            assertTrue("Full restore should succeed: ${restoreResult.exceptionOrNull()?.message ?: ""}", restoreResult.isSuccess)
            val res = restoreResult.getOrThrow()
            assertEquals(RestoreMode.FULL_REPLACE, res.mode)
            assertEquals(1, res.transactionsImported)
        }

        // Verify data in restored database
        val restoredDb = AppDatabase.getDatabase(context)
        val restoredList = restoredDb.transactionDao().getAllTransactions()
        assertEquals(1, restoredList.size)
        assertEquals("LN261101.1131.D86835", restoredList[0].transactionReference)
        assertEquals(148.0, restoredList[0].amount, 0.001)
    }

    @Test
    fun `selective merge prevents duplicate transaction references`() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val txDao = db.transactionDao()

        txDao.deleteAllTransactions()

        // Insert Transaction A
        txDao.insertTransaction(
            Transaction(
                provider = "AIRTEL",
                transactionReference = "MP261101.0843.A95608",
                transactionType = "MERCHANT_PAYMENT",
                amount = 5.0,
                status = "SUCCESSFUL"
            )
        )

        val password = "MergeTestPassword1"
        val backupFile = backupManager.createBackup(password = password).getOrThrow()

        // Insert Transaction B into current database
        txDao.insertTransaction(
            Transaction(
                provider = "MTN",
                transactionReference = "NEW_TXN_REF_001",
                transactionType = "INCOME",
                amount = 200.0,
                status = "SUCCESSFUL"
            )
        )

        assertEquals(2, txDao.getAllTransactions().size)

        // Perform SELECTIVE_MERGE
        FileInputStream(backupFile).use { fis ->
            val mergeResult = backupManager.restoreBackup(fis, password, RestoreMode.SELECTIVE_MERGE)
            assertTrue("Merge should succeed: ${mergeResult.exceptionOrNull()?.message ?: ""}", mergeResult.isSuccess)
            val res = mergeResult.getOrThrow()
            assertEquals(RestoreMode.SELECTIVE_MERGE, res.mode)
            // Transaction A was in backup and already in DB, so it should be skipped
            assertEquals(1, res.transactionsSkipped)
            assertEquals(0, res.transactionsImported)
        }

        // Database should still contain both Transaction A and Transaction B without duplicates
        val allTx = txDao.getAllTransactions()
        assertEquals(2, allTx.size)
    }
}
