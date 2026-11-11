package com.example.parser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.IngestionResult
import com.example.data.repository.TransactionRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerReconciliationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking<Unit> {
            AppDatabase.populateInitialData(db)
        }
        repository = TransactionRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAirtelCompanionSms_DeduplicatesAndMerges() = runBlocking {
        // SMS 1: Till Settlement
        val sms1 = "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436."
        val res1 = repository.processIncomingSms("AirtelMoney", sms1)
        assertTrue(res1 is IngestionResult.Success)

        // SMS 2: Sent Money with SAME TID MP261101.1131.K33436
        val sms2 = "You have sent Money to Bwalya Blessings on 0766313205.Amount K145. Txn. ID : MP261101.1131.K33436.Your available balance is K0.90"
        val res2 = repository.processIncomingSms("AirtelMoney", sms2)
        assertTrue("Companion SMS with same TID should be merged, not duplicated", res2 is IngestionResult.Merged)

        // Verify only ONE transaction is stored in the ledger
        val txns = repository.getTransactions().filter { it.provider == "AIRTEL" }
        assertEquals(1, txns.size)
        val txn = txns.first()
        assertEquals(145.0, txn.amount, 0.001)
        assertEquals("MP261101.1131.K33436", txn.transactionReference)
        // Recipient should be merged with the detailed person name
        assertTrue(txn.recipient?.contains("Bwalya Blessings") == true)
        assertEquals("MNOWALLET NFS SETTLEMENT ACCOUNT", txn.merchantOrAgent)
    }

    @Test
    fun testMtnCompanionSms_DeduplicatesAndMerges() = runBlocking {
        // SMS 1: Detailed with sender name and note TID
        val sms1 = "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895."
        val res1 = repository.processIncomingSms("MTNMoMo", sms1)
        assertTrue(res1 is IngestionResult.Success)

        // SMS 2: Generic receipt for the SAME Financial Transaction ID 10978746895
        val sms2 = "Yello.You have received ZMW 145.00 from AIRTEL NFS on your mobile money account at 2026-11-01 11:31:56. Message from sender: . Financial Transaction ID: 10978746895. Dial *115# to view balance. Thank you for using Momo from MTN."
        val res2 = repository.processIncomingSms("MTNMoMo", sms2)
        assertTrue("Generic MTN confirmation for same TID must be merged", res2 is IngestionResult.Merged)

        val txns = repository.getTransactions().filter { it.provider == "MTN" }
        assertEquals(1, txns.size)
        val txn = txns.first()
        assertEquals(145.0, txn.amount, 0.001)
        assertEquals("10978746895", txn.transactionReference)
        assertEquals("MP261101.1131.K33436", txn.description)
    }

    @Test
    fun testCrossNetworkNfs_MatchedAsInternalTransfer() = runBlocking {
        // Airtel leg: debit K145 with TID MP261101.1131.K33436
        val airtelSms = "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436."
        repository.processIncomingSms("AirtelMoney", airtelSms)

        // MTN leg: received K145 with sender note MP261101.1131.K33436
        val mtnSms = "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895."
        val res = repository.processIncomingSms("MTNMoMo", mtnSms)
        assertTrue(res is IngestionResult.Success && res.isInternalTransfer)

        val allTxns = repository.getTransactions()
        assertEquals(2, allTxns.size)
        assertTrue("Airtel leg should be marked internal transfer", allTxns.all { it.isInternalTransfer })
        assertNotNull(allTxns.first().transferGroupId)
        assertEquals(allTxns.first().transferGroupId, allTxns.last().transferGroupId)
    }

    @Test
    fun testCashWithdrawal_ExtractsCorrectAmountAndType() = runBlocking {
        val sms = "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        val res = repository.processIncomingSms("MTNMoMo", sms)
        assertTrue(res is IngestionResult.Success)

        val txns = repository.getTransactions()
        assertEquals(1, txns.size)
        val txn = txns.first()
        assertEquals(40.0, txn.amount, 0.001)
        assertEquals("CASH_WITHDRAWAL", txn.transactionType)
        assertEquals("DEBIT", txn.direction)
        assertEquals(143.26, txn.balanceAfter ?: 0.0, 0.001)
        assertEquals("10978934053", txn.transactionReference)
        assertFalse("Withdrawal is not an internal transfer", txn.isInternalTransfer)
    }

    @Test
    fun testFailedTransaction_DoesNotDebitBalance() = runBlocking {
        val sms = "Dear Customer, TXN Failed with TID: XX261101.1128.S83424, Balance not available to complete the transaction.. Please try again later.Thank you."
        val res = repository.processIncomingSms("AirtelMoney", sms)
        assertTrue(res is IngestionResult.Success)

        val txns = repository.getTransactions()
        assertEquals(1, txns.size)
        val txn = txns.first()
        assertEquals("FAILED", txn.status)
        assertEquals("NONE", txn.direction)
        assertEquals(0.0, txn.amount, 0.001)
    }

    @Test
    fun testBalanceOnly_UpdatesReportedBalanceWithoutLedgerEntry() = runBlocking {
        val sms = "Dear Customer, your Airtel Money balance is ZMW 0.90. Thank you."
        val res = repository.processIncomingSms("AirtelMoney", sms)
        assertTrue(res is IngestionResult.BalanceUpdated)

        val txns = repository.getTransactions()
        assertEquals("Balance only message must not create transaction row", 0, txns.size)

        val accounts = repository.getAccounts()
        val airtel = accounts.find { it.provider == "AIRTEL" }
        assertEquals(0.90, airtel?.lastReportedBalance ?: 0.0, 0.001)
    }
}
