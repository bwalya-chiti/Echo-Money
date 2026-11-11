package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.ui.components.MetricTile
import com.example.ui.components.SpendingPatternsDashboardView
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.CashTeal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.FilterPeriod
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    state: UiState,
    onPeriodSelect: (FilterPeriod) -> Unit,
    onSelectTransaction: (Transaction) -> Unit = {}
) {
    // Exclude internal transfers and withdrawals from consumption spending
    val spendingTxns = state.filteredTransactions.filter {
        it.transactionType != "INCOME" && it.transactionType != "CASH_WITHDRAWAL" && !it.isInternalTransfer
    }
    val totalSpending = spendingTxns.sumOf { it.amount }

    // Category breakdown
    val categoryTotals = spendingTxns.groupBy { it.categoryName }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    // Provider split
    val airtelSpending = spendingTxns.filter { it.provider == "AIRTEL" }.sumOf { it.amount }
    val mtnSpending = spendingTxns.filter { it.provider == "MTN" }.sumOf { it.amount }
    val cashSpending = spendingTxns.filter { it.provider == "CASH" }.sumOf { it.amount }

    // Daily breakdown for chart (last 7 data points)
    val daySdf = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    val dailyMap = spendingTxns.groupBy { daySdf.format(Date(it.transactionDate)) }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
    val chartPoints: List<Map.Entry<String, Double>> = dailyMap.entries.toList().takeLast(7)

    AtmosphericGlassBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Header
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FINANCIAL", color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("ANALYTICS", color = ElectricIndigoLight, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("Cash flow trends, carrier split & expense categories", color = MutedGrey, fontSize = 12.sp)
                }
            }

            // Period Selector
            item {
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

            // KPI Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        title = "Period Inflow",
                        amount = state.metrics.periodIncome,
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Period Outflow",
                        amount = state.metrics.periodExpenses,
                        isPositive = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Interactive 30-Day Category Spending Patterns (D3 / Recharts-inspired Visualizer)
            item {
                SpendingPatternsDashboardView(
                    transactions = state.transactions,
                    onSelectTransaction = onSelectTransaction
                )
            }

            // Spending Trend Glass Chart
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.35f)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Spending Trend", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x336366F1))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("ZMW ${"%,.2f".format(totalSpending)}", color = ElectricIndigoLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (chartPoints.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No spending data in selected period", color = SubtleGrey, fontSize = 12.sp)
                            }
                        } else {
                            val maxAmt = (chartPoints.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(10.0)

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val barWidth = ((size.width * 0.70f) / chartPoints.size.coerceAtLeast(1)).coerceIn(12.dp.toPx(), 26.dp.toPx())
                                val spacing = ((size.width - (chartPoints.size * barWidth)) / (chartPoints.size + 1)).coerceAtLeast(4.dp.toPx())

                                chartPoints.forEachIndexed { index, entry ->
                                    val x = spacing + index * (barWidth + spacing)
                                    val barHeight = (entry.value / maxAmt).toFloat() * (size.height - 24.dp.toPx())
                                    val y = size.height - barHeight

                                    // Luminous Gradient Bar
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            listOf(ElectricIndigoLight, ElectricViolet)
                                        ),
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Labels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                chartPoints.forEach { entry ->
                                    Text(entry.key, color = SubtleGrey, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Airtel vs MTN Carrier Split
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Carrier Outflow Distribution", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        val sum = (airtelSpending + mtnSpending + cashSpending).coerceAtLeast(1.0)
                        val airtelPct = (airtelSpending / sum * 100).toInt()
                        val mtnPct = (mtnSpending / sum * 100).toInt()
                        val cashPct = (cashSpending / sum * 100).toInt()

                        // Multi-segmented Luminous Glass Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0x331E293B))
                        ) {
                            if (airtelSpending > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight((airtelSpending / sum).toFloat())
                                        .fillMaxSize()
                                        .background(Brush.horizontalGradient(listOf(AirtelRed, Color(0xFFFF5388))))
                                )
                            }
                            if (mtnSpending > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight((mtnSpending / sum).toFloat())
                                        .fillMaxSize()
                                        .background(Brush.horizontalGradient(listOf(MtnYellow, Color(0xFFFFE066))))
                                )
                            }
                            if (cashSpending > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight((cashSpending / sum).toFloat())
                                        .fillMaxSize()
                                        .background(Brush.horizontalGradient(listOf(CashTeal, NeonCyan)))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CarrierStatItem("Airtel", airtelSpending, airtelPct, AirtelRed, Modifier.weight(1f))
                            CarrierStatItem("MTN", mtnSpending, mtnPct, MtnYellow, Modifier.weight(1f))
                            CarrierStatItem("Cash", cashSpending, cashPct, CashTeal, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Category Breakdown with Glass Bars
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Spending by Category", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        if (categoryTotals.isEmpty()) {
                            Text("No categorized expenses in this period", color = SubtleGrey, fontSize = 12.sp)
                        } else {
                            categoryTotals.forEach { (catName, amount) ->
                                val pct = if (totalSpending > 0) (amount / totalSpending).toFloat() else 0f
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(catName, color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("ZMW ${"%,.2f".format(amount)} (${(pct * 100).toInt()}%)", color = SubtleGrey, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = ElectricIndigo,
                                        trackColor = Color(0x331E293B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Carrier Charges Glass Card
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, RestrainedRed.copy(alpha = 0.35f)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Carrier Transaction Charges", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Total fees paid to Airtel & MTN: ZMW ${"%,.2f".format(state.metrics.periodFees)}",
                            color = RestrainedRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Auto-extracted from merchant charges and cash-out tariff messages.",
                            color = SubtleGrey,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CarrierStatItem(
    label: String,
    amount: Double,
    pct: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = SubtleGrey, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text("ZMW ${"%,.0f".format(amount)} ($pct%)", color = OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
