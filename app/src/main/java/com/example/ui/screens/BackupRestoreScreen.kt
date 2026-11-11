package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.backup.BackupInspection
import com.example.backup.LocalBackupFile
import com.example.backup.RestoreMode
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassBox
import com.example.ui.dialogs.customTextFieldColors
import com.example.ui.theme.AbsoluteBlack
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.RestrainedYellow
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    state: UiState,
    onBack: () -> Unit,
    onCreateBackup: (String) -> Unit,
    onExportBackupToStream: (String, java.io.OutputStream) -> Unit,
    onInspectBackup: suspend (InputStream, String) -> Result<BackupInspection>,
    onRestoreBackup: (InputStream, String, RestoreMode) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onUpdateAutoBackupSchedule: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPasswordForRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreStreamSupplier by remember { mutableStateOf<(() -> InputStream)?>(null) }
    var pendingInspection by remember { mutableStateOf<BackupInspection?>(null) }
    var pendingPassword by remember { mutableStateOf("") }
    var showInspectionConfirmModal by remember { mutableStateOf(false) }
    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.FULL_REPLACE) }

    var deleteConfirmPath by remember { mutableStateOf<String?>(null) }

    // SAF Document Launchers
    var exportPasswordToSave by remember { mutableStateOf("") }
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && exportPasswordToSave.isNotBlank()) {
            try {
                val outStream = context.contentResolver.openOutputStream(uri)
                if (outStream != null) {
                    onExportBackupToStream(exportPasswordToSave, outStream)
                } else {
                    Toast.makeText(context, "Could not open destination storage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreStreamSupplier = {
                context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open input stream")
            }
            showPasswordForRestoreDialog = true
        }
    }

    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)

    AtmosphericGlassBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("backup_restore_screen"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Back button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x26FFFFFF))
                                .border(1.dp, GlassBorderDefault, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = OffWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "BACKUP & RESTORE",
                                    color = OffWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "VAULT",
                                    color = ElectricIndigoLight,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                "AES-256-GCM encrypted, zero-leak financial snapshots",
                                color = MutedGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Security & Vault Status Glass Card
                item {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x337775FF))
                                            .border(1.dp, ElectricIndigoLight.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = ElectricIndigoLight,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "ENCRYPTION ACTIVE",
                                            color = RestrainedGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            "AES-256-GCM + PBKDF2",
                                            color = OffWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x3310B981))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "100% OFFLINE",
                                        color = RestrainedGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VaultStatBox(
                                    label = "Protected TXNs",
                                    value = "${state.transactions.size}",
                                    modifier = Modifier.weight(1f)
                                )
                                VaultStatBox(
                                    label = "Auto Schedule",
                                    value = state.autoBackupSchedule,
                                    modifier = Modifier.weight(1f)
                                )
                                VaultStatBox(
                                    label = "Vault Snapshots",
                                    value = "${state.localBackups.size}",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Backups (.echomoney) contain your complete ledger, accounts, categories, and settings encrypted with your secret password. Without your password, files are mathematically unreadable.",
                                color = SubtleGrey,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Primary Action Buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Create Backup Button
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("create_backup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Create Encrypted Backup (.echomoney)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Restore Backup Button
                        OutlinedButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("restore_backup_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Restore / Import from File",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Auto-Backup Configuration Card
                item {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Automated Background Backups",
                                    color = OffWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Android WorkManager automatically writes encrypted snapshots and rotates the 5 most recent copies without interrupting app usage.",
                                color = SubtleGrey,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("DAILY", "WEEKLY", "MONTHLY", "DISABLED").forEach { freq ->
                                    val isSelected = state.autoBackupSchedule.equals(freq, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) ElectricIndigo else Color(0x33141420))
                                            .border(
                                                1.dp,
                                                if (isSelected) ElectricIndigoLight else GlassBorderDefault,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onUpdateAutoBackupSchedule(freq) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = freq,
                                            color = if (isSelected) SoftWhite else MutedSilver,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (state.lastAutoBackupTimestamp != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Last automatic backup: ${sdf.format(Date(state.lastAutoBackupTimestamp))}",
                                    color = RestrainedGreen,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Local Backup Vault Snapshots
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "LOCAL VAULT SNAPSHOTS (${state.localBackups.size})",
                            color = MutedSilver,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (state.localBackups.isEmpty()) {
                    item {
                        GlassBox(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = SubtleGrey, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No local backups created yet", color = MutedSilver, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Create your first encrypted snapshot above", color = SubtleGrey, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    items(state.localBackups) { backup ->
                        LocalBackupItemCard(
                            backup = backup,
                            onRestore = {
                                pendingRestoreStreamSupplier = { FileInputStream(File(backup.filePath)) }
                                showPasswordForRestoreDialog = true
                            },
                            onShare = {
                                val file = File(backup.filePath)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Echo Money Backup"))
                            },
                            onDelete = { deleteConfirmPath = backup.filePath }
                        )
                    }
                }
            }

            // Busy Progress Overlay
            if (state.isBackupBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC050505)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricIndigoLight, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.backupProgressMessage ?: "Processing encrypted vault...",
                            color = OffWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // CREATE BACKUP DIALOG
    if (showCreateDialog) {
        CreateBackupDialog(
            onDismiss = { showCreateDialog = false },
            onConfirmLocal = { pwd ->
                showCreateDialog = false
                onCreateBackup(pwd)
            },
            onConfirmExport = { pwd ->
                showCreateDialog = false
                exportPasswordToSave = pwd
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportDocumentLauncher.launch("echo_money_backup_$timeStamp.echomoney")
            }
        )
    }

    // RESTORE PASSWORD DIALOG
    if (showPasswordForRestoreDialog) {
        RestorePasswordDialog(
            onDismiss = {
                showPasswordForRestoreDialog = false
                pendingRestoreStreamSupplier = null
            },
            onInspect = { pwd ->
                val supplier = pendingRestoreStreamSupplier
                if (supplier != null) {
                    scope.launch {
                        try {
                            val stream = withContext(Dispatchers.IO) { supplier() }
                            val res = onInspectBackup(stream, pwd)
                            if (res.isSuccess) {
                                pendingInspection = res.getOrNull()
                                pendingPassword = pwd
                                showPasswordForRestoreDialog = false
                                showInspectionConfirmModal = true
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "Wrong password or corrupted backup"
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Inspection error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // PRE-RESTORE INSPECTION & CONFIRMATION MODAL
    if (showInspectionConfirmModal && pendingInspection != null) {
        val inspection = pendingInspection!!
        PreRestoreInspectionDialog(
            inspection = inspection,
            selectedMode = selectedRestoreMode,
            onModeSelect = { selectedRestoreMode = it },
            onDismiss = {
                showInspectionConfirmModal = false
                pendingInspection = null
                pendingPassword = ""
                pendingRestoreStreamSupplier = null
            },
            onConfirmRestore = {
                val supplier = pendingRestoreStreamSupplier
                val pwd = pendingPassword
                val mode = selectedRestoreMode
                showInspectionConfirmModal = false
                pendingInspection = null
                pendingPassword = ""
                pendingRestoreStreamSupplier = null

                if (supplier != null) {
                    try {
                        val stream = supplier()
                        onRestoreBackup(stream, pwd, mode)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open backup stream: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    if (deleteConfirmPath != null) {
        val path = deleteConfirmPath!!
        AlertDialog(
            onDismissRequest = { deleteConfirmPath = null },
            title = { Text("Delete Backup Snapshot?", color = OffWhite) },
            text = { Text("Are you sure you want to permanently delete this encrypted backup file from your device?", color = MutedSilver, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBackup(path)
                        deleteConfirmPath = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RestrainedRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmPath = null }) {
                    Text("Cancel", color = MutedSilver)
                }
            },
            containerColor = Color(0xFF10131E)
        )
    }
}

@Composable
private fun VaultStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33121526))
            .border(1.dp, GlassBorderDefault, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = SubtleGrey, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LocalBackupItemCard(
    backup: LocalBackupFile,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    val sizeKb = (backup.sizeBytes / 1024.0)

    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (backup.isAuto) Icons.Default.AccessTime else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (backup.isAuto) ElectricIndigoLight else RestrainedGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (backup.isAuto) "AUTO SNAPSHOT" else "MANUAL BACKUP",
                        color = if (backup.isAuto) ElectricIndigoLight else RestrainedGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    "%.1f KB".format(sizeKb),
                    color = SubtleGrey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = sdf.format(Date(backup.timestamp)),
                color = OffWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = backup.fileName,
                color = SubtleGrey,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26EF4444))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RestrainedRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun CreateBackupDialog(
    onDismiss: () -> Unit,
    onConfirmLocal: (String) -> Unit,
    onConfirmExport: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val strengthColor = when {
        password.length >= 8 -> RestrainedGreen
        password.length >= 5 -> RestrainedYellow
        else -> RestrainedRed
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = ElectricIndigoLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Encrypt Backup Vault", color = OffWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set a password to protect your financial records with AES-256-GCM encryption.",
                    color = MutedSilver,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Backup Password", color = SubtleGrey) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SubtleGrey
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Password", color = SubtleGrey) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors(),
                    singleLine = true
                )

                if (password.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(strengthColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (password.length >= 8) "Strong Key" else if (password.length >= 5) "Moderate Key" else "Short Key",
                            color = strengthColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26EF4444))
                        .border(1.dp, RestrainedRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RestrainedRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CRITICAL: If you forget this password, your backup CANNOT be recovered or restored by anyone.",
                            color = RestrainedRed,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = RestrainedRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        if (password.length < 4) {
                            errorMessage = "Password must be at least 4 characters"
                        } else if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                        } else {
                            onConfirmLocal(password)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save to Local Vault")
                }

                OutlinedButton(
                    onClick = {
                        if (password.length < 4) {
                            errorMessage = "Password must be at least 4 characters"
                        } else if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                        } else {
                            onConfirmExport(password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight)
                ) {
                    Text("Export via File Picker (SAF)")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MutedSilver)
            }
        },
        containerColor = Color(0xFF10131E)
    )
}

@Composable
fun RestorePasswordDialog(
    onDismiss: () -> Unit,
    onInspect: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricIndigoLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enter Backup Password", color = OffWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the encryption passphrase used when creating this .echomoney backup.",
                    color = MutedSilver,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = SubtleGrey) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SubtleGrey
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onInspect(password) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                enabled = password.isNotBlank()
            ) {
                Text("Decrypt & Inspect")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = MutedSilver)
            }
        },
        containerColor = Color(0xFF10131E)
    )
}

@Composable
fun PreRestoreInspectionDialog(
    inspection: BackupInspection,
    selectedMode: RestoreMode,
    onModeSelect: (RestoreMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirmRestore: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    val summary = inspection.summary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = RestrainedGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify & Confirm Restore", color = OffWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Verified summary box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3312172A))
                        .border(1.dp, GlassBorderDefault, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Backup Created:", color = SubtleGrey, fontSize = 11.sp)
                            Text(sdf.format(Date(inspection.manifest.timestamp)), color = OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Transactions:", color = SubtleGrey, fontSize = 11.sp)
                            Text("${summary.totalTransactions}", color = ElectricIndigoLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Airtel Balance:", color = SubtleGrey, fontSize = 11.sp)
                            Text("ZMW %.2f".format(summary.airtelBalance), color = OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MTN MoMo Balance:", color = SubtleGrey, fontSize = 11.sp)
                            Text("ZMW %.2f".format(summary.mtnBalance), color = OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Choose Restoration Mode:", color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Mode 1: Full Replace
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedMode == RestoreMode.FULL_REPLACE) Color(0x337775FF) else Color(0x1AFFFFFF))
                        .border(1.dp, if (selectedMode == RestoreMode.FULL_REPLACE) ElectricIndigoLight else GlassBorderDefault, RoundedCornerShape(10.dp))
                        .clickable { onModeSelect(RestoreMode.FULL_REPLACE) }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.FULL_REPLACE,
                            onClick = { onModeSelect(RestoreMode.FULL_REPLACE) },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricIndigoLight)
                        )
                        Column {
                            Text("Full Restore (Clean Replace)", color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Replaces active database completely. A pre-restore safety snapshot is created automatically for rollback protection.", color = SubtleGrey, fontSize = 10.sp)
                        }
                    }
                }

                // Mode 2: Selective Merge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedMode == RestoreMode.SELECTIVE_MERGE) Color(0x337775FF) else Color(0x1AFFFFFF))
                        .border(1.dp, if (selectedMode == RestoreMode.SELECTIVE_MERGE) ElectricIndigoLight else GlassBorderDefault, RoundedCornerShape(10.dp))
                        .clickable { onModeSelect(RestoreMode.SELECTIVE_MERGE) }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.SELECTIVE_MERGE,
                            onClick = { onModeSelect(RestoreMode.SELECTIVE_MERGE) },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricIndigoLight)
                        )
                        Column {
                            Text("Selective Merge", color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Appends non-duplicate transactions while preserving your current accounts and recent messages.", color = SubtleGrey, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRestore,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
            ) {
                Text("Confirm & Restore")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = MutedSilver)
            }
        },
        containerColor = Color(0xFF10131E)
    )
}
