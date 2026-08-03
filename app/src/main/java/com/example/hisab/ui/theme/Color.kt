package com.example.hisab.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dark Mode Palette ────────────────────────────────────

val DarkBackground = Color(0xFF0F0F14)
val DarkSurface = Color(0xFF1A1A24)
val DarkSurfaceVariant = Color(0xFF232333)
val DarkCardBorder = Color(0xFF2A2A3C)

// ── Light Mode Palette ───────────────────────────────────

val LightBackground = Color(0xFFF8F9FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F1F5)
val LightCardBorder = Color(0xFFE2E4EA)

// ── Primary / Accent ─────────────────────────────────────

val PrimaryTeal = Color(0xFF00E5A0)
val PrimaryTealDark = Color(0xFF00C48C)
val PrimaryTealLight = Color(0xFF33EAAF)
val PrimaryTealSurface = Color(0xFF0A2A1F)

// ── Semantic Colors ──────────────────────────────────────

val IncomeGreen = Color(0xFF00E676)
val IncomeGreenDark = Color(0xFF00C853)
val IncomeGreenSurface = Color(0xFF0A2E1A)

val ExpenseRed = Color(0xFFFF5252)
val ExpenseRedDark = Color(0xFFD32F2F)
val ExpenseRedSurface = Color(0xFF2E0A0A)

val WarningAmber = Color(0xFFFFAB40)
val WarningAmberSurface = Color(0xFF2E2200)

// ── Text Colors ──────────────────────────────────────────

val DarkTextPrimary = Color(0xFFEEEEF0)
val DarkTextSecondary = Color(0xFF8E8E9A)
val DarkTextTertiary = Color(0xFF5A5A6A)

val LightTextPrimary = Color(0xFF1A1A2E)
val LightTextSecondary = Color(0xFF6B7280)
val LightTextTertiary = Color(0xFF9CA3AF)

// ── Chart Palette (8 distinct colors for pie/bar) ────────

val ChartColor1 = Color(0xFF00E5A0)  // Teal
val ChartColor2 = Color(0xFF5C6BC0)  // Indigo
val ChartColor3 = Color(0xFFFF7043)  // Deep Orange
val ChartColor4 = Color(0xFFAB47BC)  // Purple
val ChartColor5 = Color(0xFF26C6DA)  // Cyan
val ChartColor6 = Color(0xFFFFCA28)  // Amber
val ChartColor7 = Color(0xFFEF5350)  // Red
val ChartColor8 = Color(0xFF66BB6A)  // Green

val ChartColors = listOf(
    ChartColor1, ChartColor2, ChartColor3, ChartColor4,
    ChartColor5, ChartColor6, ChartColor7, ChartColor8
)

// ── Heatmap Intensity Colors ─────────────────────────────

val HeatmapZero = Color(0xFF1E1E2E)
val HeatmapLow = Color(0xFF1B3D2F)
val HeatmapMedium = Color(0xFF4A6023)
val HeatmapHigh = Color(0xFF8B5E1E)
val HeatmapMax = Color(0xFF8B2020)

val HeatmapZeroLight = Color(0xFFEEEFF3)
val HeatmapLowLight = Color(0xFFC8E6C9)
val HeatmapMediumLight = Color(0xFFFFF9C4)
val HeatmapHighLight = Color(0xFFFFCC80)
val HeatmapMaxLight = Color(0xFFEF9A9A)

// ── Glassmorphism ────────────────────────────────────────

val GlassWhite = Color(0x12FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val GlassWhiteLight = Color(0x0D000000)
val GlassBorderLight = Color(0x1A000000)