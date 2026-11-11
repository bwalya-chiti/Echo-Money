package com.example.parser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.accounting.AccountingEngine
import com.example.data.accounting.ReconciliationEngine
import com.example.data.accounting.ReportingEngine
import com.example.data.model.Money
import com.example.data.repository.IngestionResult
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DoubleEntryAccountingTest {

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
    fun testDoubleEntryInvariant_AllPostedTransactionsBalance() = runBlocking {
        // Ingest multiple transaction types:
        // 1. Loan disbursement K148
        val loanSms = "Dear customer you have been credited with a NaSova loan of 148.00 ZMW from FIKILIZA. New balance 148.72 ZMW. Txn. Id: LN261101.1131.D86835"
        repository.processIncomingSms("AirtelMoney", loanSms)

        // 2. Merchant payment K5 with K0 fee
        val merchantSms = "PAID ZMW 5.00 to SOCHESCARE AIRTEL Charge ZMW 0.00, TID MP261101.0843.A95608. Bal ZMW 0.90 Date: 01-November-2026"
        repository.processIncomingSms("AirtelMoney", merchantSms)

        // 3. Cash withdrawal K40
        val withdrawSms = "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        repository.processIncomingSms("MTNMoMo", withdrawSms)

        // 4. Cross-network internal transfer K145
        val airtelTransfer = "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436."
        repository.processIncomingSms("AirtelMoney", airtelTransfer)
        val mtnTransfer = "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895."
        repository.processIncomingSms("MTNMoMo", mtnTransfer)

        // Check journal entries invariant: Total Debits == Total Credits for every journal entry
        val journalEntries = db.journalDao().getJournalLinesForEntry(1)
        val allEntries = db.transactionDao().getAllTransactions()
        assertTrue(allEntries.isNotEmpty())

        for (tx in allEntries) {
            val entry = db.journalDao().getJournalEntryForTransaction(tx.id)
            if (entry != null) {
                val lines = db.journalDao().getJournalLinesForEntry(entry.id)
                val sumDebits = lines.sumOf { it.debitMinor }
                val sumCredits = lines.sumOf { it.creditMinor }
                assertEquals("Debits must equal credits for journal entry ${entry.id}", sumDebits, sumCredits)
                assertTrue("Non-zero entry must have positive balance", sumDebits > 0L)
            }
        }
    }

    @Test
    fun testCashWithdrawal_MovesToCashAssetWithoutConsumptionExpense() = runBlocking {
        val withdrawSms = "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        repository.processIncomingSms("MTNMoMo", withdrawSms)

        val tx = repository.getTransactions().first()
        val entry = db.journalDao().getJournalEntryForTransaction(tx.id)
        assertNotNull(entry)

        val lines = db.journalDao().getJournalLinesForEntry(entry!!.id)
        val debitLine = lines.find { it.debitMinor > 0L }
        val creditLine = lines.find { it.creditMinor > 0L }

        assertNotNull(debitLine)
        assertNotNull(creditLine)
        assertEquals(AccountingEngine.ASSET_CASH, debitLine!!.accountCode)
        assertEquals(AccountingEngine.ASSET_MTN, creditLine!!.accountCode)
        assertEquals(4000L, debitLine.debitMinor)
        assertEquals(4000L, creditLine.creditMinor)

        // Check Daily Statement: Cash withdrawal is separated from actual consumption
        val statement = ReportingEngine.generateDailyStatement(db, tx.transactionDate)
        assertEquals(4000L, statement.cashWithdrawalsMinor)
        assertEquals(0L, statement.actualExpenditureMinor)
    }

    @Test
    fun testLoanDisbursement_RecordsLiabilityWithoutEarnedIncome() = runBlocking {
        val loanSms = "Dear customer you have been credited with a NaSova loan of 148.00 ZMW from FIKILIZA. New balance 148.72 ZMW. Txn. Id: LN261101.1131.D86835"
        repository.processIncomingSms("AirtelMoney", loanSms)

        val tx = repository.getTransactions().first()
        val entry = db.journalDao().getJournalEntryForTransaction(tx.id)
        assertNotNull(entry)

        val lines = db.journalDao().getJournalLinesForEntry(entry!!.id)
        val debitLine = lines.find { it.debitMinor > 0L }
        val creditLine = lines.find { it.creditMinor > 0L }

        assertEquals(AccountingEngine.ASSET_AIRTEL, debitLine?.accountCode)
        assertEquals(AccountingEngine.LIAB_FIKILIZA, creditLine?.accountCode)
        assertEquals(14800L, debitLine?.debitMinor)
        assertEquals(14800L, creditLine?.creditMinor)

        val statement = ReportingEngine.generateDailyStatement(db, tx.transactionDate)
        assertEquals(14800L, statement.loanProceedsMinor)
        assertEquals(0L, statement.earnedIncomeMinor)
    }

    @Test
    fun testReconciliation_DiscrepancyDetection() = runBlocking {
        // Set opening balance to K0.00
        repository.updateOpeningBalance("AIRTEL", 0.0)

        // Ingest a merchant payment of K5 where reported balance in SMS is K0.90
        val merchantSms = "PAID ZMW 5.00 to SOCHESCARE AIRTEL Charge ZMW 0.00, TID MP261101.0843.A95608. Bal ZMW 0.90 Date: 01-November-2026"
        repository.processIncomingSms("AirtelMoney", merchantSms)

        // Calculated from K0.00 - K5.00 = -K5.00. But SMS reports K0.90!
        // This means there is a missing opening balance of K5.90!
        val discrepancies = db.reconciliationDiscrepancyDao().getUnresolvedDiscrepancies()
        assertTrue("Discrepancy should be recorded when reported balance differs from calculated ledger balance", discrepancies.isNotEmpty())
        val disc = discrepancies.first()
        assertEquals("AIRTEL", disc.provider)
        assertEquals(90L, disc.reportedBalanceMinor)
        assertEquals(-500L, disc.calculatedBalanceMinor)
        assertEquals(-590L, disc.discrepancyMinor)
    }
}
