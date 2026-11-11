package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserUnitTest {

    // 1. Airtel Failed Transaction
    @Test
    fun testAirtel_FailedTransaction() {
        val sms = "Dear Customer, TXN Failed with TID: XX261101.1128.S83424, Balance not available to complete the transaction.. Please try again later.Thank you."
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("FAILED", parsed?.transactionType)
        assertEquals("FAILED", parsed?.status)
        assertEquals("NONE", parsed?.direction)
        assertEquals(0.0, parsed?.amount ?: -1.0, 0.001)
        assertEquals("XX261101.1128.S83424", parsed?.transactionReference)
        assertTrue(parsed?.description?.contains("Balance not available") == true)
    }

    // 2. Airtel NaSova Loan Credit
    @Test
    fun testAirtel_NaSovaLoanCredit() {
        val sms = "Dear customer you have been credited with a NaSova loan of 148.00 ZMW from FIKILIZA. New balance 148.72 ZMW. Txn. Id: LN261101.1131.D86835"
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("LOAN_DISBURSEMENT", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals("SUCCESSFUL", parsed?.status)
        assertEquals(148.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(148.72, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("LN261101.1131.D86835", parsed?.transactionReference)
        assertEquals("Loan Proceeds", parsed?.categoryName)
        assertTrue(parsed?.sender?.contains("FIKILIZA") == true)
    }

    // 3. Airtel Till NFS Settlement Payment
    @Test
    fun testAirtel_NfsSettlementTill() {
        val sms = "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436."
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("OUTGOING_TRANSFER", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(145.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("MNOWALLET NFS SETTLEMENT ACCOUNT", parsed?.recipient)
        assertEquals(0.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261101.1131.K33436", parsed?.transactionReference)
        assertTrue(parsed?.isPotentialInternalTransfer == true)
    }

    // 4. Airtel Sent Money with TID
    @Test
    fun testAirtel_SentMoneyWithTid() {
        val sms = "You have sent Money to Bwalya Blessings on 0766313205.Amount K145. Txn. ID : MP261101.1131.K33436.Your available balance is K0.90"
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("OUTGOING_TRANSFER", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(145.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(0.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261101.1131.K33436", parsed?.transactionReference)
        assertTrue(parsed?.recipient?.contains("Bwalya Blessings") == true)
    }

    // 5. Airtel Merchant Payment
    @Test
    fun testAirtel_MerchantPayment() {
        val sms = "PAID ZMW 10.00 to GLOBAL PAY COLLECTIONS Charge ZMW 0.00, TID AT77281. Bal ZMW 8.22 Date: 27-October-2026"
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("MERCHANT_PAYMENT", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(10.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("GLOBAL PAY COLLECTIONS", parsed?.recipient)
        assertEquals(0.0, parsed?.fee ?: -1.0, 0.001)
        assertEquals(8.22, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("AT77281", parsed?.transactionReference)
    }

    // 6. Airtel Cash Withdrawal
    @Test
    fun testAirtel_CashWithdrawal() {
        val sms = "You have withdrawn ZMW 200.00 from KABWE AGENT 04. Bal is ZMW 21.04. TID: AT119284."
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("CASH_WITHDRAWAL", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(200.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("Agent: KABWE AGENT 04", parsed?.recipient)
        assertEquals(21.04, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("AT119284", parsed?.transactionReference)
    }

    // 7. Airtel Reversal
    @Test
    fun testAirtel_Reversal() {
        val sms = "Reversal of transaction MP261101.0843.A95608 has been completed. ZMW 5.00 has been credited back to your Airtel Money account. New balance: ZMW 5.90. Txn ID: RV261101.1200.R12345"
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("REVERSAL", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(5.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(5.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261101.0843.A95608", parsed?.originalTransactionReference)
        assertEquals("RV261101.1200.R12345", parsed?.transactionReference)
    }

    // 8. Airtel Balance Notification Only
    @Test
    fun testAirtel_BalanceOnly() {
        val sms = "Dear Customer, your Airtel Money balance is ZMW 0.90. Thank you."
        val parsed = AirtelSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("BALANCE_UPDATE", parsed?.transactionType)
        assertEquals("NONE", parsed?.direction)
        assertEquals(0.0, parsed?.amount ?: -1.0, 0.001)
        assertEquals(0.90, parsed?.balanceAfter ?: 0.0, 0.001)
    }

    // 9. MTN Cash Withdrawal (Bug Fix Verification: "at on 2026-11-01 11:50:00" and "40.00 ZMW")
    @Test
    fun testMtn_CashWithdrawal_AtOnSyntax() {
        val sms = "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("CASH_WITHDRAWAL", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(40.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(143.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("10978934053", parsed?.transactionReference)
        assertEquals("Cash Withdrawal", parsed?.categoryName)
    }

    // 10. MTN Received from Airtel NFS with Sender and Note
    @Test
    fun testMtn_ReceivedFromAirtelNfsWithDetails() {
        val sms = "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("INCOMING_TRANSFER", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(145.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("Airtel NFS - Blessings Bwalya (XXXXXX3205)", parsed?.sender)
        assertEquals("MP261101.1131.K33436", parsed?.description)
        assertEquals("10978746895", parsed?.transactionReference)
        assertTrue(parsed?.isPotentialInternalTransfer == true)
    }

    // 11. MTN Generic Received Confirmation
    @Test
    fun testMtn_ReceivedGeneric() {
        val sms = "Yello.You have received ZMW 145.00 from AIRTEL NFS on your mobile money account at 2026-11-01 11:31:56. Message from sender: . Financial Transaction ID: 10978746895. Dial *115# to view balance. Thank you for using Momo from MTN."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("INCOMING_TRANSFER", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(145.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("10978746895", parsed?.transactionReference)
        assertTrue(parsed?.isPotentialInternalTransfer == true)
    }

    // 12. MTN Data Bundle Purchase
    @Test
    fun testMtn_DataBundlePurchase() {
        val sms = "Yello, Payment of ZMW 5.00 to Mobile Money successful at 2026-10-28 18:34:03. Message: You have successfully purchased K5:6GB/5hrs for 0961234567 valid until 2026-10-28 23:34:03. Your new balance: 30.76 ZMW. Financial Transaction ID: MT772910."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("MERCHANT_PAYMENT", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(5.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(30.76, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MT772910", parsed?.transactionReference)
    }

    // 13. MTN Reversal
    @Test
    fun testMtn_Reversal() {
        val sms = "Yello, transaction 10978934053 of ZMW 40.00 has been reversed. Your new balance: 183.26 ZMW. Financial Transaction ID: 10979999999"
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("REVERSAL", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(40.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(183.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("10978934053", parsed?.originalTransactionReference)
        assertEquals("10979999999", parsed?.transactionReference)
    }

    // 14. MTN Balance Only
    @Test
    fun testMtn_BalanceOnly() {
        val sms = "Yello, your MTN Mobile Money balance is 143.26 ZMW. Thank you."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("BALANCE_UPDATE", parsed?.transactionType)
        assertEquals("NONE", parsed?.direction)
        assertEquals(143.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals(0.0, parsed?.amount ?: -1.0, 0.001)
    }

    // 15. MTN Failed Transaction
    @Test
    fun testMtn_FailedTransaction() {
        val sms = "Yello, transaction failed. Your balance is insufficient to complete the transaction of ZMW 50.00. Financial Transaction ID: FL109783."
        val parsed = MtnSmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("FAILED", parsed?.transactionType)
        assertEquals("FAILED", parsed?.status)
        assertEquals("NONE", parsed?.direction)
        assertEquals("FL109783", parsed?.transactionReference)
    }

    // 16. Non-financial random SMS
    @Test
    fun testInvalidMessage_NotFinancialConfirmation() {
        val randomSms = "Hey, call me back when you get 50 kwacha."
        val parsed = SmsParsingEngine.parse("Friend", randomSms)
        assertNull(parsed)
    }
}
