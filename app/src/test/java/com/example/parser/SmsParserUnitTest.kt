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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
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
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("BALANCE_UPDATE", parsed?.transactionType)
        assertEquals("NONE", parsed?.direction)
        assertEquals(0.0, parsed?.amount ?: -1.0, 0.001)
        assertEquals(0.90, parsed?.balanceAfter ?: 0.0, 0.001)
    }

    // 9. MTN Cash Withdrawal
    @Test
    fun testMtn_CashWithdrawal_AtOnSyntax() {
        val sms = "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053."
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
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
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
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
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
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
        val sms = "Yello, Payment of ZMW 5.00 to Mobile Money successful at 2026-10-28 18:34:03. Message: You have successfully purchased K5:6GB/5hrs for 0961234567 valid until 2026-10-28 23:34:03. Your new balance: 138.26 ZMW. Financial Transaction ID: MT772910."
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("MERCHANT_PAYMENT", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(5.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(138.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MT772910", parsed?.transactionReference)
    }

    // 13. MTN Reversal
    @Test
    fun testMtn_Reversal() {
        val sms = "Yello, transaction 10978934053 of ZMW 40.00 has been reversed. Your new balance: 183.26 ZMW. Financial Transaction ID: 10979999999"
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
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
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
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
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("FAILED", parsed?.transactionType)
        assertEquals("FAILED", parsed?.status)
        assertEquals("NONE", parsed?.direction)
        assertEquals("FL109783", parsed?.transactionReference)
    }

    // 16. Airtel Incoming P2P with Phone and Balance (Fixture 10)
    @Test
    fun testAirtel_IncomingP2P() {
        val sms = "You have received ZMW 50.00 from Chileshe Mwape (0977112233) on 2026-11-02 09:15:20. TID: MP261102.0915.A10293. Balance: ZMW 50.90"
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("INCOMING_TRANSFER", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(50.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(50.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261102.0915.A10293", parsed?.transactionReference)
    }

    // 17. Airtel Booth Cash Deposit (Fixture 11)
    @Test
    fun testAirtel_CashDeposit() {
        val sms = "Cash deposit of ZMW 200.00 by Agent LUSAKA CENTRAL BOOTH on 2026-11-02 10:00:00. TID: MP261102.1000.C44910. New balance: ZMW 250.90"
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("CASH_DEPOSIT", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(200.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(250.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261102.1000.C44910", parsed?.transactionReference)
    }

    // 18. Airtel ZESCO Utility Token Payment (Fixture 12)
    @Test
    fun testAirtel_ZescoPayment() {
        val sms = "Payment of ZMW 100.00 to ZESCO PREPAID successful. Token: 4920-1192-3381-0029. Fee: ZMW 0.00. Bal: ZMW 150.90. TID: MP261102.1045.Z88102"
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("MERCHANT_PAYMENT", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(100.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(150.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("MP261102.1045.Z88102", parsed?.transactionReference)
        assertEquals("Electricity & Utilities", parsed?.categoryName)
    }

    // 19. MTN P2P Transfer with Fee (Fixture 13)
    @Test
    fun testMtn_P2PTransferWithFee() {
        val sms = "Yello. You have transferred ZMW 80.00 to Mulenga Kaunda (0966445566). Fee: ZMW 2.00. Your new balance: 56.26 ZMW. Financial Transaction ID: 10979102834."
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("OUTGOING_TRANSFER", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(80.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(2.0, parsed?.fee ?: 0.0, 0.001)
        assertEquals(56.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("10979102834", parsed?.transactionReference)
    }

    // 20. MTN Reversal (Fixture 14)
    @Test
    fun testMtn_ReversalWithPrecedingAmount() {
        val sms = "Yello. Reversal of ZMW 80.00 for transaction 10979102834 has been completed. Your new balance: 136.26 ZMW. Financial Transaction ID: 10979981240."
        val parsed = SmsParsingEngine.parse("MTNMoMo", sms)
        assertNotNull(parsed)
        assertEquals("MTN", parsed?.provider)
        assertEquals("REVERSAL", parsed?.transactionType)
        assertEquals("CREDIT", parsed?.direction)
        assertEquals(80.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(136.26, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("10979102834", parsed?.originalTransactionReference)
        assertEquals("10979981240", parsed?.transactionReference)
    }

    // 21. Airtel NaSova Loan Repayment (Fixture 18)
    @Test
    fun testAirtel_LoanRepayment() {
        val sms = "Your NaSova loan repayment of ZMW 148.00 to FIKILIZA has been processed. New balance: ZMW 102.90. TID: LN261102.1400.P33819"
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNotNull(parsed)
        assertEquals("AIRTEL", parsed?.provider)
        assertEquals("LOAN_REPAYMENT", parsed?.transactionType)
        assertEquals("DEBIT", parsed?.direction)
        assertEquals(148.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(102.90, parsed?.balanceAfter ?: 0.0, 0.001)
        assertEquals("LN261102.1400.P33819", parsed?.transactionReference)
    }

    // =========================================================================
    // NEGATIVE TESTS: Messages with figures that must NOT be picked up
    // =========================================================================

    // 22. MTN Promotional Offer with prices & figures
    @Test
    fun testRejection_MtnPromotionalDataOfferWithFigures() {
        val sms = "Yello! Dial *117# to get 1GB data for only K10 valid for 24hrs. Don't miss out on high speed streaming!"
        val parsed = SmsParsingEngine.parse("MTN", sms)
        assertNull("Promotional MTN message with figures must be rejected", parsed)
    }

    // 23. Airtel Lottery Campaign with jackpot figures
    @Test
    fun testRejection_AirtelJackpotPromotionWithFigures() {
        val sms = "Dear Customer, Recharge with K20 or more today and stand a chance to win 50,000 ZMW in the weekly draw! Dial *115#."
        val parsed = SmsParsingEngine.parse("Airtel", sms)
        assertNull("Lottery campaign with monetary figures must be rejected", parsed)
    }

    // 24. OTP / Verification Code message mentioning payment figures
    @Test
    fun testRejection_OtpSecurityMessageWithFigures() {
        val sms = "Your OTP is 849201 for authorising your payment of ZMW 145.00 to TechStore. Do not share this PIN with anyone."
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNull("OTP message with transaction figure must be rejected", parsed)
    }

    // 25. Casual peer conversation mentioning figures and kwacha
    @Test
    fun testRejection_CasualPeerChatterWithFigures() {
        val sms = "Hey, call me back when you get 50 kwacha."
        val parsed = SmsParsingEngine.parse("Friend", sms)
        assertNull("Casual peer message with kwacha must be rejected", parsed)
    }

    // 26. Price discussion / shopping text
    @Test
    fun testRejection_ShoppingPriceDiscussion() {
        val sms = "Hey bro, the sneakers cost K450 at Levy Junction. Call me on 0977112233."
        val parsed = SmsParsingEngine.parse("+260977112233", sms)
        assertNull("Shopping chat with K450 figure must be rejected", parsed)
    }

    // 27. Meeting time and room numbers
    @Test
    fun testRejection_MeetingNoticeWithNumbers() {
        val sms = "Meeting scheduled at 14:30 in Boardroom 3. Please bring 15 copies of the proposal."
        val parsed = SmsParsingEngine.parse("Office", sms)
        assertNull("Meeting text with numbers must be rejected", parsed)
    }

    // 28. Airtime loan promotion offer (not confirmed credit)
    @Test
    fun testRejection_AirtimeLoanMarketingOffer() {
        val sms = "You qualify for a loan of K50. Dial *115# to apply now for Siliza airtime."
        val parsed = SmsParsingEngine.parse("AirtelMoney", sms)
        assertNull("Loan marketing offer must be rejected", parsed)
    }

    // 29. Data bundle expiry notice
    @Test
    fun testRejection_DataBundleExpiryNotice() {
        val sms = "Dear Customer, your 2GB data bundle has expired. Dial *117# to buy a new bundle."
        val parsed = SmsParsingEngine.parse("117", sms)
        assertNull("Bundle expiration notice must be rejected", parsed)
    }
}
