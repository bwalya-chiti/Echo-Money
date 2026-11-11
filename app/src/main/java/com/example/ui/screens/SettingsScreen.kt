package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassBox
import com.example.ui.dialogs.customTextFieldColors
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    state: UiState,
    onRequestSmsPermission: () -> Unit,
    onOpenSetPin: () -> Unit,
    onDisablePin: () -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onOpenBackupVault: () -> Unit,
    onExportCsv: suspend () -> String,
    onExportJson: suspend () -> String,
    onImportJson: suspend (String) -> Boolean,
    onClearAllData: () -> Unit,
    onRefreshDiagnostics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showAuditLogs by remember { mutableStateOf(false) }
    var showAuditEvents by remember { mutableStateOf(false) }

    val biometricStatus = remember { com.example.security.BiometricHelper.checkBiometricStatus(context) }

    val sdf = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.ENGLISH)

    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SETTINGS", color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("& TOOLS", color = ElectricIndigoLight, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("Security, SMS background sync, logs, and data exports", color = MutedGrey, fontSize = 12.sp)
                }
            }

            // Security Glass Card
            item {
                SettingsGlassCard(title = "Application Security", icon = Icons.Default.Security) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App PIN Lock", color = OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.isPinConfigured) "PIN protection is active" else "Require 4-digit PIN on app launch",
                                color = SubtleGrey,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.isPinConfigured,
                            onCheckedChange = { checked ->
                                if (checked) onOpenSetPin() else onDisablePin()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = OffWhite, checkedTrackColor = ElectricIndigo)
                        )
                    }

                    if (state.isPinConfigured) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Biometric Unlock Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Unlock", color = OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (biometricStatus) {
                                        com.example.security.BiometricStatus.AVAILABLE -> "Fingerprint & Face Recognition"
                                        com.example.security.BiometricStatus.NOT_ENROLLED -> "No fingerprints enrolled on device"
                                        com.example.security.BiometricStatus.NO_HARDWARE -> "Biometric hardware not available"
                                        com.example.security.BiometricStatus.UNAVAILABLE -> "Biometric sensor unavailable"
                                    },
                                    color = SubtleGrey,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = state.isBiometricConfigured,
                                enabled = biometricStatus == com.example.security.BiometricStatus.AVAILABLE,
                                onCheckedChange = { onToggleBiometrics(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = OffWhite, checkedTrackColor = ElectricIndigo)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onOpenSetPin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Security PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // SMS Ingestion Card
            item {
                SettingsGlassCard(title = "SMS Background Ingestion", icon = Icons.Default.Sms) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isSmsPermissionGranted) "SMS Permission Active" else "SMS Permission Needed",
                                color = if (state.isSmsPermissionGranted) RestrainedGreen else RestrainedRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Captures Airtel Money and MTN MoMo incoming transaction SMS messages automatically.",
                                color = SubtleGrey,
                                fontSize = 12.sp
                            )
                        }

                        if (!state.isSmsPermissionGranted) {
                            Button(
                                onClick = onRequestSmsPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RestrainedGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Private & Local: SMS messages are parsed entirely on-device.\n• Operates via Android Telephony.SMS_RECEIVED receiver.\n• For instant background detection, ensure battery optimization is set to 'Unrestricted'.",
                        color = MutedGrey.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }


            // SMS Audit Log
            item {
                SettingsGlassCard(title = "SMS Audit Log (${state.smsLogs.size})", icon = Icons.Default.Sms) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.smsLogs.isEmpty()) "No processed SMS messages logged." else "View hashes & parsing audit trail",
                            color = SubtleGrey,
                            fontSize = 12.sp
                        )
                        if (state.smsLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showAuditLogs = !showAuditLogs },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (showAuditLogs) "Collapse" else "Expand (${state.smsLogs.size})", fontSize = 11.sp)
                            }
                        }
                    }

                    if (showAuditLogs) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.smsLogs.take(10).forEach { log ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x330E1424))
                                        .border(1.dp, GlassBorderDefault, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${log.provider} • ${log.processingStatus}",
                                                color = if (log.processingStatus == "SUCCESS") RestrainedGreen else RestrainedRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = sdf.format(Date(log.receivedAt)),
                                                color = SubtleGrey,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.rawBody,
                                            color = MutedGrey,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 2
                                        )
                                        if (log.errorMessage != null) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(log.errorMessage, color = RestrainedRed, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Encrypted Backup & Restore Vault
            item {
                SettingsGlassCard(title = "Encrypted Backup & Restore", icon = Icons.Default.Security) {
                    Text(
                        text = "Protect your financial records with AES-256-GCM encryption, automatic scheduled background backups, and tamper-proof verification.",
                        color = SubtleGrey,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33121526))
                                .border(1.dp, GlassBorderDefault, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${state.localBackups.size}", color = OffWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Snapshots", color = SubtleGrey, fontSize = 10.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33121526))
                                .border(1.dp, GlassBorderDefault, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.autoBackupSchedule, color = ElectricIndigoLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Auto Backup", color = SubtleGrey, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenBackupVault,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Backup & Restore Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val csv = onExportCsv()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                        type = "text/csv"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Echo Money CSV")
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Share CSV", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = onExportJson()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        type = "application/json"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Echo Money JSON Backup")
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Share JSON", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Danger Zone
            item {
                SettingsGlassCard(title = "Data Reset", icon = Icons.Default.DeleteSweep) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear All Data", color = RestrainedRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Erase all transactions, SMS audit logs, and reset balances", color = SubtleGrey, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x26EF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RestrainedRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear", color = RestrainedRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // App Information Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ECHO MONEY v1.0", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Designed for Airtel Money & MTN Mobile Money Zambia", color = MutedSilver, fontSize = 11.sp)
                    Text("Black Obsidian Glass Edition • Offline-first • Zero cloud trackers", color = SubtleGrey.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Erase All Data?", color = OffWhite) },
            text = { Text("This will permanently remove all stored transactions, SMS logs, and reset account balances.", color = MutedGrey) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearConfirm = false
                        Toast.makeText(context, "All data has been cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RestrainedRed)
                ) {
                    Text("Yes, Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            },
            containerColor = Color(0xFF131B2E)
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import JSON Backup", color = OffWhite) },
            text = {
                Column {
                    Text("Paste your exported Echo Money JSON backup below:", color = MutedSilver, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        colors = customTextFieldColors(),
                        placeholder = { Text("{ \"transactions\": [ ... ] }", color = SubtleGrey) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = onImportJson(importJsonText)
                            showImportDialog = false
                            if (success) {
                                Toast.makeText(context, "Import successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Import failed: Invalid JSON format", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = MutedGrey)
                }
            },
            containerColor = Color(0xFF131B2E)
        )
    }
}

@Composable
private fun SettingsGlassCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
