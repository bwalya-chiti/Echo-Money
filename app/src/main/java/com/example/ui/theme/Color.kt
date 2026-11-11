package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// BLACK OBSIDIAN GLASS DESIGN SYSTEM — ECHO MONEY
val AbsoluteBlack = Color(0xFF050505) // Primary background
val ObsidianBlack = Color(0xFF0B0B0E) // Secondary surfaces
val ObsidianGlassCard = Color(0xB8121218) // Translucent dark glass cards
val ObsidianGlassCardSecondary = Color(0x80161620)
val ObsidianGlassCardElevated = Color(0xCC1A1A24)

// Understated Grey Borders & Specular Glass Reflection
val GlassBorderTop = Color(0x38FFFFFF) // Subtle specular top edge
val GlassBorderBottom = Color(0x12FFFFFF)
val GlassBorderDefault = Color(0x1F8E8E93) // Thin understated grey border
val GlassBorderHighlight = Color(0x407775FF)

// Primary Accent — Electric Indigo
val ElectricIndigo = Color(0xFF7775FF)
val ElectricIndigoLight = Color(0xFF9E9DFF)
val ElectricIndigoDark = Color(0xFF5552E0)
val ElectricIndigoMuted = Color(0x267775FF)
val ElectricViolet = Color(0xFF7775FF)
val NeonCyan = Color(0xFF9E9DFF)
val ObsidianHighlight = Color(0x407775FF)

// Financial Status Indicators (Restrained)
val RestrainedGreen = Color(0xFF10B981) // Restrained Green
val RestrainedGreenContainer = Color(0x2610B981)
val RestrainedRed = Color(0xFFEF4444) // Restrained Red
val RestrainedRedContainer = Color(0x26EF4444)
val RestrainedYellow = Color(0xFFF59E0B) // Restrained Yellow

// Carrier Glass Accents (Subtle & Elegant)
val AirtelRed = Color(0xFFF43F5E)
val AirtelRedGlass = Color(0x24F43F5E)
val MtnYellow = Color(0xFFF59E0B)
val MtnYellowGlass = Color(0x24F59E0B)
val CashTeal = Color(0xFF14B8A6)
val CashTealGlass = Color(0x2414B8A6)

// Typography & Neutrals
val SoftWhite = Color(0xFFF9F9FB) // Soft white typography
val MutedSilver = Color(0xFFA1A1AA) // Muted silver secondary text
val SubtleGrey = Color(0xFF71717A)
val ObsidianDivider = Color(0x1AFFFFFF)

// Legacy aliases for backward compatibility
val OffWhite = SoftWhite
val MutedGrey = MutedSilver
val NearBlack = AbsoluteBlack
val DarkCharcoal = ObsidianBlack
val SlateCard = ObsidianGlassCard
val SlateCardBorder = GlassBorderDefault
val SlateCardSecondary = ObsidianGlassCardSecondary
val GlassBackground = AbsoluteBlack
val GlassSurface = ObsidianGlassCard
val GlassSurfaceCard = ObsidianGlassCard

// Obsidian Glass Brushes
val GlassBorderBrush = Brush.verticalGradient(
    listOf(GlassBorderTop, GlassBorderBottom)
)

val ObsidianCardGradient = Brush.verticalGradient(
    listOf(Color(0xD914141C), Color(0xB80E0E14))
)

val ObsidianGlowBrush = Brush.radialGradient(
    colors = listOf(Color(0x1F7775FF), Color.Transparent)
)
