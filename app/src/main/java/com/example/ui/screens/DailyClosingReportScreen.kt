package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.accounting.DailyFinancialStatement
import com.example.data.model.Money
import com.example.ui.theme.ElectricIndigo
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyClosingReportScreen(
    uiState: UiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statement = uiState.dailyStatement
    val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH)
    val dateStr = sdf.format(Date(statement?.dateTimestamp ?: System.currentTimeMillis()))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050505)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("daily_closing_report_screen")
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ElectricIndigo.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, ElectricIndigo.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Daily Statement",
                        tint = ElectricIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Daily Financial Statement",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateStr,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_daily_statement")
                ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = ElectricIndigo
                )
            }
        }

        if (statement == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Generating statement from ledger...", color = Color.White.copy(alpha = 0.6f))
            }
            return
        }

        // Consolidated Opening & Closing Position
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricIndigo.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CONSOLIDATED WALLET POSITION",
                    color = ElectricIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Day Opening", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text(
                            text = Money.format(statement.openingBalanceTotalMinor),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Day Closing", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text(
                            text = Money.format(statement.closingBalanceTotalMinor),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Net Flow Definitions (Master Spec Section 20)
        Text(
            text = "NET FLOW MEASURES",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowMetricCard(
                title = "External Cash Flow",
                amountMinor = statement.netExternalCashFlowMinor,
                subtitle = "Credits - Debits (External)",
                modifier = Modifier.weight(1f)
            )
            FlowMetricCard(
                title = "Actual Expenditure",
                amountMinor = statement.actualExpenditureMinor,
                subtitle = "Consumption Only",
                isNegativeBad = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowMetricCard(
                title = "Earned Income",
                amountMinor = statement.earnedIncomeMinor,
                subtitle = "Excl. loans & refunds",
                modifier = Modifier.weight(1f)
            )
            FlowMetricCard(
                title = "Net Account Movement",
                amountMinor = statement.netAccountMovementMinor,
                subtitle = "Closing - Opening",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detailed Movement Breakdown
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TRANSACTION MOVEMENT AUDIT",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                BreakdownRow("External Money Received", statement.externalMoneyReceivedMinor, isCredit = true)
                BreakdownRow("Loan Proceeds (Liabilities Incurred)", statement.loanProceedsMinor, isCredit = true, isSpecial = true)
                BreakdownRow("Refunds & Reversals", statement.reversalsMinor, isCredit = true)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))

                BreakdownRow("Actual Expenditure (Purchases, Bills)", statement.actualExpenditureMinor, isCredit = false)
                BreakdownRow("Cash Withdrawals (Physical Cash Asset)", statement.cashWithdrawalsMinor, isCredit = false, isSpecial = true)
                BreakdownRow("Loan Repayments (Principal Reduced)", statement.loanRepaymentsMinor, isCredit = false, isSpecial = true)
                BreakdownRow("Transaction Fees & Taxes", statement.feesMinor, isCredit = false)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))

                BreakdownRow("Internal Transfers (Airtel <-> MTN)", statement.internalTransfersMinor, isCredit = null)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accounting Integrity Explanatory Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E17)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricIndigo.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ElectricIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Double-Entry Accounting Principles",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Cash withdrawals are not counted as consumption — they represent transfers into your physical Cash in Hand asset.\n" +
                            "• Loan disbursements are credited as liabilities, never counted as earned income.\n" +
                            "• Internal transfers between Airtel Money and MTN MoMo are linked and do not inflate income or expense totals.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reconciliation & Audit status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (statement.discrepancyCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (statement.discrepancyCount == 0) Color(0xFF4CAF50) else Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (statement.discrepancyCount == 0) "Ledger In Balance (0 Discrepancies)" else "${statement.discrepancyCount} Discrepancy Found",
                    color = if (statement.discrepancyCount == 0) Color(0xFF4CAF50) else Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (statement.pendingReviewCount > 0) {
                Text(
                    text = "${statement.pendingReviewCount} items in Review",
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
}

@Composable
fun FlowMetricCard(
    title: String,
    amountMinor: Long,
    subtitle: String,
    isNegativeBad: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Money.format(amountMinor),
                color = when {
                    isNegativeBad -> Color.White
                    amountMinor > 0L -> Color(0xFF4CAF50)
                    amountMinor < 0L -> Color(0xFFFF5252)
                    else -> Color.White
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}

@Composable
fun BreakdownRow(
    label: String,
    amountMinor: Long,
    isCredit: Boolean?,
    isSpecial: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSpecial) ElectricIndigo else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
        Text(
            text = when (isCredit) {
                true -> "+ ${Money.format(amountMinor)}"
                false -> "- ${Money.format(amountMinor)}"
                null -> "⇌ ${Money.format(amountMinor)}"
            },
            color = when (isCredit) {
                true -> Color(0xFF4CAF50)
                false -> Color.White
                null -> ElectricIndigo
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
