package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Transaction
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.FilterChipItem
import com.example.ui.components.GlassBox
import com.example.ui.components.ProviderBadge
import com.example.ui.dialogs.customTextFieldColors
import com.example.ui.theme.CashTeal
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.RestrainedYellow
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.SortOrder
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    state: UiState,
    onSearchChange: (String) -> Unit,
    onProviderFilter: (String) -> Unit,
    onTypeFilter: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onSelectTransaction: (Transaction) -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    var sortMenuExpanded by remember { mutableStateOf(false) }

    AtmosphericGlassBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Glass Top Bar with Search & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x3D131B2E))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))),
                        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    )
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search by name, TID, amount...", color = SubtleGrey, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MutedGrey, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = customTextFieldColors()
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x401E293B))
                                .border(1.dp, GlassBorderDefault, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = OffWhite)
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest First") },
                                onClick = { onSortChange(SortOrder.NEWEST); sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest First") },
                                onClick = { onSortChange(SortOrder.OLDEST); sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Highest Amount") },
                                onClick = { onSortChange(SortOrder.HIGHEST); sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Lowest Amount") },
                                onClick = { onSortChange(SortOrder.LOWEST); sortMenuExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Translucent Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All Providers", "AIRTEL" to "Airtel", "MTN" to "MTN", "CASH" to "Cash").forEach { (code, label) ->
                        FilterChipItem(
                            label = label,
                            isSelected = state.providerFilter == code,
                            onClick = { onProviderFilter(code) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(Color(0x33FFFFFF))
                            .align(Alignment.CenterVertically)
                    )

                    listOf(
                        "ALL" to "All Types",
                        "INCOME" to "Income",
                        "EXPENSE" to "Expenses",
                        "INTERNAL" to "Internal ⇄",
                        "WITHDRAWAL" to "Withdrawals"
                    ).forEach { (code, label) ->
                        FilterChipItem(
                            label = label,
                            isSelected = state.typeFilter == code,
                            onClick = { onTypeFilter(code) }
                        )
                    }
                }
            }

            // Count & Volume Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.filteredTransactions.size} transactions found",
                    color = SubtleGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                val totalAmount = state.filteredTransactions.sumOf { it.amount }
                Text(
                    text = "Total: ZMW ${"%,.2f".format(totalAmount)}",
                    color = OffWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Transactions List with Glass Cards
            if (state.filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = SubtleGrey, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Matching Transactions", color = OffWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Try resetting filters or search query.", color = MutedGrey, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filteredTransactions, key = { it.id }) { txn ->
                        TransactionListItem(
                            transaction = txn,
                            dateFormatted = sdf.format(Date(txn.transactionDate)),
                            onClick = { onSelectTransaction(txn) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: Transaction,
    dateFormatted: String,
    onClick: () -> Unit
) {
    val isFailed = transaction.status == "FAILED"
    val isIncome = transaction.direction == "CREDIT" && !transaction.isInternalTransfer
    val isWithdrawal = transaction.transactionType == "CASH_WITHDRAWAL"
    val isLoan = transaction.transactionType == "LOAN_DISBURSEMENT"
    val amountColor = when {
        isFailed -> MutedGrey
        transaction.isInternalTransfer -> ElectricIndigoLight
        isWithdrawal -> CashTeal
        isIncome -> RestrainedGreen
        else -> RestrainedRed
    }
    val prefix = when {
        isFailed -> "✕ "
        transaction.isInternalTransfer -> "⇄ "
        isWithdrawal -> "⇊ "
        isIncome -> "+ "
        else -> "- "
    }

    val title = transaction.recipient?.trim()?.takeIf { it.isNotEmpty() && it != "." }
        ?: transaction.sender?.trim()?.takeIf { it.isNotEmpty() && it != "." }
        ?: transaction.merchantOrAgent?.trim()?.takeIf { it.isNotEmpty() && it != "." }
        ?: transaction.description?.trim()?.takeIf { it.isNotEmpty() && it != "." }
        ?: transaction.categoryName.trim().takeIf { it.isNotEmpty() && it != "." }
        ?: when (transaction.transactionType) {
            "CASH_WITHDRAWAL" -> "Cash Withdrawal"
            "LOAN_DISBURSEMENT" -> "Loan Advance"
            "LOAN_REPAYMENT" -> "Loan Repayment"
            "MERCHANT_PAYMENT" -> "Merchant Payment"
            "INTERNAL_TRANSFER" -> "Internal Transfer"
            "INCOMING_TRANSFER" -> "Received Transfer"
            "OUTGOING_TRANSFER" -> "Sent Transfer"
            "FEE" -> "Transaction Charge"
            "REVERSAL" -> "Reversal"
            "BALANCE_UPDATE" -> "Balance Check"
            else -> if (transaction.direction == "CREDIT") "Received Money" else "Mobile Payment"
        }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        borderBrush = Brush.verticalGradient(listOf(GlassBorderDefault, Color(0x0AFFFFFF)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderBadge(provider = transaction.provider)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = if (isFailed) MutedSilver else OffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${transaction.categoryName} • $dateFormatted",
                        color = SubtleGrey,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!transaction.transactionReference.isNullOrBlank()) {
                        Text(
                            text = "TID: ${transaction.transactionReference}",
                            color = MutedGrey,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix ZMW ${"%,.2f".format(transaction.amount)}",
                    color = amountColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                when {
                    isFailed -> {
                        Text("FAILED", color = RestrainedRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    transaction.isInternalTransfer -> {
                        Text("Internal Transfer", color = ElectricIndigoLight, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    isWithdrawal -> {
                        Text("Cash Out", color = CashTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    isLoan -> {
                        Text("Loan Advance", color = RestrainedYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    transaction.fee > 0 -> {
                        Text("+ K${"%.2f".format(transaction.fee)} fee", color = SubtleGrey, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

