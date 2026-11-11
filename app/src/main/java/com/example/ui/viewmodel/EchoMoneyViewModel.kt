package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.EchoMoneyApplication
import com.example.data.accounting.DailyFinancialStatement
import com.example.data.accounting.ReportingEngine
import com.example.data.model.Account
import com.example.data.model.ReconciliationDiscrepancy
import com.example.data.model.SmsLog
import com.example.data.model.Transaction
import com.example.data.repository.IngestionResult
import com.example.security.SecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class FilterPeriod {
    TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, ALL
}

enum class SortOrder {
    NEWEST, OLDEST, HIGHEST, LOWEST
}

data class FinancialMetrics(
    val combinedBalance: Double = 0.0,
    val airtelReportedBalance: Double = 0.0,
    val airtelCalculatedBalance: Double = 0.0,
    val airtelReconciliationDiff: Double = 0.0,
    val mtnReportedBalance: Double = 0.0,
    val mtnCalculatedBalance: Double = 0.0,
    val mtnReconciliationDiff: Double = 0.0,
    val cashCalculatedBalance: Double = 0.0,
    val hasReconciliationDiscrepancy: Boolean = false,
    val periodIncome: Double = 0.0,
    val periodExpenses: Double = 0.0,
    val periodWithdrawals: Double = 0.0,
    val periodInternalTransfers: Double = 0.0,
    val periodFees: Double = 0.0,
    val periodNetCashFlow: Double = 0.0
)

data class UiState(
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val reviewItems: List<Transaction> = emptyList(),
    val discrepancies: List<ReconciliationDiscrepancy> = emptyList(),
    val auditEvents: List<com.example.data.model.AuditEvent> = emptyList(),
    val systemDiagnostics: com.example.data.repository.SystemDiagnostics? = null,
    val dailyStatement: DailyFinancialStatement? = null,
    val metrics: FinancialMetrics = FinancialMetrics(),
    val selectedPeriod: FilterPeriod = FilterPeriod.THIS_MONTH,
    val searchQuery: String = "",
    val providerFilter: String = "ALL", // ALL, AIRTEL, MTN, CASH
    val typeFilter: String = "ALL", // ALL, INCOME, EXPENSE, INTERNAL, WITHDRAWAL
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val smsLogs: List<SmsLog> = emptyList(),
    val isSmsPermissionGranted: Boolean = false,
    val messageSnackbar: String? = null,
    val isPinLocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val isBiometricConfigured: Boolean = false,
    val localBackups: List<com.example.backup.LocalBackupFile> = emptyList(),
    val autoBackupSchedule: String = "DAILY",
    val lastAutoBackupTimestamp: Long? = null,
    val isBackupBusy: Boolean = false,
    val backupProgressMessage: String? = null
)

class EchoMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as EchoMoneyApplication).repository
    val securityManager = SecurityManager(application)
    val backupManager = com.example.backup.BackupManager(application)

    private val _selectedPeriod = MutableStateFlow(FilterPeriod.THIS_MONTH)
    private val _searchQuery = MutableStateFlow("")
    private val _providerFilter = MutableStateFlow("ALL")
    private val _typeFilter = MutableStateFlow("ALL")
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _isSmsPermissionGranted = MutableStateFlow(false)
    private val _messageSnackbar = MutableStateFlow<String?>(null)
    private val _localBackups = MutableStateFlow<List<com.example.backup.LocalBackupFile>>(emptyList())
    private val _autoBackupSchedule = MutableStateFlow("DAILY")
    private val _lastAutoBackupTimestamp = MutableStateFlow<Long?>(null)
    private val _isBackupBusy = MutableStateFlow(false)
    private val _backupProgressMessage = MutableStateFlow<String?>(null)
    private val _dailyStatement = MutableStateFlow<DailyFinancialStatement?>(null)
    private val _systemDiagnostics = MutableStateFlow<com.example.data.repository.SystemDiagnostics?>(null)

    val uiState: StateFlow<UiState> = combine(
        combine<Any?, StatePackA>(
            repository.accountsFlow,
            repository.transactionsFlow,
            repository.smsLogsFlow,
            repository.reviewNeededFlow,
            repository.discrepanciesFlow,
            repository.auditEventsFlow
        ) { args: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            StatePackA(
                args[0] as List<Account>,
                args[1] as List<Transaction>,
                args[2] as List<SmsLog>,
                args[3] as List<Transaction>,
                args[4] as List<ReconciliationDiscrepancy>,
                args[5] as List<com.example.data.model.AuditEvent>
            )
        },
        combine<Any?, StatePackB>(
            _selectedPeriod,
            _searchQuery,
            _providerFilter,
            _typeFilter,
            _sortOrder,
            _isSmsPermissionGranted
        ) { args: Array<Any?> ->
            StatePackB(
                args[0] as FilterPeriod,
                args[1] as String,
                args[2] as String,
                args[3] as String,
                args[4] as SortOrder,
                args[5] as Boolean
            )
        },
        combine<Any?, StatePackC>(
            _messageSnackbar,
            securityManager.isLocked,
            _localBackups,
            _autoBackupSchedule,
            _lastAutoBackupTimestamp,
            _isBackupBusy,
            _backupProgressMessage,
            _dailyStatement,
            _systemDiagnostics
        ) { args: Array<Any?> ->
            StatePackC(
                args[0] as String?,
                args[1] as Boolean,
                @Suppress("UNCHECKED_CAST") (args[2] as List<com.example.backup.LocalBackupFile>),
                args[3] as String,
                args[4] as Long?,
                args[5] as Boolean,
                args[6] as String?,
                args[7] as DailyFinancialStatement?,
                args[8] as com.example.data.repository.SystemDiagnostics?
            )
        }
    ) { packA, packB, packC ->
        val accounts = packA.accounts
        val transactions = packA.transactions
        val period = packB.period
        val search = packB.search
        val provFilter = packB.provFilter
        val tFilter = packB.tFilter
        val sort = packB.sort

        val (startTime, endTime) = getPeriodTimeRange(period)

        // Filter transactions
        val filtered = transactions.filter { txn ->
            val inPeriod = if (period == FilterPeriod.ALL) true else txn.transactionDate in startTime..endTime

            val inSearch = if (search.isBlank()) true else {
                val q = search.trim().lowercase()
                (txn.recipient?.lowercase()?.contains(q) == true) ||
                (txn.sender?.lowercase()?.contains(q) == true) ||
                (txn.description?.lowercase()?.contains(q) == true) ||
                (txn.transactionReference?.lowercase()?.contains(q) == true) ||
                (txn.categoryName.lowercase().contains(q)) ||
                (txn.amount.toString().contains(q))
            }

            val inProvider = if (provFilter == "ALL") true else txn.provider.equals(provFilter, ignoreCase = true)

            val inType = when (tFilter) {
                "ALL" -> true
                "INCOME" -> (txn.transactionType == "INCOMING_TRANSFER" || txn.direction == "CREDIT") && !txn.isInternalTransfer && txn.transactionType != "LOAN_DISBURSEMENT"
                "EXPENSE" -> (txn.direction == "DEBIT" && txn.transactionType != "CASH_WITHDRAWAL" && !txn.isInternalTransfer)
                "INTERNAL" -> txn.isInternalTransfer
                "WITHDRAWAL" -> txn.transactionType == "CASH_WITHDRAWAL"
                else -> true
            }

            inPeriod && inSearch && inProvider && inType
        }.let { list ->
            when (sort) {
                SortOrder.NEWEST -> list.sortedByDescending { it.transactionDate }
                SortOrder.OLDEST -> list.sortedBy { it.transactionDate }
                SortOrder.HIGHEST -> list.sortedByDescending { it.amountMinor }
                SortOrder.LOWEST -> list.sortedBy { it.amountMinor }
            }
        }

        // Compute metrics
        val metrics = calculateMetrics(accounts, transactions, startTime, endTime, period)

        UiState(
            accounts = accounts,
            transactions = transactions,
            filteredTransactions = filtered,
            reviewItems = packA.reviews,
            discrepancies = packA.discrepancies,
            auditEvents = packA.auditEvents,
            systemDiagnostics = packC.diagnostics,
            dailyStatement = packC.dailyStatement,
            metrics = metrics,
            selectedPeriod = period,
            searchQuery = search,
            providerFilter = provFilter,
            typeFilter = tFilter,
            sortOrder = sort,
            smsLogs = packA.smsLogs,
            isSmsPermissionGranted = packB.smsGranted,
            messageSnackbar = packC.snackbar,
            isPinLocked = packC.locked,
            isPinConfigured = securityManager.isPinEnabled(),
            isBiometricConfigured = securityManager.isBiometricEnabled(),
            localBackups = packC.localBackups,
            autoBackupSchedule = packC.autoSchedule,
            lastAutoBackupTimestamp = packC.lastAutoTs,
            isBackupBusy = packC.isBusy,
            backupProgressMessage = packC.progressMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    private data class StatePackA(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val smsLogs: List<SmsLog>,
        val reviews: List<Transaction>,
        val discrepancies: List<ReconciliationDiscrepancy>,
        val auditEvents: List<com.example.data.model.AuditEvent>
    )

    private data class StatePackB(
        val period: FilterPeriod,
        val search: String,
        val provFilter: String,
        val tFilter: String,
        val sort: SortOrder,
        val smsGranted: Boolean
    )

    private data class StatePackC(
        val snackbar: String?,
        val locked: Boolean,
        val localBackups: List<com.example.backup.LocalBackupFile>,
        val autoSchedule: String,
        val lastAutoTs: Long?,
        val isBusy: Boolean,
        val progressMsg: String?,
        val dailyStatement: DailyFinancialStatement?,
        val diagnostics: com.example.data.repository.SystemDiagnostics?
    )

    private fun getPeriodTimeRange(period: FilterPeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        return when (period) {
            FilterPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            FilterPeriod.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            FilterPeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            FilterPeriod.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                start to cal.timeInMillis
            }
            FilterPeriod.ALL -> {
                0L to Long.MAX_VALUE
            }
        }
    }

    private fun calculateMetrics(
        accounts: List<Account>,
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long,
        period: FilterPeriod
    ): FinancialMetrics {
        val airtelAcc = accounts.find { it.provider == "AIRTEL" }
        val mtnAcc = accounts.find { it.provider == "MTN" }
        val cashAcc = accounts.find { it.provider == "CASH" }

        var airtelCalc = airtelAcc?.openingBalance ?: 0.0
        var mtnCalc = mtnAcc?.openingBalance ?: 0.0
        var cashCalc = cashAcc?.openingBalance ?: 0.0

        var pIncome = 0.0
        var pExpenses = 0.0
        var pWithdrawals = 0.0
        var pInternalTransfers = 0.0
        var pFees = 0.0

        for (t in transactions) {
            if (t.status == "SUCCESSFUL") {
                val isCredit = t.direction == "CREDIT"
                val isDebit = t.direction == "DEBIT"
                val isWithdrawal = t.transactionType == "CASH_WITHDRAWAL"

                when (t.provider) {
                    "AIRTEL" -> {
                        if (isCredit) {
                            airtelCalc += t.amount
                        } else if (isDebit) {
                            airtelCalc -= (t.amount + t.fee)
                            if (isWithdrawal) {
                                cashCalc += t.amount
                            }
                        }
                    }
                    "MTN" -> {
                        if (isCredit) {
                            mtnCalc += t.amount
                        } else if (isDebit) {
                            mtnCalc -= (t.amount + t.fee)
                            if (isWithdrawal) {
                                cashCalc += t.amount
                            }
                        }
                    }
                    "CASH" -> {
                        if (isCredit) cashCalc += t.amount else if (isDebit) cashCalc -= (t.amount + t.fee)
                    }
                }
            }

            val inPeriod = if (period == FilterPeriod.ALL) true else t.transactionDate in startTime..endTime
            if (inPeriod && t.status == "SUCCESSFUL") {
                pFees += t.fee
                if (t.isInternalTransfer) {
                    pInternalTransfers += t.amount
                } else if (t.transactionType == "CASH_WITHDRAWAL") {
                    pWithdrawals += t.amount
                } else if (t.transactionType == "LOAN_DISBURSEMENT") {
                    // Loan proceeds: separate from earned income
                } else if (t.direction == "CREDIT") {
                    pIncome += t.amount
                } else if (t.direction == "DEBIT") {
                    pExpenses += t.amount
                }
            }
        }

        val airtelReported = airtelAcc?.lastReportedBalance ?: airtelCalc
        val mtnReported = mtnAcc?.lastReportedBalance ?: mtnCalc
        val airtelDiff = airtelCalc - airtelReported
        val mtnDiff = mtnCalc - mtnReported
        val hasDiscrepancy = (kotlin.math.abs(airtelDiff) > 0.01 && airtelAcc?.lastReportedBalance != null) ||
                             (kotlin.math.abs(mtnDiff) > 0.01 && mtnAcc?.lastReportedBalance != null)
        val combined = airtelCalc + mtnCalc + cashCalc

        return FinancialMetrics(
            combinedBalance = combined,
            airtelReportedBalance = airtelReported,
            airtelCalculatedBalance = airtelCalc,
            airtelReconciliationDiff = airtelDiff,
            mtnReportedBalance = mtnReported,
            mtnCalculatedBalance = mtnCalc,
            mtnReconciliationDiff = mtnDiff,
            cashCalculatedBalance = cashCalc,
            hasReconciliationDiscrepancy = hasDiscrepancy,
            periodIncome = pIncome,
            periodExpenses = pExpenses,
            periodWithdrawals = pWithdrawals,
            periodInternalTransfers = pInternalTransfers,
            periodFees = pFees,
            periodNetCashFlow = pIncome - pExpenses
        )
    }

    fun setPeriod(period: FilterPeriod) {
        _selectedPeriod.value = period
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setProviderFilter(provider: String) {
        _providerFilter.value = provider
    }

    fun setTypeFilter(type: String) {
        _typeFilter.value = type
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSmsPermissionGranted(granted: Boolean) {
        _isSmsPermissionGranted.value = granted
    }

    fun dismissSnackbar() {
        _messageSnackbar.value = null
    }

    fun showMessage(msg: String) {
        _messageSnackbar.value = msg
    }

    fun updateOpeningBalance(provider: String, balance: Double) {
        viewModelScope.launch {
            repository.updateOpeningBalance(provider, balance)
            showMessage("$provider opening balance updated to ZMW ${"%.2f".format(balance)}")
            refreshDailyStatement()
        }
    }

    fun reconcileAccount(provider: String, targetBalance: Double) {
        viewModelScope.launch {
            val txns = repository.getTransactions().filter { it.provider == provider && it.status == "SUCCESSFUL" }
            val netMovement = txns.sumOf {
                if (it.direction == "CREDIT") it.amount else -(it.amount + it.fee)
            }
            val neededOpening = targetBalance - netMovement
            repository.updateOpeningBalance(provider, neededOpening)
            repository.reconcileAccount(provider, targetBalance)
            showMessage("$provider reconciled! Opening balance adjusted to ZMW ${"%.2f".format(neededOpening)}")
            refreshDailyStatement()
        }
    }

    fun resolveReview(id: Long, approved: Boolean, category: String? = null) {
        viewModelScope.launch {
            repository.resolveReview(id, approved, category)
            showMessage(if (approved) "Transaction approved and posted" else "Transaction rejected")
            refreshDailyStatement()
        }
    }

    fun resolveDiscrepancy(id: Long, notes: String) {
        viewModelScope.launch {
            repository.resolveDiscrepancy(id, notes)
            showMessage("Discrepancy marked as resolved")
            refreshDailyStatement()
        }
    }

    fun refreshDailyStatement(targetDate: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val db = com.example.data.AppDatabase.getDatabase(getApplication())
            val statement = ReportingEngine.generateDailyStatement(db, targetDate)
            _dailyStatement.value = statement
        }
    }

    fun addManualTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            showMessage("Transaction added successfully")
            refreshDailyStatement()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            showMessage("Transaction updated")
            refreshDailyStatement()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            showMessage("Transaction deleted")
            refreshDailyStatement()
        }
    }

    fun toggleInternalTransfer(id: Long) {
        viewModelScope.launch {
            repository.toggleInternalTransfer(id)
            showMessage("Internal transfer status toggled")
            refreshDailyStatement()
            refreshSystemDiagnostics()
        }
    }

    suspend fun getJournalLinesForTransaction(id: Long): List<com.example.data.model.JournalLine> {
        return repository.getJournalLinesForTransaction(id)
    }

    fun reverseTransaction(id: Long, reason: String) {
        viewModelScope.launch {
            val rev = repository.postCompensatingReversal(id, reason)
            if (rev != null) {
                showMessage("Compensating reversal posted for Txn #$id")
                refreshDailyStatement()
                refreshSystemDiagnostics()
            } else {
                showMessage("Transaction not found or already reversed")
            }
        }
    }

    fun refreshSystemDiagnostics() {
        viewModelScope.launch {
            _systemDiagnostics.value = repository.getSystemDiagnostics()
        }
    }

    fun simulateSmsIngestion(sender: String, body: String, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.processIncomingSms(sender, body)) {
                is IngestionResult.Success -> {
                    val msg = "Success: Recorded ${res.transaction.provider} ZMW ${"%.2f".format(res.transaction.amount)}" +
                            if (res.isInternalTransfer) " (Linked Internal Transfer)" else ""
                    showMessage(msg)
                    onResult(msg)
                }
                is IngestionResult.Merged -> {
                    val msg = "Merged: ${res.reason} (Amount: ZMW ${"%.2f".format(res.transaction.amount)})"
                    showMessage(msg)
                    onResult(msg)
                }
                is IngestionResult.BalanceUpdated -> {
                    val msg = "Balance Updated: ${res.provider} reported balance is ZMW ${"%.2f".format(res.newBalance)}"
                    showMessage(msg)
                    onResult(msg)
                }
                is IngestionResult.ReviewNeeded -> {
                    val msg = "Recorded for Review: ZMW ${"%.2f".format(res.transaction.amount)}"
                    showMessage(msg)
                    onResult(msg)
                }
                is IngestionResult.Duplicate -> {
                    showMessage("Duplicate detected: ${res.reason}")
                    onResult("Duplicate: ${res.reason}")
                }
                is IngestionResult.Failed -> {
                    showMessage("Failed: ${res.error}")
                    onResult("Failed: ${res.error}")
                }
            }
            refreshDailyStatement()
            refreshSystemDiagnostics()
        }
    }

    val masterFixtures: List<TestFixture> = MASTER_TEST_FIXTURES

    fun runTestFixture(fixture: TestFixture, onResult: (String) -> Unit) {
        simulateSmsIngestion(fixture.sender, fixture.body, onResult)
    }

    fun runAllTestFixtures(onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var successCount = 0
            var duplicateCount = 0
            for (fixture in MASTER_TEST_FIXTURES) {
                when (repository.processIncomingSms(fixture.sender, fixture.body)) {
                    is IngestionResult.Success, is IngestionResult.ReviewNeeded, is IngestionResult.Merged, is IngestionResult.BalanceUpdated -> successCount++
                    is IngestionResult.Duplicate -> duplicateCount++
                    is IngestionResult.Failed -> {}
                }
            }
            showMessage("Processed 18 fixtures: $successCount handled, $duplicateCount duplicates")
            refreshDailyStatement()
            refreshSystemDiagnostics()
            onComplete(successCount, duplicateCount)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            showMessage("All transaction and log data cleared")
            refreshDailyStatement()
            refreshSystemDiagnostics()
        }
    }

    suspend fun exportCsv(): String = repository.exportToCsv()
    suspend fun exportJson(): String = repository.exportToJson()
    suspend fun importJson(json: String): Boolean = repository.importFromJson(json)

    fun unlockApp(pin: String): Boolean {
        return securityManager.unlock(pin)
    }

    fun unlockWithBiometrics(): Boolean {
        return securityManager.unlockWithBiometrics()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityManager.setBiometricEnabled(enabled)
        if (enabled) {
            showMessage("Biometric unlock enabled")
        } else {
            showMessage("Biometric unlock disabled")
        }
    }

    fun setPin(pin: String) {
        securityManager.setPin(pin)
        showMessage("Security PIN set successfully")
    }

    fun disablePin() {
        securityManager.disablePin()
        showMessage("Security PIN disabled")
    }

    init {
        refreshLocalBackups()
        loadAutoBackupSchedule()
        refreshDailyStatement()
        refreshSystemDiagnostics()
    }

    fun refreshLocalBackups() {
        _localBackups.value = backupManager.listLocalBackups()
    }

    private fun loadAutoBackupSchedule() {
        viewModelScope.launch {
            val db = com.example.data.AppDatabase.getDatabase(getApplication())
            val setting = db.appSettingDao().getSetting("auto_backup_schedule")?.settingValue ?: "DAILY"
            _autoBackupSchedule.value = setting
            val lastTs = db.appSettingDao().getSetting("last_auto_backup_timestamp")?.settingValue?.toLongOrNull()
            _lastAutoBackupTimestamp.value = lastTs
        }
    }

    fun updateAutoBackupSchedule(frequency: String) {
        viewModelScope.launch {
            val db = com.example.data.AppDatabase.getDatabase(getApplication())
            db.appSettingDao().setSetting(com.example.data.model.AppSetting("auto_backup_schedule", frequency))
            _autoBackupSchedule.value = frequency
            com.example.backup.BackupScheduler.scheduleAutoBackup(getApplication(), frequency)
            showMessage("Auto backup frequency set to $frequency")
        }
    }

    fun createEncryptedBackup(password: String, onResult: (Result<java.io.File>) -> Unit = {}) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgressMessage.value = "Encrypting financial database with AES-256-GCM..."
            try {
                val res = backupManager.createBackup(password = password, isAuto = false)
                refreshLocalBackups()
                if (res.isSuccess) {
                    showMessage("Encrypted backup (.echomoney) created successfully")
                } else {
                    showMessage("Backup failed: ${res.exceptionOrNull()?.message}")
                }
                onResult(res)
            } finally {
                _isBackupBusy.value = false
                _backupProgressMessage.value = null
            }
        }
    }

    fun exportEncryptedBackup(password: String, outputStream: java.io.OutputStream, onResult: (Result<java.io.File>) -> Unit = {}) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgressMessage.value = "Exporting encrypted backup (.echomoney)..."
            try {
                val res = backupManager.createBackup(password = password, isAuto = false, targetOutputStream = outputStream)
                refreshLocalBackups()
                if (res.isSuccess) {
                    showMessage("Backup exported successfully")
                } else {
                    showMessage("Export failed: ${res.exceptionOrNull()?.message}")
                }
                onResult(res)
            } finally {
                _isBackupBusy.value = false
                _backupProgressMessage.value = null
            }
        }
    }

    suspend fun inspectBackup(inputStream: java.io.InputStream, password: String): Result<com.example.backup.BackupInspection> {
        return backupManager.inspectBackup(inputStream, password)
    }

    fun restoreBackup(
        inputStream: java.io.InputStream,
        password: String,
        mode: com.example.backup.RestoreMode,
        onResult: (Result<com.example.backup.RestoreResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupProgressMessage.value = if (mode == com.example.backup.RestoreMode.FULL_REPLACE)
                "Creating safety snapshot & restoring database..."
            else "Merging transactions & verifying integrity..."

            try {
                val res = backupManager.restoreBackup(inputStream, password, mode)
                if (res.isSuccess) {
                    val data = res.getOrNull()
                    if (data != null && data.mode == com.example.backup.RestoreMode.FULL_REPLACE) {
                        showMessage("Full restore complete: ${data.transactionsImported} transactions loaded")
                    } else if (data != null) {
                        showMessage("Merge complete: ${data.transactionsImported} added, ${data.transactionsSkipped} duplicates skipped")
                    }
                    refreshLocalBackups()
                    refreshDailyStatement()
                } else {
                    showMessage("Restore failed: ${res.exceptionOrNull()?.message}")
                }
                onResult(res)
            } finally {
                _isBackupBusy.value = false
                _backupProgressMessage.value = null
            }
        }
    }

    fun deleteLocalBackup(filePath: String) {
        viewModelScope.launch {
            val deleted = backupManager.deleteLocalBackup(filePath)
            if (deleted) {
                refreshLocalBackups()
                showMessage("Backup deleted")
            } else {
                showMessage("Failed to delete backup")
            }
        }
    }
}

data class TestFixture(
    val id: Int,
    val title: String,
    val sender: String,
    val body: String,
    val tag: String,
    val expectedType: String
)

val MASTER_TEST_FIXTURES = listOf(
    TestFixture(
        1, "Airtel Failed - Insufficient Balance", "AirtelMoney",
        "Dear Customer, TXN Failed with TID: XX261101.1128.S83424, Balance not available to complete the transaction.. Please try again later.Thank you.",
        "Failed TXN", "FAILED"
    ),
    TestFixture(
        2, "Airtel NaSova Loan Credit from FIKILIZA", "AirtelMoney",
        "Dear customer you have been credited with a NaSova loan of 148.00 ZMW from FIKILIZA. New balance 148.72 ZMW. Txn. Id: LN261101.1131.D86835",
        "Loan Proceeds", "LOAN_DISBURSEMENT"
    ),
    TestFixture(
        3, "Airtel NFS Till Settlement Outgoing (K145)", "AirtelMoney",
        "Payment of ZMW 145.00 Till Number MNOWALLET NFS SETTLEMENT ACCOUNT. Airtel Money bal is ZMW 0.90. TID : MP261101.1131.K33436.",
        "Till Transfer", "OUTGOING_TRANSFER"
    ),
    TestFixture(
        4, "Airtel Sent Money Companion (K145 Same TID)", "AirtelMoney",
        "You have sent Money to Bwalya Blessings on 0766313205.Amount K145. Txn. ID : MP261101.1131.K33436.Your available balance is K0.90",
        "P2P Companion", "OUTGOING_TRANSFER"
    ),
    TestFixture(
        5, "Airtel Merchant Payment to SochesCare (K5)", "AirtelMoney",
        "PAID ZMW 5.00 to SOCHESCARE AIRTEL Charge ZMW 0.00, TID MP261101.0843.A95608. Bal ZMW 0.90 Date: 01-November-2026",
        "Merchant Pay", "MERCHANT_PAYMENT"
    ),
    TestFixture(
        6, "MTN NFS Incoming with Sender Note (K145)", "MTNMoMo",
        "Yello.You have received ZMW 145.00 from Airtel NFS - Blessings Bwalya (XXXXXX3205) on your mobile money account at 2026-11-01T11:31:56.375. Message from sender: MP261101.1131.K33436. Financial Transaction ID: 10978746895.",
        "NFS Incoming", "INCOMING_TRANSFER"
    ),
    TestFixture(
        7, "MTN Duplicate Generic Receipt for same TID", "MTNMoMo",
        "Yello.You have received ZMW 145.00 from AIRTEL NFS on your mobile money account at 2026-11-01 11:31:56. Message from sender: . Financial Transaction ID: 10978746895. Dial *115# to view balance. Thank you for using Momo from MTN.",
        "MTN Duplicate", "DUPLICATE_MERGE"
    ),
    TestFixture(
        8, "MTN Agent Cash Withdrawal (K40)", "MTNMoMo",
        "You have withdrawn 40.00 ZMW from your mobile money account at on 2026-11-01 11:50:00. Your new balance:143.26 ZMW. Financial Transaction Id: 10978934053.",
        "Cash Withdrawal", "CASH_WITHDRAWAL"
    ),
    TestFixture(
        9, "MTN Data Bundle Purchase (K5)", "MTNMoMo",
        "Yello, Payment of ZMW 5.00 to Mobile Money successful at 2026-10-28 18:34:03. Message: You have successfully purchased K5:6GB/5hrs for 0961234567 valid until 2026-10-28 23:34:03. Your new balance: 138.26 ZMW. Financial Transaction ID: MT772910.",
        "Data Bundle", "MERCHANT_PAYMENT"
    ),
    TestFixture(
        10, "Airtel Incoming P2P Transfer (K50)", "AirtelMoney",
        "You have received ZMW 50.00 from Chileshe Mwape (0977112233) on 2026-11-02 09:15:20. TID: MP261102.0915.A10293. Balance: ZMW 50.90",
        "P2P Inbound", "INCOMING_TRANSFER"
    ),
    TestFixture(
        11, "Airtel Booth Agent Cash Deposit (K200)", "AirtelMoney",
        "Cash deposit of ZMW 200.00 by Agent LUSAKA CENTRAL BOOTH on 2026-11-02 10:00:00. TID: MP261102.1000.C44910. New balance: ZMW 250.90",
        "Cash Deposit", "CASH_DEPOSIT"
    ),
    TestFixture(
        12, "Airtel ZESCO Electricity Token Payment (K100)", "AirtelMoney",
        "Payment of ZMW 100.00 to ZESCO PREPAID successful. Token: 4920-1192-3381-0029. Fee: ZMW 0.00. Bal: ZMW 150.90. TID: MP261102.1045.Z88102",
        "Utility Bill", "MERCHANT_PAYMENT"
    ),
    TestFixture(
        13, "MTN P2P Transfer with Transaction Fee (K80 + K2 Fee)", "MTNMoMo",
        "Yello. You have transferred ZMW 80.00 to Mulenga Kaunda (0966445566). Fee: ZMW 2.00. Your new balance: 56.26 ZMW. Financial Transaction ID: 10979102834.",
        "P2P Out + Fee", "OUTGOING_TRANSFER"
    ),
    TestFixture(
        14, "MTN Transaction Reversal Credit (K80)", "MTNMoMo",
        "Yello. Reversal of ZMW 80.00 for transaction 10979102834 has been completed. Your new balance: 136.26 ZMW. Financial Transaction ID: 10979981240.",
        "MTN Reversal", "REVERSAL"
    ),
    TestFixture(
        15, "Airtel Transaction Reversal Credit (K100)", "AirtelMoney",
        "Reversal of ZMW 100.00 for TID MP261102.1045.Z88102 completed. New balance ZMW 250.90. TID: MP261102.1130.R99102",
        "Airtel Reversal", "REVERSAL"
    ),
    TestFixture(
        16, "Airtel Balance Enquiry Notification", "AirtelMoney",
        "Dear Customer, your Airtel Money account balance is ZMW 250.90 as at 02/11/2026 12:00:00. Thank you.",
        "Balance Check", "BALANCE_UPDATE"
    ),
    TestFixture(
        17, "MTN Balance Enquiry Notification", "MTNMoMo",
        "Yello! Your available Mobile Money balance is 136.26 ZMW. Financial Transaction Id: 10980011223.",
        "Balance Check", "BALANCE_UPDATE"
    ),
    TestFixture(
        18, "Airtel NaSova Loan Repayment Auto-Deduction (K148)", "AirtelMoney",
        "Your NaSova loan repayment of ZMW 148.00 to FIKILIZA has been processed. New balance: ZMW 102.90. TID: LN261102.1400.P33819",
        "Loan Repayment", "LOAN_REPAYMENT"
    )
)
