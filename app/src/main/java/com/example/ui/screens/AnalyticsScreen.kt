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
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.FilterChipItem
import com.example.ui.components.GlassBox
import com.example.ui.components.MetricTile
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.CashTeal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OffWhite
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
    onPeriodSelect: (FilterPeriod) -> Unit
) {
    // 1. Data Processing
    val spendingTxns = state.filteredTransactions.filter {
        it.transactionType != "INCOME" && it.transactionType != "CASH_WITHDRAWAL" && !it.isInternalTransfer
    }
    val totalSpending = spendingTxns.sumOf { it.amount }

    val categoryTotals = spendingTxns.groupBy { it.categoryName }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val airtelSpending = spendingTxns.filter { it.provider == "AIRTEL" }.sumOf { it.amount }
    val mtnSpending = spendingTxns.filter { it.provider == "MTN" }.sumOf { it.amount }
    val cashSpending = spendingTxns.filter { it.provider == "CASH" }.sumOf { it.amount }

    val trendMap = when (state.selectedPeriod) {
        FilterPeriod.TODAY -> spendingTxns.groupBy { SimpleDateFormat("HH:00", Locale.ENGLISH).format(Date(it.transactionDate)) }
        FilterPeriod.THIS_WEEK, FilterPeriod.THIS_MONTH -> spendingTxns.groupBy { SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(it.transactionDate)) }
        else -> spendingTxns.groupBy { SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(Date(it.transactionDate)) }
    }.mapValues { it.value.sumOf { t -> t.amount } }.toSortedMap()

    val chartPoints = trendMap.entries.toList().takeLast(12)

    // 2. UI Layout
    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FINANCIAL", color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("DASHBOARD", color = ElectricIndigoLight, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("Monthly spending trends & carrier liquidity analytics", color = MutedGrey, fontSize = 12.sp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        FilterPeriod.TODAY to "Today",
                        FilterPeriod.THIS_WEEK to "This Week",
                        FilterPeriod.THIS_MONTH to "This Month",
                        FilterPeriod.LAST_MONTH to "Last Month",
                        FilterPeriod.ALL to "All Time"
                    ).forEach { (period, label) ->
                        FilterChipItem(label = label, isSelected = state.selectedPeriod == period, onClick = { onPeriodSelect(period) })
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(title = "Total Inflow", amount = state.metrics.periodIncome, isPositive = true, modifier = Modifier.weight(1f))
                    MetricTile(title = "Total Outflow", amount = state.metrics.periodExpenses, isPositive = false, modifier = Modifier.weight(1f))
                }
            }

            // Trend Chart Card
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.35f)))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Spending Velocity", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Trend across selected period", color = SubtleGrey, fontSize = 11.sp)
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x336366F1)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("ZMW ${"%,.2f".format(totalSpending)}", color = ElectricIndigoLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        if (chartPoints.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                Text("No financial data captured for this period", color = SubtleGrey, fontSize = 12.sp)
                            }
                        } else {
                            val maxAmt = (chartPoints.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(10.0)
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    for (i in 0..4) {
                                        val y = size.height - (i * size.height / 4)
                                        drawLine(color = Color.White.copy(alpha = 0.05f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                    }
                                }
                                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                                    val barWidth = 16.dp.toPx()
                                    val totalWidth = size.width
                                    val spacing = (totalWidth - (chartPoints.size * barWidth)) / (chartPoints.size + 1)
                                    chartPoints.forEachIndexed { index, entry ->
                                        val x = spacing + index * (barWidth + spacing)
                                        val ratio = (entry.value / maxAmt).toFloat()
                                        val barHeight = ratio * (size.height - 10.dp.toPx())
                                        val y = size.height - barHeight
                                        drawRoundRect(color = ElectricIndigo.copy(alpha = 0.15f * ratio), topLeft = Offset(x - 4.dp.toPx(), y - 4.dp.toPx()), size = Size(barWidth + 8.dp.toPx(), barHeight + 4.dp.toPx()), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
                                        drawRoundRect(brush = Brush.verticalGradient(listOf(ElectricIndigoLight, ElectricIndigo)), topLeft = Offset(x, y), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                chartPoints.forEach { entry ->
                                    Text(text = entry.key, color = SubtleGrey, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip)
                                }
                            }
                        }
                    }
                }
            }

            // Carrier Split Card
            item {
                GlassBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Carrier Outflow Distribution", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val sum = (airtelSpending + mtnSpending + cashSpending).coerceAtLeast(1.0)
                        Row(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0x331E293B))) {
                            if (airtelSpending > 0) Box(modifier = Modifier.weight((airtelSpending / sum).toFloat()).fillMaxSize().background(Brush.horizontalGradient(listOf(AirtelRed, Color(0xFFFF5388)))))
                            if (mtnSpending > 0) Box(modifier = Modifier.weight((mtnSpending / sum).toFloat()).fillMaxSize().background(Brush.horizontalGradient(listOf(MtnYellow, Color(0xFFFFE066)))))
                            if (cashSpending > 0) Box(modifier = Modifier.weight((cashSpending / sum).toFloat()).fillMaxSize().background(Brush.horizontalGradient(listOf(CashTeal, NeonCyan))))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            CarrierStatItem("Airtel", airtelSpending, (airtelSpending / sum * 100).toInt(), AirtelRed)
                            CarrierStatItem("MTN", mtnSpending, (mtnSpending / sum * 100).toInt(), MtnYellow)
                            CarrierStatItem("Cash", cashSpending, (cashSpending / sum * 100).toInt(), CashTeal)
                        }
                    }
                }
            }

            // Categories Card
            item {
                GlassBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Spending by Category", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))
                        if (categoryTotals.isEmpty()) {
                            Text("No categorized expenses in this period", color = SubtleGrey, fontSize = 12.sp)
                        } else {
                            categoryTotals.forEach { (catName, amount) ->
                                val pct = if (totalSpending > 0) (amount / totalSpending).toFloat() else 0f
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(catName, color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("ZMW ${"%,.2f".format(amount)} (${(pct * 100).toInt()}%)", color = SubtleGrey, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)), color = ElectricIndigo, trackColor = Color(0x331E293B))
                                }
                            }
                        }
                    }
                }
            }

            // Fees Card
            item {
                GlassBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, RestrainedRed.copy(alpha = 0.35f)))) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Carrier Transaction Charges", color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Total fees paid to Airtel & MTN: ZMW ${"%,.2f".format(state.metrics.periodFees)}", color = RestrainedRed, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Auto-extracted from merchant charges and cash-out tariff messages.", color = SubtleGrey, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarrierStatItem(label: String, amount: Double, pct: Int, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, color = SubtleGrey, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text("ZMW ${"%,.2f".format(amount)} ($pct%)", color = OffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
