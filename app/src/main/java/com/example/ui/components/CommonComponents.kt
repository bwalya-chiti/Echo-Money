package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AbsoluteBlack
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.AirtelRedGlass
import com.example.ui.theme.CashTeal
import com.example.ui.theme.CashTealGlass
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoLight
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassBorderDefault
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MtnYellowGlass
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianCardGradient
import com.example.ui.theme.RestrainedGreen
import com.example.ui.theme.RestrainedRed
import com.example.ui.theme.SoftWhite
import com.example.ui.theme.SubtleGrey

@Composable
fun AtmosphericGlassBackground(
    modifier: Modifier = Modifier,
    showWatermark: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AbsoluteBlack)
    ) {
        // Refined subtle electric indigo ambient light in obsidian dark space
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-right subtle electric indigo aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricIndigo.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(width * 0.85f, height * 0.10f),
                    radius = width * 0.70f
                )
            )

            // Center-left faint obsidian aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x0F7775FF), Color.Transparent),
                    center = Offset(width * 0.10f, height * 0.50f),
                    radius = width * 0.60f
                )
            )
        }

        if (showWatermark) {
            // Futuristic transparent Echo Money Obsidian insignia watermark
            Image(
                painter = painterResource(id = R.drawable.echo_money_icon_1793380992762),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(380.dp)
                    .offset(y = (-40).dp)
                    .alpha(0.12f),
                contentScale = ContentScale.Fit
            )
        }

        content()
    }
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderBrush: Brush = GlassBorderBrush,
    backgroundBrush: Brush = ObsidianCardGradient,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundBrush)
            .border(BorderStroke(1.dp, borderBrush), shape),
        content = content
    )
}

@Composable
fun ProviderBadge(
    provider: String,
    modifier: Modifier = Modifier
) {
    val (glassBg, textGlowColor, label) = when (provider.uppercase()) {
        "AIRTEL" -> Triple(AirtelRedGlass, AirtelRed, "AIRTEL")
        "MTN" -> Triple(MtnYellowGlass, MtnYellow, "MTN")
        "CASH" -> Triple(CashTealGlass, CashTeal, "CASH")
        else -> Triple(Color(0x20FFFFFF), SoftWhite, provider)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(glassBg)
            .border(1.dp, textGlowColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textGlowColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = if (isSelected) {
        Brush.linearGradient(listOf(ElectricIndigo, Color(0xFF6361E6)))
    } else {
        Brush.linearGradient(listOf(Color(0x8014141C), Color(0x600F0F14)))
    }
    val borderColor = if (isSelected) ElectricIndigoLight else GlassBorderDefault

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else MutedSilver,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    amount: Double,
    isPositive: Boolean? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val textColor = when (isPositive) {
        true -> RestrainedGreen
        false -> RestrainedRed
        null -> SoftWhite
    }

    GlassBox(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderDefault))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = MutedSilver,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ZMW ${"%,.2f".format(amount)}",
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = SubtleGrey,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
