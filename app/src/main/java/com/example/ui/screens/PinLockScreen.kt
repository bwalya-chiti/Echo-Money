package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassBox
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MutedGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SubtleGrey

@Composable
fun PinLockScreen(
    isBiometricEnabled: Boolean = false,
    onUnlockAttempt: (String) -> Boolean,
    onBiometricUnlockRequest: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && onBiometricUnlockRequest != null) {
            onBiometricUnlockRequest()
        }
    }

    fun addDigit(d: String) {
        if (pin.length < 4) {
            val newPin = pin + d
            pin = newPin
            error = false
            if (newPin.length == 4) {
                val success = onUnlockAttempt(newPin)
                if (!success) {
                    error = true
                    pin = ""
                }
            }
        }
    }

    fun removeDigit() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            error = false
        }
    }

    AtmosphericGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(28.dp),
                borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, ElectricIndigo.copy(alpha = 0.4f)))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0x337775FF))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, Brush.linearGradient(listOf(com.example.ui.theme.ElectricIndigo, com.example.ui.theme.ObsidianHighlight))), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.echo_money_icon_1793380992762),
                            contentDescription = "Echo Money Emblem",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .alpha(0.90f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ECHO", color = com.example.ui.theme.SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("MONEY", color = com.example.ui.theme.ElectricIndigo, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enter your 4-digit PIN to unlock", color = com.example.ui.theme.MutedSilver, fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(28.dp))

                    // Luminous PIN Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in 0 until 4) {
                            val filled = i < pin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) Brush.linearGradient(listOf(com.example.ui.theme.ElectricIndigo, com.example.ui.theme.ObsidianHighlight))
                                        else Brush.linearGradient(listOf(Color(0x33121218), Color(0x220B0B0E)))
                                    )
                                    .border(1.dp, if (error) RestrainedRed else GlassBorderDefault, CircleShape)
                            )
                        }
                    }

                    if (error) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Incorrect PIN. Please try again.", color = RestrainedRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keypad Grid
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(if (isBiometricEnabled && onBiometricUnlockRequest != null) "BIO" else "", "0", "DEL")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { key ->
                                    if (key == "BIO") {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x337775FF))
                                                .border(1.dp, Brush.linearGradient(listOf(ElectricIndigo, GlassBorderDefault)), CircleShape)
                                                .clickable { onBiometricUnlockRequest?.invoke() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Fingerprint,
                                                contentDescription = "Biometric Unlock",
                                                tint = ElectricIndigoLight,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else if (key.isEmpty()) {
                                        Box(modifier = Modifier.size(64.dp))
                                    } else if (key == "DEL") {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x331E293B))
                                                .border(1.dp, GlassBorderDefault, CircleShape)
                                                .clickable { removeDigit() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete",
                                                tint = MutedGrey,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x4D1E2942))
                                                .border(1.dp, GlassBorderDefault, CircleShape)
                                                .clickable { addDigit(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(key, color = OffWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
