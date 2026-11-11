package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Transaction
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.FilterChipItem
import com.example.ui.components.GlassBox
import com.example.ui.components.MetricTile
import com.example.ui.components.ProviderBadge
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.CashTeal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianCardGradient
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.RestrainedYellow
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.FilterPeriod
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: UiState,
    onPeriodSelect: (FilterPeriod) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenAddManual: () -> Unit,
    onOpenReconcile: (String) -> Unit,
    onSelectTransaction: (Transaction) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onOpenReviewCentre: () -> Unit = {},
    onOpenDailyStatement: () -> Unit = {}
) {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH)

    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header — ECHO MONEY
            item {
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
                                .background(Color(0x3314141E))
                                .border(1.dp, GlassBorderTop, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.echo_money_icon_1793380992762),
                                contentDescription = "Echo Money Emblem",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ECHO",
                                    color = SoftWhite,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MONEY",
                                    color = ElectricIndigo,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            Text(
                                text = "Black Obsidian Mobile Money Tracker",
                                color = MutedSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    // Frosted Glass SMS Listener Status Badge
                    val statusBorder = if (state.isSmsPermissionGranted) RestrainedGreen.copy(alpha = 0.5f) else RestrainedRed.copy(alpha = 0.5f)
                    val statusBg = if (state.isSmsPermissionGranted) Color(0x2010B981) else Color(0x20EF4444)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusBg)
                            .border(1.dp, statusBorder, RoundedCornerShape(20.dp))
                            .clickable(onClick = onRequestSmsPermission)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state.isSmsPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (state.isSmsPermissionGranted) RestrainedGreen else RestrainedRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isSmsPermissionGranted) "SMS Live" else "Enable SMS",
                                color = if (state.isSmsPermissionGranted) RestrainedGreen else RestrainedRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Black Obsidian Glass Combined Liquidity Hero Card
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    borderBrush = Brush.verticalGradient(
                        listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.35f), GlassBorderDefault)
                    ),
                    backgroundBrush = Brush.verticalGradient(
                        listOf(Color(0xD914141C), Color(0xCC0C0C12))
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Translucent Echo Money insignia watermark embedded in the hero card
                        Image(
                            painter = painterResource(id = R.drawable.echo_money_icon_1793380992762),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(130.dp)
                                .offset(x = 18.dp, y = (-12).dp)
                                .alpha(0.16f),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ElectricIndigo)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "COMBINED LIQUIDITY",
                                    color = MutedSilver,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x247775FF))
                                    .border(1.dp, ElectricIndigo.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ZMW",
                                    color = ElectricIndigoLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ZMW ${"%,.2f".format(state.metrics.combinedBalance)}",
                            color = SoftWhite,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Provider Mini-Glass Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProviderGlassMiniCard(
                                provider = "Airtel",
                                amount = state.metrics.airtelReportedBalance,
                                color = AirtelRed,
                                modifier = Modifier.weight(1f)
                            )
                            ProviderGlassMiniCard(
                                provider = "MTN",
                                amount = state.metrics.mtnReportedBalance,
                                color = MtnYellow,
                                modifier = Modifier.weight(1f)
                            )
                            ProviderGlassMiniCard(
                                provider = "Cash",
                                amount = state.metrics.cashCalculatedBalance,
                                color = CashTeal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

            // Review Centre Alert Banner
            val reviewCount = state.reviewItems.size + state.discrepancies.size
            if (reviewCount > 0) {
                item {
                    GlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenReviewCentre),
                        shape = RoundedCornerShape(14.dp),
                        borderBrush = Brush.horizontalGradient(listOf(Color(0xFFFFA726), ElectricIndigo.copy(alpha = 0.6f))),
                        backgroundBrush = Brush.verticalGradient(listOf(Color(0xE62A1F10), Color(0xCC1E150A)))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Review Needed",
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$reviewCount Items in Review Centre",
                                    color = SoftWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Audit unclassified activity, probable duplicates & discrepancies",
                                    color = MutedSilver,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Daily Statement Card
            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDailyStatement),
                    shape = RoundedCornerShape(16.dp),
                    backgroundBrush = Brush.verticalGradient(listOf(Color(0xCC131320), Color(0xB30C0C14))),
                    borderBrush = Brush.horizontalGradient(listOf(ElectricIndigo.copy(alpha = 0.5f), GlassBorderTop))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricIndigo.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Daily Statement",
                                tint = ElectricIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Closing Statement",
                                color = SoftWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val stmt = state.dailyStatement
                            val netFlowStr = if (stmt != null) com.example.data.model.Money.format(stmt.netExternalCashFlowMinor) else "ZMW 0.00"
                            Text(
                                text = "Net External Cash Flow: $netFlowStr • Tap for full audit",
                                color = MutedSilver,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ElectricIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Reconciliation Warning Banner (Shown only when provider reported balance differs from calculated ledger balance)
            if (state.metrics.hasReconciliationDiscrepancy) {
                item {
                    GlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenReconcile(if (kotlin.math.abs(state.metrics.airtelReconciliationDiff) > 0.01) "AIRTEL" else "MTN")
                            },
                        shape = RoundedCornerShape(14.dp),
                        borderBrush = Brush.horizontalGradient(listOf(RestrainedYellow, RestrainedRed.copy(alpha = 0.6f))),
                        backgroundBrush = Brush.verticalGradient(listOf(Color(0xE62A1F10), Color(0xCC1E150A)))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Reconciliation Discrepancy",
                                tint = RestrainedYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Balance Discrepancy Detected",
                                    color = SoftWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val diffText = buildString {
                                    if (kotlin.math.abs(state.metrics.airtelReconciliationDiff) > 0.01) {
                                        append("Airtel diff: ZMW ${"%.2f".format(state.metrics.airtelReconciliationDiff)} ")
                                    }
                                    if (kotlin.math.abs(state.metrics.mtnReconciliationDiff) > 0.01) {
                                        append("MTN diff: ZMW ${"%.2f".format(state.metrics.mtnReconciliationDiff)}")
                                    }
                                }
                                Text(
                                    text = "$diffText • Tap to reconcile opening balance",
                                    color = MutedSilver,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = RestrainedYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Quick Actions Bar (Dark Obsidian Glass Buttons)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenAddManual),
                        shape = RoundedCornerShape(12.dp),
                        backgroundBrush = Brush.linearGradient(listOf(Color(0x99181824), Color(0x7311111A)))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = RestrainedGreen, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Transaction",
                                color = SoftWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    GlassBox(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenReconcile("AIRTEL") },
                        shape = RoundedCornerShape(12.dp),
                        backgroundBrush = Brush.linearGradient(listOf(Color(0x99181824), Color(0x7311111A)))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reconcile",
                                color = SoftWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Period Selector
            item {
                Column {
                    Text(
                        text = "PERIOD SUMMARY",
                        color = MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            FilterPeriod.TODAY to "Today",
                            FilterPeriod.THIS_WEEK to "This Week",
                            FilterPeriod.THIS_MONTH to "This Month",
                            FilterPeriod.LAST_MONTH to "Last Month",
                            FilterPeriod.ALL to "All Time"
                        ).forEach { (period, label) ->
                            FilterChipItem(
                                label = label,
                                isSelected = state.selectedPeriod == period,
                                onClick = { onPeriodSelect(period) }
                            )
                        }
                    }
                }
            }

            // Financial Metrics Grid with Black Obsidian Glass Tiles
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "Total Income",
                            amount = state.metrics.periodIncome,
                            isPositive = true,
                            subtitle = "Received money",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Total Outflow",
                            amount = state.metrics.periodExpenses,
                            isPositive = false,
                            subtitle = "Payments & expenses",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "Net Cash Flow",
                            amount = state.metrics.periodNetCashFlow,
                            isPositive = state.metrics.periodNetCashFlow >= 0,
                            subtitle = "Income minus expenses",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Internal Transfers",
                            amount = state.metrics.periodInternalTransfers,
                            isPositive = null,
                            subtitle = "Airtel ⇄ MTN non-spending",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "Cash Withdrawals",
                            amount = state.metrics.periodWithdrawals,
                            isPositive = null,
                            subtitle = "Converted to cash",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Transaction Charges",
                            amount = state.metrics.periodFees,
                            isPositive = false,
                            subtitle = "Carrier fees paid",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT TRANSACTIONS",
                        color = MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier.clickable(onClick = onNavigateToTransactions),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "View All (${state.transactions.size})",
                            color = ElectricIndigoLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ElectricIndigoLight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Recent Transactions List
            if (state.transactions.isEmpty()) {
                item {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = null,
                                tint = ElectricIndigo.copy(alpha = 0.7f),
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No Transactions Recorded Yet", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Incoming Airtel & MTN MoMo SMS transaction notifications will automatically be captured and organized here in real time.",
                                color = MutedSilver,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenAddManual,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Transaction", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(state.transactions.take(6)) { txn ->
                    DashboardTransactionItem(
                        transaction = txn,
                        dateFormatted = sdf.format(Date(txn.transactionDate)),
                        onClick = { onSelectTransaction(txn) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderGlassMiniCard(
    provider: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66181822))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = provider,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "K ${"%,.2f".format(amount)}",
                color = SoftWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DashboardTransactionItem(
    transaction: Transaction,
    dateFormatted: String,
    onClick: () -> Unit
) {
    val isFailed = transaction.status == "FAILED"
    val isIncome = transaction.direction == "CREDIT" && !transaction.isInternalTransfer
    val isWithdrawal = transaction.transactionType == "CASH_WITHDRAWAL"
    val isLoan = transaction.transactionType == "LOAN_DISBURSEMENT"
    val amountColor = when {
        isFailed -> MutedSilver
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

    val counterparty = transaction.recipient?.trim()?.takeIf { it.isNotEmpty() && it != "." }
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
        borderBrush = Brush.verticalGradient(listOf(GlassBorderDefault, Color(0x08FFFFFF)))
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
                        text = counterparty,
                        color = if (isFailed) MutedSilver else SoftWhite,
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
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix ZMW ${"%,.2f".format(transaction.amount)}",
                    color = amountColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
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
                    transaction.transactionReference != null -> {
                        Text(transaction.transactionReference.take(12), color = SubtleGrey, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
