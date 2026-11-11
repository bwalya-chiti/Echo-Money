package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassBox
import com.example.ui.components.ProviderBadge
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.CashTeal
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SubtleGrey
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun AccountsScreen(
    state: UiState,
    onOpenReconcile: (String) -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)

    AtmosphericGlassBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ACCOUNT", color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("BALANCES", color = ElectricIndigoLight, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("Reconcile SMS-reported balances with ledger calculations", color = MutedGrey, fontSize = 12.sp)
                }
            }

            // Airtel Money Glass Card
            item {
                val airtelAcc = state.accounts.find { it.provider == "AIRTEL" }
                AccountGlassReconciliationCard(
                    provider = "AIRTEL",
                    accountName = "Airtel Money Zambia",
                    brandColor = AirtelRed,
                    openingBalance = airtelAcc?.openingBalance ?: 0.0,
                    reportedBalance = state.metrics.airtelReportedBalance,
                    calculatedBalance = state.metrics.airtelCalculatedBalance,
                    lastUpdate = airtelAcc?.lastBalanceUpdate?.let { sdf.format(Date(it)) },
                    onReconcile = { onOpenReconcile("AIRTEL") }
                )
            }

            // MTN MoMo Glass Card
            item {
                val mtnAcc = state.accounts.find { it.provider == "MTN" }
                AccountGlassReconciliationCard(
                    provider = "MTN",
                    accountName = "MTN Mobile Money Zambia",
                    brandColor = MtnYellow,
                    openingBalance = mtnAcc?.openingBalance ?: 0.0,
                    reportedBalance = state.metrics.mtnReportedBalance,
                    calculatedBalance = state.metrics.mtnCalculatedBalance,
                    lastUpdate = mtnAcc?.lastBalanceUpdate?.let { sdf.format(Date(it)) },
                    onReconcile = { onOpenReconcile("MTN") }
                )
            }

            // Cash Account Glass Card
            item {
                val cashAcc = state.accounts.find { it.provider == "CASH" }
                AccountGlassReconciliationCard(
                    provider = "CASH",
                    accountName = "Cash Wallet",
                    brandColor = CashTeal,
                    openingBalance = cashAcc?.openingBalance ?: 0.0,
                    reportedBalance = state.metrics.cashCalculatedBalance,
                    calculatedBalance = state.metrics.cashCalculatedBalance,
                    lastUpdate = null,
                    isCash = true,
                    onReconcile = { onOpenReconcile("CASH") }
                )
            }

            // Educational Glass Guide Card
            item {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.3f)))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("How Balance Reconciliation Works", color = OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "• Reported Balance: Extracted directly from your latest provider confirmation SMS (e.g., 'Airtel Money bal is ZMW 18.22' or 'Your new balance: 30.76 ZMW').\n" +
                                "• Calculated Balance: Sum of your configured opening balance plus all detected income minus recorded expenses.\n" +
                                "• Difference: Represents untracked transactions before the app was installed or cash withdrawals. Tap Reconcile to adjust baseline.",
                                color = MutedGrey,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountGlassReconciliationCard(
    provider: String,
    accountName: String,
    brandColor: Color,
    openingBalance: Double,
    reportedBalance: Double,
    calculatedBalance: Double,
    lastUpdate: String?,
    isCash: Boolean = false,
    onReconcile: () -> Unit
) {
    val diff = reportedBalance - calculatedBalance
    val isBalanced = abs(diff) < 0.01

    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        borderBrush = Brush.verticalGradient(
            listOf(brandColor.copy(alpha = 0.7f), GlassBorderDefault)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBadge(provider = provider)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(accountName, color = OffWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                if (lastUpdate != null) {
                    Text("Updated $lastUpdate", color = SubtleGrey, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reported Balance
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCash) "Current Cash" else "SMS Reported Bal",
                        color = SubtleGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "ZMW ${"%,.2f".format(reportedBalance)}",
                        color = OffWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Calculated Balance
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ledger Calculated",
                        color = SubtleGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "ZMW ${"%,.2f".format(calculatedBalance)}",
                        color = OffWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details and Diff Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Opening Bal: ZMW ${"%.2f".format(openingBalance)}",
                    color = SubtleGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (!isCash) {
                    val diffColor = if (isBalanced) RestrainedGreen else RestrainedRed
                    Text(
                        text = if (isBalanced) "Reconciled ✓" else "Diff: ZMW ${"%,.2f".format(diff)}",
                        color = diffColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onReconcile,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDefault),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Configure & Reconcile $provider", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
