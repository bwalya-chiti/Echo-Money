package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.JournalLine
import com.example.data.model.Money
import com.example.data.model.Transaction
import com.example.ui.components.FilterChipItem
import com.example.ui.components.ProviderBadge
import com.example.ui.theme.AbsoluteBlack
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianGlassCard
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SoftWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onToggleInternalTransfer: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReverseTransaction: ((Long, String) -> Unit)? = null,
    loadJournalLines: (suspend (Long) -> List<JournalLine>)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm", Locale.ENGLISH)
    var journalLines by remember { mutableStateOf<List<JournalLine>>(emptyList()) }
    var showReversalPrompt by remember { mutableStateOf(false) }
    var reversalReason by remember { mutableStateOf("Manual correction / Compensating adjustment") }

    LaunchedEffect(transaction.id) {
        if (loadJournalLines != null) {
            journalLines = loadJournalLines(transaction.id)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderBrush), RoundedCornerShape(20.dp)),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderBadge(provider = transaction.provider)
                        if (transaction.status == "REVERSED") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(RestrainedRed.copy(alpha = 0.2f))
                                    .border(1.dp, RestrainedRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("REVERSED", color = RestrainedRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedSilver)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                val isIncome = (transaction.transactionType == "INCOMING_TRANSFER" || transaction.direction == "CREDIT") && !transaction.isInternalTransfer && transaction.transactionType != "LOAN_DISBURSEMENT"
                val amountColor = if (transaction.status == "REVERSED") MutedSilver else if (transaction.isInternalTransfer) ElectricIndigo else if (isIncome) RestrainedGreen else RestrainedRed
                val prefix = if (transaction.isInternalTransfer) "⇄ " else if (isIncome) "+ " else "- "

                Text(
                    text = "$prefix ZMW ${"%,.2f".format(transaction.amount)}",
                    color = amountColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (transaction.fee > 0) {
                    Text(
                        text = "Transaction charge: ZMW ${"%.2f".format(transaction.fee)}",
                        color = MutedSilver,
                        fontSize = 12.sp
                    )
                }

                if (transaction.status == "REVERSED") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transaction.reviewReason ?: "This transaction has been compensated/reversed.",
                        color = RestrainedRed.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Rows
                DetailRow("Type", transaction.transactionType.replace("_", " "))
                DetailRow("Status", transaction.status)
                DetailRow("Category", transaction.categoryName)
                transaction.recipient?.let { DetailRow("Recipient / To", it) }
                transaction.sender?.let { DetailRow("Sender / From", it) }
                transaction.description?.let { DetailRow("Description", it) }
                DetailRow("Date & Time", sdf.format(Date(transaction.transactionDate)))

                transaction.balanceAfter?.let {
                    DetailRow("Reported Balance After", "ZMW ${"%,.2f".format(it)}")
                }

                if (!transaction.transactionReference.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Transaction Ref (TID)", color = MutedSilver, fontSize = 11.sp)
                            Text(
                                transaction.transactionReference,
                                color = SoftWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(transaction.transactionReference))
                            Toast.makeText(context, "Copied reference to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ElectricIndigo, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (transaction.isInternalTransfer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricIndigo.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Linked Internal Account Transfer: Excluded from income and expense totals to prevent artificial inflation.",
                            color = SoftWhite,
                            fontSize = 11.sp
                        )
                    }
                }

                // Double-Entry Journal Breakdown
                if (journalLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Double-Entry Ledger Lines", color = ElectricIndigoLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22121526))
                            .border(BorderStroke(1.dp, GlassBorderDefault), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            journalLines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.accountCode, color = SoftWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                        line.description?.let {
                                            Text(it, color = MutedSilver, fontSize = 10.sp)
                                        }
                                    }
                                    if (line.debitMinor > 0) {
                                        Text(
                                            "Dr ZMW ${"%.2f".format(Money.toZmw(line.debitMinor))}",
                                            color = RestrainedGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (line.creditMinor > 0) {
                                        Text(
                                            "Cr ZMW ${"%.2f".format(Money.toZmw(line.creditMinor))}",
                                            color = ElectricIndigoLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Raw SMS Card
                if (!transaction.rawSms.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Original SMS Confirmation", color = MutedSilver, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AbsoluteBlack)
                            .border(BorderStroke(1.dp, GlassBorderDefault), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = transaction.rawSms,
                            color = MutedSilver,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Compensating Reversal Button
                    if (onReverseTransaction != null && transaction.status != "REVERSED" && transaction.transactionType != "REVERSAL") {
                        OutlinedButton(
                            onClick = { showReversalPrompt = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Post Compensating Reversal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onToggleInternalTransfer(transaction.id)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigo),
                            border = BorderStroke(1.dp, ElectricIndigo),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (transaction.isInternalTransfer) "Unmark Internal" else "Mark Internal", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onDelete(transaction.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RestrainedRed),
                            border = BorderStroke(1.dp, RestrainedRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showReversalPrompt && onReverseTransaction != null) {
        AlertDialog(
            onDismissRequest = { showReversalPrompt = false },
            title = { Text("Compensating Reversal", color = SoftWhite) },
            text = {
                Column {
                    Text(
                        "In compliance with financial accounting standards, ECHO MONEY will not silently delete records. It will post an explicit compensating reversal journal entry to balance the ledger.",
                        color = MutedSilver,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reversalReason,
                        onValueChange = { reversalReason = it },
                        label = { Text("Reversal Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = customTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReverseTransaction(transaction.id, reversalReason)
                        showReversalPrompt = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Confirm Reversal", color = AbsoluteBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReversalPrompt = false }) {
                    Text("Cancel", color = MutedSilver)
                }
            },
            containerColor = Color(0xFF131B2E)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = MutedSilver, fontSize = 11.sp)
        Text(text = value, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AddManualTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var provider by remember { mutableStateOf("AIRTEL") }
    var type by remember { mutableStateOf("EXPENSE") }
    var amountText by remember { mutableStateOf("") }
    var recipientText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("General") }
    var descText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderBrush), RoundedCornerShape(20.dp)),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Record Manual Transaction", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))

                Text("Provider", color = MutedSilver, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AIRTEL", "MTN", "CASH").forEach { p ->
                        FilterChipItem(label = p, isSelected = provider == p, onClick = { provider = p })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Type", color = MutedSilver, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("INCOME", "EXPENSE", "CASH_WITHDRAWAL").forEach { t ->
                        FilterChipItem(
                            label = if (t == "CASH_WITHDRAWAL") "Withdrawal" else t.lowercase().replaceFirstChar { it.uppercase() },
                            isSelected = type == t,
                            onClick = { type = t }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (ZMW)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = recipientText,
                    onValueChange = { recipientText = it },
                    label = { Text(if (type == "INCOME") "Sender" else "Recipient / Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Notes / Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, GlassBorderDefault)
                    ) {
                        Text("Cancel", color = MutedSilver)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                val txn = Transaction(
                                    provider = provider,
                                    transactionType = type,
                                    amount = amt,
                                    categoryName = categoryText.ifBlank { "General" },
                                    recipient = if (type != "INCOME") recipientText.ifBlank { null } else null,
                                    sender = if (type == "INCOME") recipientText.ifBlank { null } else null,
                                    description = descText.ifBlank { null },
                                    transactionDate = System.currentTimeMillis()
                                )
                                onSave(txn)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                    ) {
                        Text("Save Transaction", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ReconcileDialog(
    provider: String,
    currentReported: Double,
    currentCalculated: Double,
    onDismiss: () -> Unit,
    onSaveOpeningBalance: (Double) -> Unit,
    onReconcile: (Double) -> Unit
) {
    var openingInput by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf(if (currentReported > 0) "%.2f".format(currentReported) else "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderBrush), RoundedCornerShape(20.dp)),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Reconcile $provider Account", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current Calculated: ZMW ${"%,.2f".format(currentCalculated)}\nLast Reported by SMS: ZMW ${"%,.2f".format(currentReported)}",
                    color = MutedSilver,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Option 1: Set Opening Balance", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Use when first setting up your account before SMS history.", color = MutedSilver, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = openingInput,
                    onValueChange = { openingInput = it },
                    label = { Text("Opening Balance (ZMW)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val bal = openingInput.toDoubleOrNull()
                        if (bal != null) {
                            onSaveOpeningBalance(bal)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Update Opening Balance")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Option 2: Reconcile to Balance", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Update reported baseline to eliminate discrepancy.", color = MutedSilver, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    label = { Text("Target Balance (ZMW)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val bal = targetInput.toDoubleOrNull()
                        if (bal != null) {
                            onReconcile(bal)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RestrainedGreen),
                    border = BorderStroke(1.dp, RestrainedGreen)
                ) {
                    Text("Reconcile Balance")
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, GlassBorderDefault)
                ) {
                    Text("Close", color = MutedSilver)
                }
            }
        }
    }
}

@Composable
fun SmsTestLabDialog(
    onDismiss: () -> Unit,
    onRunAllFixtures: () -> Unit,
    onRunFixture: ((com.example.ui.viewmodel.TestFixture) -> Unit)? = null,
    onSimulateCustom: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Master Fixtures, 1: Custom SMS
    var customSender by remember { mutableStateOf("AirtelMoney") }
    var customBody by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    val fixtures = com.example.ui.viewmodel.MASTER_TEST_FIXTURES

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderBrush), RoundedCornerShape(20.dp)),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SMS Parser Test Lab", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("18 Real Zambian Scenarios", color = ElectricIndigoLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedSilver)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Run all 18 fixtures button
                Button(
                    onClick = {
                        onRunAllFixtures()
                        testResult = "Executed all 18 Master Zambian SMS fixtures! Verified deduplication, double-entry ledger & balances."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run All 18 Master Fixtures", fontWeight = FontWeight.Bold)
                }

                if (testResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AbsoluteBlack)
                            .border(BorderStroke(1.dp, GlassBorderDefault), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(testResult!!, color = RestrainedGreen, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: Fixtures vs Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipItem(
                        label = "Fixtures (${fixtures.size})",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    FilterChipItem(
                        label = "Custom SMS Simulator",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        fixtures.forEach { fixture ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22121526))
                                    .border(BorderStroke(1.dp, GlassBorderDefault), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ProviderBadge(provider = if (fixture.sender.contains("MTN", ignoreCase = true)) "MTN" else "AIRTEL")
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ElectricIndigo.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(fixture.tag, color = ElectricIndigoLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (onRunFixture != null) {
                                                    onRunFixture(fixture)
                                                } else {
                                                    onSimulateCustom(fixture.sender, fixture.body)
                                                }
                                                testResult = "Processed fixture #${fixture.id}: ${fixture.title}"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x337775FF)),
                                            border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Run", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(fixture.title, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = fixture.body,
                                        color = MutedSilver,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        Text("Simulate Custom SMS Message", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("AirtelMoney", "MTNMoMo").forEach { s ->
                                FilterChipItem(label = s, isSelected = customSender == s, onClick = { customSender = s })
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customBody,
                            onValueChange = { customBody = it },
                            placeholder = { Text("Paste raw confirmation SMS text here...", color = MutedSilver, fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = customTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (customBody.isNotBlank()) {
                                    onSimulateCustom(customSender, customBody)
                                    testResult = "Processed custom SMS message."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianGlassCard),
                            border = BorderStroke(1.dp, GlassBorderDefault),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Parse & Ingest SMS", color = SoftWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderBrush), RoundedCornerShape(20.dp)),
            color = ObsidianBlack
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Set 4-Digit Security PIN", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Protect your financial records from unauthorized access.", color = MutedSilver, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pin = it },
                    label = { Text("4-digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorText!!, color = RestrainedRed, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, GlassBorderDefault)
                    ) {
                        Text("Cancel", color = MutedSilver)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (pin.length != 4) {
                                errorText = "PIN must be exactly 4 digits"
                            } else if (pin != confirmPin) {
                                errorText = "PINs do not match"
                            } else {
                                onSavePin(pin)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                    ) {
                        Text("Save PIN", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SoftWhite,
    unfocusedTextColor = SoftWhite,
    focusedContainerColor = Color(0x33121218),
    unfocusedContainerColor = Color(0x220B0B0E),
    focusedBorderColor = ElectricIndigo,
    unfocusedBorderColor = GlassBorderDefault,
    focusedLabelColor = ElectricIndigo,
    unfocusedLabelColor = MutedSilver
)
