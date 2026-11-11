package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Money
import com.example.data.model.ReconciliationDiscrepancy
import com.example.data.model.Transaction
import com.example.ui.theme.ElectricIndigo
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReviewCentreScreen(
    uiState: UiState,
    onResolveReview: (Long, Boolean, String?) -> Unit,
    onResolveDiscrepancy: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pending Reviews (${uiState.reviewItems.size})", "Discrepancies (${uiState.discrepancies.size})")

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
                .testTag("review_centre_screen")
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
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Review Centre",
                        tint = ElectricIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Review Centre",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Audit unclassified activity, discrepancies & ambiguous transfers",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF0B0B0E),
            contentColor = ElectricIndigo,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricIndigo
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) ElectricIndigo else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.testTag("review_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // Pending Reviews Tab
            if (uiState.reviewItems.isEmpty()) {
                EmptyStateCard(
                    title = "All Clear",
                    description = "No transactions currently require manual review. All messages were parsed and matched with high confidence."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("pending_reviews_list")
                ) {
                    items(uiState.reviewItems, key = { it.id }) { item ->
                        ReviewItemCard(
                            transaction = item,
                            onApprove = { onResolveReview(item.id, true, null) },
                            onReject = { onResolveReview(item.id, false, null) }
                        )
                    }
                }
            }
        } else {
            // Discrepancies Tab
            if (uiState.discrepancies.isEmpty()) {
                EmptyStateCard(
                    title = "Perfect Reconciliation",
                    description = "No balance discrepancies detected. Ledger running balances agree 100% with provider-reported SMS statements."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("discrepancies_list")
                ) {
                    items(uiState.discrepancies, key = { it.id }) { discrepancy ->
                        DiscrepancyCard(
                            discrepancy = discrepancy,
                            onResolve = { notes -> onResolveDiscrepancy(discrepancy.id, notes) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun ReviewItemCard(
    transaction: Transaction,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    val dateStr = sdf.format(Date(transaction.transactionDate))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFFFA726).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .testTag("review_item_${transaction.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (transaction.provider == "AIRTEL") Color(0xFFE50914).copy(alpha = 0.2f) else Color(0xFFFFCC00).copy(alpha = 0.2f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transaction.provider,
                        color = if (transaction.provider == "AIRTEL") Color(0xFFFF5252) else Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = transaction.categoryName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = Money.formatSigned(transaction.amountMinor, transaction.direction),
                    color = if (transaction.direction == "CREDIT") Color(0xFF4CAF50) else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason for review
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFA726).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transaction.reviewReason ?: "Requires user verification",
                        color = Color(0xFFFFA726),
                        fontSize = 12.sp
                    )
                }
            }

            if (!transaction.rawSms.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Raw Evidence:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    text = transaction.rawSms,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$dateStr • Ref: ${transaction.transactionReference ?: "None"}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF5252).copy(alpha = 0.5f))),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reject_button_${transaction.id}")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("approve_button_${transaction.id}")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve & Post", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DiscrepancyCard(
    discrepancy: ReconciliationDiscrepancy,
    onResolve: (String) -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    val dateStr = sdf.format(Date(discrepancy.timestamp))
    var showResolveInput by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("Reconciled by user") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag("discrepancy_item_${discrepancy.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${discrepancy.provider} Balance Discrepancy",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = Money.format(discrepancy.discrepancyMinor),
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Provider Reported", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(Money.format(discrepancy.reportedBalanceMinor), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Calculated from Ledger", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(Money.format(discrepancy.calculatedBalanceMinor), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$dateStr ${if (discrepancy.transactionReference != null) "• TID: ${discrepancy.transactionReference}" else ""}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )

            AnimatedVisibility(visible = showResolveInput) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Resolution Reason", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricIndigo,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onResolve(notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End).testTag("confirm_resolve_${discrepancy.id}")
                    ) {
                        Text("Confirm Resolution", fontSize = 12.sp)
                    }
                }
            }

            if (!showResolveInput) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showResolveInput = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigo),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricIndigo.copy(alpha = 0.5f))),
                    modifier = Modifier.align(Alignment.End).testTag("resolve_button_${discrepancy.id}")
                ) {
                    Text("Resolve Discrepancy", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E12)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(ElectricIndigo.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ElectricIndigo,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
