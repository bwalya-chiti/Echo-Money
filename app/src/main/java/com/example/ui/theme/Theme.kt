package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EchoMoneyColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoDark,
    onPrimaryContainer = SoftWhite,
    secondary = ElectricIndigoLight,
    onSecondary = AbsoluteBlack,
    secondaryContainer = ObsidianGlassCardSecondary,
    onSecondaryContainer = SoftWhite,
    tertiary = RestrainedGreen,
    onTertiary = Color.White,
    background = AbsoluteBlack,
    onBackground = SoftWhite,
    surface = ObsidianBlack,
    onSurface = SoftWhite,
    surfaceVariant = ObsidianGlassCard,
    onSurfaceVariant = MutedSilver,
    outline = GlassBorderDefault,
    outlineVariant = ObsidianDivider,
    error = RestrainedRed,
    onError = Color.White
)

@Composable
fun EchoMoneyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EchoMoneyColorScheme,
        typography = Typography,
        content = content
    )
}
