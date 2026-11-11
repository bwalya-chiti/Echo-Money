package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import com.example.ui.theme.SubtleGrey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Transaction
import com.example.ui.dialogs.AddManualTransactionDialog
import com.example.ui.dialogs.ReconcileDialog
import com.example.ui.dialogs.SetPinDialog
import com.example.ui.dialogs.TransactionDetailDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.AbsoluteBlack
import com.example.ui.theme.EchoMoneyTheme
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftWhite
import com.example.ui.viewmodel.EchoMoneyViewModel

enum class NavigationTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", androidx.compose.material.icons.Icons.Default.Dashboard),
    TRANSACTIONS("Activity", androidx.compose.material.icons.Icons.AutoMirrored.Filled.ReceiptLong),
    ANALYTICS("Analytics", androidx.compose.material.icons.Icons.Default.BarChart),
    ACCOUNTS("Accounts", androidx.compose.material.icons.Icons.Default.AccountBalance),
    SETTINGS("Settings", androidx.compose.material.icons.Icons.Default.Settings)
}

class MainActivity : FragmentActivity() {

    private val viewModel: EchoMoneyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EchoMoneyTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: EchoMoneyViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    // Dialog states
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showAddManual by remember { mutableStateOf(false) }
    var reconcileProvider by remember { mutableStateOf<String?>(null) }
    var showSetPin by remember { mutableStateOf(false) }
    var showBackupVault by remember { mutableStateOf(false) }
    var showReviewCentre by remember { mutableStateOf(false) }
    var showDailyStatement by remember { mutableStateOf(false) }

    // Check & request SMS and Notification permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] == true
        viewModel.setSmsPermissionGranted(smsGranted)
    }

    LaunchedEffect(Unit) {
        val hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        viewModel.setSmsPermissionGranted(hasReceiveSms)
    }

    fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Snackbar reaction
    LaunchedEffect(uiState.messageSnackbar) {
        uiState.messageSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    // Back button handling
    BackHandler(enabled = currentTab != NavigationTab.DASHBOARD) {
        currentTab = NavigationTab.DASHBOARD
    }

    if (uiState.isPinLocked) {
        PinLockScreen(
            isBiometricEnabled = uiState.isBiometricConfigured,
            onUnlockAttempt = { pin -> viewModel.unlockApp(pin) },
            onBiometricUnlockRequest = {
                (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
                    com.example.security.BiometricHelper.authenticate(
                        activity = activity,
                        onSuccess = { viewModel.unlockWithBiometrics() }
                    )
                }
            }
        )
        return
    }

    if (showReviewCentre) {
        BackHandler { showReviewCentre = false }
        com.example.ui.screens.ReviewCentreScreen(
            uiState = uiState,
            onResolveReview = { id, approved, cat -> viewModel.resolveReview(id, approved, cat) },
            onResolveDiscrepancy = { id, notes -> viewModel.resolveDiscrepancy(id, notes) }
        )
        return
    }

    if (showDailyStatement) {
        BackHandler { showDailyStatement = false }
        com.example.ui.screens.DailyClosingReportScreen(
            uiState = uiState,
            onRefresh = { viewModel.refreshDailyStatement() }
        )
        return
    }

    if (showBackupVault) {
        com.example.ui.screens.BackupRestoreScreen(
            state = uiState,
            onBack = { showBackupVault = false },
            onCreateBackup = { pwd -> viewModel.createEncryptedBackup(pwd) },
            onExportBackupToStream = { pwd, stream -> viewModel.exportEncryptedBackup(pwd, stream) },
            onInspectBackup = { stream, pwd -> viewModel.inspectBackup(stream, pwd) },
            onRestoreBackup = { stream, pwd, mode -> viewModel.restoreBackup(stream, pwd, mode) },
            onDeleteBackup = { path -> viewModel.deleteLocalBackup(path) },
            onUpdateAutoBackupSchedule = { freq -> viewModel.updateAutoBackupSchedule(freq) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AbsoluteBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianBlack.copy(alpha = 0.94f),
                contentColor = SoftWhite,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(GlassBorderTop, Color.Transparent)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricIndigo,
                            selectedTextColor = ElectricIndigo,
                            unselectedIconColor = MutedSilver,
                            unselectedTextColor = MutedSilver,
                            indicatorColor = ElectricIndigo.copy(alpha = 0.22f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(
                    state = uiState,
                    onPeriodSelect = { viewModel.setPeriod(it) },
                    onRequestSmsPermission = { requestPermissions() },
                    onOpenAddManual = { showAddManual = true },
                    onOpenReconcile = { reconcileProvider = it },
                    onSelectTransaction = { selectedTransaction = it },
                    onNavigateToTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                    onOpenReviewCentre = { showReviewCentre = true },
                    onOpenDailyStatement = { showDailyStatement = true }
                )
                NavigationTab.TRANSACTIONS -> TransactionsScreen(
                    state = uiState,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onProviderFilter = { viewModel.setProviderFilter(it) },
                    onTypeFilter = { viewModel.setTypeFilter(it) },
                    onSortChange = { viewModel.setSortOrder(it) },
                    onSelectTransaction = { selectedTransaction = it }
                )
                NavigationTab.ANALYTICS -> AnalyticsScreen(
                    state = uiState,
                    onPeriodSelect = { viewModel.setPeriod(it) }
                )
                NavigationTab.ACCOUNTS -> AccountsScreen(
                    state = uiState,
                    onOpenReconcile = { reconcileProvider = it }
                )
                NavigationTab.SETTINGS -> SettingsScreen(
                    state = uiState,
                    onRequestSmsPermission = { requestPermissions() },
                    onOpenSetPin = { showSetPin = true },
                    onDisablePin = { viewModel.disablePin() },
                    onToggleBiometrics = { viewModel.setBiometricEnabled(it) },
                    onOpenBackupVault = { showBackupVault = true },
                    onExportCsv = { viewModel.exportCsv() },
                    onExportJson = { viewModel.exportJson() },
                    onImportJson = { viewModel.importJson(it) },
                    onClearAllData = { viewModel.clearAllData() }
                )
            }
        }

        // Active Dialogs
        selectedTransaction?.let { txn ->
            TransactionDetailDialog(
                transaction = txn,
                onDismiss = { selectedTransaction = null },
                onToggleInternalTransfer = { id -> viewModel.toggleInternalTransfer(id) },
                onDelete = { id -> viewModel.deleteTransaction(id) },
                onReverseTransaction = { id, reason -> viewModel.reverseTransaction(id, reason) },
                loadJournalLines = { id -> viewModel.getJournalLinesForTransaction(id) }
            )
        }

        if (showAddManual) {
            AddManualTransactionDialog(
                onDismiss = { showAddManual = false },
                onSave = { txn -> viewModel.addManualTransaction(txn) }
            )
        }

        reconcileProvider?.let { prov ->
            val acc = uiState.accounts.find { it.provider == prov }
            val reported = when (prov) {
                "AIRTEL" -> uiState.metrics.airtelReportedBalance
                "MTN" -> uiState.metrics.mtnReportedBalance
                else -> uiState.metrics.cashCalculatedBalance
            }
            val calculated = when (prov) {
                "AIRTEL" -> uiState.metrics.airtelCalculatedBalance
                "MTN" -> uiState.metrics.mtnCalculatedBalance
                else -> uiState.metrics.cashCalculatedBalance
            }
            ReconcileDialog(
                provider = prov,
                currentReported = reported,
                currentCalculated = calculated,
                onDismiss = { reconcileProvider = null },
                onSaveOpeningBalance = { bal -> viewModel.updateOpeningBalance(prov, bal) },
                onReconcile = { bal -> viewModel.reconcileAccount(prov, bal) }
            )
        }


        if (showSetPin) {
            SetPinDialog(
                onDismiss = { showSetPin = false },
                onSavePin = { pin -> viewModel.setPin(pin) }
            )
        }
    }
}
