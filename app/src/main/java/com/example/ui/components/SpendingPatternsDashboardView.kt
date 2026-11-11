package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Transaction
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Standardized spending category model for D3/Recharts-inspired visualizations.
 */
data class CategorySpendingItem(
    val id: String,
    val name: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Float,
    val color: Color,
    val gradient: Brush,
    val icon: ImageVector,
    val airtelAmount: Double,
    val mtnAmount: Double,
    val cashAmount: Double
)

/**
 * Daily spending point for the 30-day timeline chart.
 */
data class DailySpendingPoint(
    val dayTimestamp: Long,
    val dayLabel: String,
    val fullDateLabel: String,
    val totalAmount: Double,
    val categoryBreakdown: Map<String, Double>
)

enum class ChartDisplayMode(val label: String, val icon: ImageVector) {
    DONUT("Share", Icons.Default.PieChart),
    TREND_AREA("30D Trend", Icons.AutoMirrored.Filled.ShowChart),
    BAR_RANK("Compare", Icons.Default.BarChart)
}

/**
 * Active, attractive, and interactive D3 / Recharts-inspired spending patterns dashboard view.
 * Visualizes 30-day consumption patterns by category (Airtime, Bills, Transfers, Merchant, etc.).
 */
@Composable
fun SpendingPatternsDashboardView(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
    onSelectTransaction: (Transaction) -> Unit = {}
) {
    // 1. Calculate the 30-day window
    val now = remember { System.currentTimeMillis() }
    val thirtyDaysAgo = remember(now) { now - (30L * 24 * 60 * 60 * 1000L) }

    // 2. Filter 30-day outflow/spending transactions
    // Excludes incoming money, failed items, and internal transfers between user's own accounts
    val thirtyDayOutflows = remember(transactions) {
        transactions.filter {
            it.transactionDate in thirtyDaysAgo..now &&
                    it.direction == "DEBIT" &&
                    it.status != "FAILED" &&
                    !it.isInternalTransfer
        }
    }

    val total30DaySpending = remember(thirtyDayOutflows) {
        thirtyDayOutflows.sumOf { it.amount }
    }

    // 3. Normalize categories into standard mobile money spending buckets
    val categories = remember(thirtyDayOutflows, total30DaySpending) {
        val grouped = thirtyDayOutflows.groupBy { txn ->
            val c = txn.categoryName.lowercase(Locale.ENGLISH)
            when {
                c.contains("bundle") || c.contains("internet") || c.contains("data") || c.contains("airtime") -> "Airtime & Data"
                c.contains("zesco") || c.contains("utility") || c.contains("utilities") || c.contains("electricity") || c.contains("bill") || c.contains("prepaid") -> "Bills & Utilities"
                c.contains("transfer") || c.contains("p2p") || c.contains("sent") || c.contains("outgoing") -> "Transfers & P2P"
                c.contains("merchant") || c.contains("paid") || c.contains("shopping") || c.contains("store") -> "Merchant & Retail"
                c.contains("loan") || c.contains("repayment") -> "Loan Repayments"
                c.contains("withdraw") || c.contains("cash out") -> "Cash Withdrawals"
                else -> "General & Other"
            }
        }

        val meta = mapOf(
            "Airtime & Data" to Triple(Color(0xFF00E5FF), Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0091EA))), Icons.Default.PhoneAndroid),
            "Bills & Utilities" to Triple(Color(0xFFFFB300), Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6D00))), Icons.Default.Bolt),
            "Transfers & P2P" to Triple(Color(0xFF9E86FF), Brush.linearGradient(listOf(Color(0xFF9E86FF), Color(0xFF651FFF))), Icons.AutoMirrored.Filled.Send),
            "Merchant & Retail" to Triple(Color(0xFFFF4081), Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63))), Icons.Default.ShoppingBag),
            "Loan Repayments" to Triple(Color(0xFF26C6DA), Brush.linearGradient(listOf(Color(0xFF26C6DA), Color(0xFF00838F))), Icons.Default.AccountBalance),
            "Cash Withdrawals" to Triple(Color(0xFF10B981), Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))), Icons.Default.LocalAtm),
            "General & Other" to Triple(Color(0xFFBA68C8), Brush.linearGradient(listOf(Color(0xFFBA68C8), Color(0xFF7B1FA2))), Icons.Default.Category)
        )

        grouped.map { (catName, txns) ->
            val amount = txns.sumOf { it.amount }
            val (color, grad, icon) = meta[catName] ?: Triple(Color(0xFF9E9DFF), Brush.linearGradient(listOf(Color(0xFF9E9DFF), ElectricIndigo)), Icons.Default.Category)
            val pct = if (total30DaySpending > 0) (amount / total30DaySpending).toFloat() else 0f
            CategorySpendingItem(
                id = catName,
                name = catName,
                totalAmount = amount,
                transactionCount = txns.size,
                percentage = pct,
                color = color,
                gradient = grad,
                icon = icon,
                airtelAmount = txns.filter { it.provider == "AIRTEL" }.sumOf { it.amount },
                mtnAmount = txns.filter { it.provider == "MTN" }.sumOf { it.amount },
                cashAmount = txns.filter { it.provider == "CASH" }.sumOf { it.amount }
            )
        }.sortedByDescending { it.totalAmount }
    }

    // 4. Generate 30 daily continuous time slots for trend area/curve
    val dailyPoints = remember(thirtyDayOutflows, now) {
        val sdfShort = SimpleDateFormat("dd MMM", Locale.ENGLISH)
        val sdfFull = SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.timeInMillis = now

        val points = mutableListOf<DailySpendingPoint>()
        for (i in 29 downTo 0) {
            val dayCal = Calendar.getInstance()
            dayCal.timeInMillis = now - (i.toLong() * 24 * 60 * 60 * 1000L)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            dayCal.set(Calendar.MINUTE, 0)
            dayCal.set(Calendar.SECOND, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            val startOfDay = dayCal.timeInMillis
            val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1

            val dayTxns = thirtyDayOutflows.filter { it.transactionDate in startOfDay..endOfDay }
            val dayTotal = dayTxns.sumOf { it.amount }

            val breakdown = dayTxns.groupBy { txn ->
                val c = txn.categoryName.lowercase(Locale.ENGLISH)
                when {
                    c.contains("bundle") || c.contains("internet") || c.contains("data") || c.contains("airtime") -> "Airtime & Data"
                    c.contains("zesco") || c.contains("utility") || c.contains("utilities") || c.contains("electricity") || c.contains("bill") || c.contains("prepaid") -> "Bills & Utilities"
                    c.contains("transfer") || c.contains("p2p") || c.contains("sent") || c.contains("outgoing") -> "Transfers & P2P"
                    c.contains("merchant") || c.contains("paid") || c.contains("shopping") || c.contains("store") -> "Merchant & Retail"
                    c.contains("loan") || c.contains("repayment") -> "Loan Repayments"
                    c.contains("withdraw") || c.contains("cash out") -> "Cash Withdrawals"
                    else -> "General & Other"
                }
            }.mapValues { it.value.sumOf { t -> t.amount } }

            points.add(
                DailySpendingPoint(
                    dayTimestamp = startOfDay,
                    dayLabel = sdfShort.format(Date(startOfDay)),
                    fullDateLabel = sdfFull.format(Date(startOfDay)),
                    totalAmount = dayTotal,
                    categoryBreakdown = breakdown
                )
            )
        }
        points
    }

    // Interactive UI States
    var chartMode by remember { mutableStateOf(ChartDisplayMode.DONUT) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var hoveredDailyPoint by remember { mutableStateOf<DailySpendingPoint?>(null) }

    // Chart entry animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(chartMode, selectedCategoryFilter) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    // KPIs for 30-day window
    val activeDaysCount = remember(dailyPoints) { dailyPoints.count { it.totalAmount > 0 } }
    val dailyAverage = remember(total30DaySpending) { total30DaySpending / 30.0 }
    val topCategory = remember(categories) { categories.firstOrNull() }

    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        borderBrush = Brush.verticalGradient(
            listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.40f), GlassBorderDefault)
        ),
        backgroundBrush = Brush.verticalGradient(
            listOf(Color(0xE612121A), Color(0xCC09090E))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // Header Row: Title & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(ElectricIndigoLight, Color(0xFF6366F1)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "30-DAY SPENDING",
                                color = SoftWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PATTERNS",
                                color = ElectricIndigoLight,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Interactive breakdown of category outflows",
                            color = MutedSilver,
                            fontSize = 11.sp
                        )
                    }
                }

                // D3 / Recharts Mode Indicator Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x2E6366F1))
                        .border(1.dp, ElectricIndigo.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Active Visualizer",
                            color = ElectricIndigoLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 30-Day Metric Summary Ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x40161622))
                    .border(1.dp, GlassBorderDefault, RoundedCornerShape(14.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Outflow", color = SubtleGrey, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text("ZMW ${"%,.2f".format(total30DaySpending)}", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                Column {
                    Text("Daily Average", color = SubtleGrey, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text("ZMW ${"%,.2f".format(dailyAverage)}", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                Column {
                    Text("Top Category", color = SubtleGrey, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(topCategory?.name ?: "None", color = ElectricIndigoLight, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Chart Mode Selector (Donut / 30D Trend / Comparison)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x4D12121C))
                    .border(1.dp, GlassBorderDefault, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartDisplayMode.values().forEach { mode ->
                    val isSelected = chartMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) ElectricIndigo else Color.Transparent)
                            .clickable { chartMode = mode }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = mode.label,
                                tint = if (isSelected) Color.White else MutedSilver,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = mode.label,
                                color = if (isSelected) Color.White else MutedSilver,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Filter Pills (Allows filtering by Category like Recharts legend toggles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isAllSelected = selectedCategoryFilter == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isAllSelected) ElectricIndigo.copy(alpha = 0.25f) else Color(0x331E1E2C))
                        .border(
                            1.dp,
                            if (isAllSelected) ElectricIndigo else GlassBorderDefault,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedCategoryFilter = null }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "All Categories (${categories.size})",
                        color = if (isAllSelected) SoftWhite else MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) cat.color.copy(alpha = 0.22f) else Color(0x331E1E2C))
                            .border(
                                1.dp,
                                if (isSelected) cat.color else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedCategoryFilter = if (isSelected) null else cat.id
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(cat.color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = cat.name,
                                color = if (isSelected) SoftWhite else MutedSilver,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PRIMARY CHART CANVAS AREA
            if (thirtyDayOutflows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22181824)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = SubtleGrey, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No spending records in the last 30 days", color = SubtleGrey, fontSize = 12.sp)
                    }
                }
            } else {
                when (chartMode) {
                    ChartDisplayMode.DONUT -> {
                        ActiveDonutChartView(
                            categories = categories,
                            totalSpending = total30DaySpending,
                            selectedCategoryName = selectedCategoryFilter,
                            animationProgress = animProgress.value,
                            onSelectCategory = { catName ->
                                selectedCategoryFilter = if (selectedCategoryFilter == catName) null else catName
                            }
                        )
                    }
                    ChartDisplayMode.TREND_AREA -> {
                        Active30DayTrendChartView(
                            dailyPoints = dailyPoints,
                            selectedCategory = selectedCategoryFilter,
                            animationProgress = animProgress.value,
                            hoveredPoint = hoveredDailyPoint,
                            onHoverPoint = { point -> hoveredDailyPoint = point }
                        )
                    }
                    ChartDisplayMode.BAR_RANK -> {
                        ActiveRankedBarChartView(
                            categories = categories,
                            totalSpending = total30DaySpending,
                            selectedCategory = selectedCategoryFilter,
                            animationProgress = animProgress.value,
                            onSelectCategory = { catName ->
                                selectedCategoryFilter = if (selectedCategoryFilter == catName) null else catName
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Category Details List with Drilldown
            Text(
                text = if (selectedCategoryFilter != null) "CATEGORY DETAIL: ${selectedCategoryFilter!!.uppercase()}" else "CATEGORY BREAKDOWN (LAST 30 DAYS)",
                color = MutedSilver,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val displayCategories = if (selectedCategoryFilter != null) {
                categories.filter { it.id == selectedCategoryFilter }
            } else {
                categories
            }

            displayCategories.forEach { cat ->
                CategoryBreakdownCard(
                    item = cat,
                    isSelected = selectedCategoryFilter == cat.id,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == cat.id) null else cat.id
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Drilldown: Recent Transactions in selected category
            if (selectedCategoryFilter != null) {
                val categoryTxns = thirtyDayOutflows.filter { txn ->
                    val c = txn.categoryName.lowercase(Locale.ENGLISH)
                    val target = selectedCategoryFilter!!.lowercase(Locale.ENGLISH)
                    when {
                        target.contains("airtime") -> c.contains("bundle") || c.contains("internet") || c.contains("data") || c.contains("airtime")
                        target.contains("bill") -> c.contains("zesco") || c.contains("utility") || c.contains("utilities") || c.contains("electricity") || c.contains("bill") || c.contains("prepaid")
                        target.contains("transfer") -> c.contains("transfer") || c.contains("p2p") || c.contains("sent") || c.contains("outgoing")
                        target.contains("merchant") -> c.contains("merchant") || c.contains("paid") || c.contains("shopping") || c.contains("store")
                        target.contains("loan") -> c.contains("loan") || c.contains("repayment")
                        target.contains("withdraw") -> c.contains("withdraw") || c.contains("cash out")
                        else -> true
                    }
                }.take(5)

                if (categoryTxns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RECENT TRANSACTIONS IN ${selectedCategoryFilter!!.uppercase()}",
                        color = MutedSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    categoryTxns.forEach { txn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3312121A))
                                .clickable { onSelectTransaction(txn) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (txn.provider == "AIRTEL") AirtelRed else MtnYellow)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = txn.recipient ?: txn.merchantOrAgent ?: txn.description ?: "Transaction",
                                        color = SoftWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH).format(Date(txn.transactionDate))
                                    Text(text = "$dateStr • ${txn.provider}", color = SubtleGrey, fontSize = 10.sp)
                                }
                            }
                            Text(
                                text = "ZMW ${"%,.2f".format(txn.amount)}",
                                color = SoftWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Active interactive Donut Chart with central callout and slice pop animation.
 */
@Composable
private fun ActiveDonutChartView(
    categories: List<CategorySpendingItem>,
    totalSpending: Double,
    selectedCategoryName: String?,
    animationProgress: Float,
    onSelectCategory: (String) -> Unit
) {
    val activeItem = categories.firstOrNull { it.id == selectedCategoryName }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(categories, totalSpending) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                        val innerR = (size.width / 2f) * 0.52f
                        val outerR = (size.width / 2f) * 0.95f

                        if (distance in innerR..outerR) {
                            var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f

                            var currentAngle = 0f
                            for (cat in categories) {
                                val sweep = cat.percentage * 360f
                                if (angle in currentAngle..(currentAngle + sweep)) {
                                    onSelectCategory(cat.id)
                                    break
                                }
                                currentAngle += sweep
                            }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.width / 2f) * 0.72f
            val baseStrokeWidth = 24.dp.toPx()

            var startAngle = -90f

            categories.forEach { cat ->
                val isSelected = cat.id == selectedCategoryName
                val sweepAngle = cat.percentage * 360f * animationProgress

                val strokeWidth = if (isSelected) baseStrokeWidth * 1.35f else baseStrokeWidth
                val radiusOffset = if (isSelected) 6.dp.toPx() else 0f

                // Active glow for selected slice
                if (isSelected) {
                    drawArc(
                        color = cat.color.copy(alpha = 0.35f),
                        startAngle = startAngle + 1f,
                        sweepAngle = (sweepAngle - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius - radiusOffset - 6.dp.toPx(), center.y - baseRadius - radiusOffset - 6.dp.toPx()),
                        size = Size((baseRadius + radiusOffset + 6.dp.toPx()) * 2, (baseRadius + radiusOffset + 6.dp.toPx()) * 2),
                        style = Stroke(width = strokeWidth + 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Sliced Arc
                drawArc(
                    brush = cat.gradient,
                    startAngle = startAngle + 1.5f,
                    sweepAngle = (sweepAngle - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius - radiusOffset, center.y - baseRadius - radiusOffset),
                    size = Size((baseRadius + radiusOffset) * 2, (baseRadius + radiusOffset) * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                startAngle += cat.percentage * 360f
            }

            // Inner dark glass core circle
            drawCircle(
                color = Color(0xCC0E0E14),
                radius = baseRadius - (baseStrokeWidth / 2f) - 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = GlassBorderTop,
                radius = baseRadius - (baseStrokeWidth / 2f) - 6.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Center Callout (Displays active category metrics or total 30-day sum)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (activeItem != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(activeItem.color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(activeItem.icon, contentDescription = null, tint = activeItem.color, modifier = Modifier.size(13.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeItem.name,
                    color = SoftWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ZMW ${"%,.2f".format(activeItem.totalAmount)}",
                    color = activeItem.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${(activeItem.percentage * 100).toInt()}% of 30D spend",
                    color = SubtleGrey,
                    fontSize = 9.sp
                )
            } else {
                Text(
                    text = "30-DAY OUTFLOW",
                    color = SubtleGrey,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "ZMW ${"%,.0f".format(totalSpending)}",
                    color = SoftWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Tap slice to inspect",
                        color = ElectricIndigoLight,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Active 30-Day Trend Area & Curve chart with interactive touch scrubber and tooltip.
 */
@Composable
private fun Active30DayTrendChartView(
    dailyPoints: List<DailySpendingPoint>,
    selectedCategory: String?,
    animationProgress: Float,
    hoveredPoint: DailySpendingPoint?,
    onHoverPoint: (DailySpendingPoint?) -> Unit
) {
    val displayedValues = remember(dailyPoints, selectedCategory) {
        dailyPoints.map { point ->
            if (selectedCategory != null) {
                point.categoryBreakdown[selectedCategory] ?: 0.0
            } else {
                point.totalAmount
            }
        }
    }

    val maxVal = remember(displayedValues) {
        (displayedValues.maxOrNull() ?: 1.0).coerceAtLeast(20.0)
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Active Hover Tooltip Box
        if (hoveredPoint != null) {
            val hVal = if (selectedCategory != null) {
                hoveredPoint.categoryBreakdown[selectedCategory] ?: 0.0
            } else {
                hoveredPoint.totalAmount
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE61E1E2C))
                    .border(1.dp, ElectricIndigoLight.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = hoveredPoint.fullDateLabel,
                            color = SoftWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Spent: ZMW ${"%,.2f".format(hVal)}",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Canvas Area / Curve
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dailyPoints, displayedValues) {
                        detectDragGestures(
                            onDragEnd = { onHoverPoint(null) },
                            onDragCancel = { onHoverPoint(null) },
                            onDrag = { change, _ ->
                                val x = change.position.x
                                val index = ((x / size.width) * (dailyPoints.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, dailyPoints.size - 1)
                                onHoverPoint(dailyPoints[index])
                            }
                        )
                    }
                    .pointerInput(dailyPoints, displayedValues) {
                        detectTapGestures(
                            onPress = { offset ->
                                val x = offset.x
                                val index = ((x / size.width) * (dailyPoints.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, dailyPoints.size - 1)
                                onHoverPoint(dailyPoints[index])
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height - 24.dp.toPx()
                val bottomY = size.height - 12.dp.toPx()
                val n = displayedValues.size

                if (n < 2) return@Canvas

                val stepX = w / (n - 1)

                // 1. Draw subtle horizontal grid lines (D3 / Recharts style)
                val gridLevels = listOf(0.25f, 0.50f, 0.75f, 1.0f)
                gridLevels.forEach { level ->
                    val y = bottomY - (h * level)
                    drawLine(
                        color = Color(0x1AFFFFFF),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // 2. Build smooth cubic bezier curve
                val points = displayedValues.mapIndexed { i, v ->
                    val x = i * stepX
                    val y = bottomY - ((v / maxVal).toFloat() * h * animationProgress).coerceAtLeast(0f)
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                        val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                        cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, bottomY)
                    lineTo(points.first().x, bottomY)
                    close()
                }

                // Draw Gradient Area (Recharts Area Fill)
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x5500E5FF),
                            Color(0x337775FF),
                            Color(0x0012121A)
                        ),
                        startY = 0f,
                        endY = bottomY
                    )
                )

                // Draw Luminous Stroke Line
                drawPath(
                    path = strokePath,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF00E5FF), ElectricIndigoLight, Color(0xFFFF4081))
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // 3. Draw Active Touch Scrubber Line if user is interacting
                if (hoveredPoint != null) {
                    val hIdx = dailyPoints.indexOf(hoveredPoint).coerceAtLeast(0)
                    val activeOffset = points[hIdx]

                    // Vertical guide line
                    drawLine(
                        color = Color(0x8000E5FF),
                        start = Offset(activeOffset.x, 0f),
                        end = Offset(activeOffset.x, bottomY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Outer halo
                    drawCircle(
                        color = Color(0x4000E5FF),
                        radius = 11.dp.toPx(),
                        center = activeOffset
                    )

                    // Inner bright pulsing dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.5.dp.toPx(),
                        center = activeOffset
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 3.dp.toPx(),
                        center = activeOffset
                    )
                }
            }
        }

        // Timeline Date Labels (X-Axis)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelIndices = listOf(0, 7, 14, 21, 29).filter { it < dailyPoints.size }
            labelIndices.forEach { idx ->
                Text(
                    text = dailyPoints[idx].dayLabel,
                    color = SubtleGrey,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Ranked category comparison bar chart (D3 style horizontal comparison).
 */
@Composable
private fun ActiveRankedBarChartView(
    categories: List<CategorySpendingItem>,
    totalSpending: Double,
    selectedCategory: String?,
    animationProgress: Float,
    onSelectCategory: (String) -> Unit
) {
    val maxAmt = categories.maxOfOrNull { it.totalAmount }?.coerceAtLeast(10.0) ?: 10.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = selectedCategory == cat.id
            val ratio = ((cat.totalAmount / maxAmt).toFloat() * animationProgress).coerceIn(0.02f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0x336366F1) else Color.Transparent)
                    .clickable { onSelectCategory(cat.id) }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(cat.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cat.icon, contentDescription = null, tint = cat.color, modifier = Modifier.size(11.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(cat.name, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = "ZMW ${"%,.2f".format(cat.totalAmount)} (${(cat.percentage * 100).toInt()}%)",
                        color = if (isSelected) cat.color else SoftWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Luminous progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x331E1E2C))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cat.gradient)
                    )
                }
            }
        }
    }
}

/**
 * Rich category item card with carrier breakdown, count and averages.
 */
@Composable
private fun CategoryBreakdownCard(
    item: CategorySpendingItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderCol = if (isSelected) item.color else GlassBorderDefault
    val bgCol = if (isSelected) item.color.copy(alpha = 0.12f) else Color(0x3314141E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(item.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.name,
                            color = SoftWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val avg = if (item.transactionCount > 0) item.totalAmount / item.transactionCount else 0.0
                        Text(
                            text = "${item.transactionCount} transactions • Avg ZMW ${"%,.2f".format(avg)}",
                            color = SubtleGrey,
                            fontSize = 10.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ZMW ${"%,.2f".format(item.totalAmount)}",
                        color = SoftWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${(item.percentage * 100).toInt()}% of total",
                        color = item.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Carrier Split Indicator (Airtel vs MTN within this category)
            val sum = (item.airtelAmount + item.mtnAmount + item.cashAmount).coerceAtLeast(1.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                if (item.airtelAmount > 0) {
                    Box(
                        modifier = Modifier
                            .weight((item.airtelAmount / sum).toFloat())
                            .height(4.dp)
                            .background(AirtelRed)
                    )
                }
                if (item.mtnAmount > 0) {
                    Box(
                        modifier = Modifier
                            .weight((item.mtnAmount / sum).toFloat())
                            .height(4.dp)
                            .background(MtnYellow)
                    )
                }
                if (item.cashAmount > 0) {
                    Box(
                        modifier = Modifier
                            .weight((item.cashAmount / sum).toFloat())
                            .height(4.dp)
                            .background(Color(0xFF10B981))
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Airtel: ZMW ${"%,.0f".format(item.airtelAmount)}",
                    color = AirtelRed.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "MTN: ZMW ${"%,.0f".format(item.mtnAmount)}",
                    color = MtnYellow.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
