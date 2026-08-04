package com.example.hisab.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Extended Colors (non-Material3) ──────────────────────

data class HisabExtendedColors(
    val income: Color,
    val incomeSurface: Color,
    val expense: Color,
    val expenseSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardBorder: Color,
    val surfaceCard: Color,
    val innerSurface: Color,
    val glass: Color,
    val glassBorder: Color,
    val chartColors: List<Color>,
    val heatmapZero: Color,
    val heatmapLow: Color,
    val heatmapMedium: Color,
    val heatmapHigh: Color,
    val heatmapMax: Color
)

val LocalHisabColors = staticCompositionLocalOf {
    HisabExtendedColors(
        income = IncomeGreen,
        incomeSurface = IncomeGreenSurface,
        expense = ExpenseRed,
        expenseSurface = ExpenseRedSurface,
        warning = WarningAmber,
        warningSurface = WarningAmberSurface,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textTertiary = DarkTextTertiary,
        cardBorder = DarkCardBorder,
        surfaceCard = Color(0xFF161620),
        innerSurface = Color(0xFF12121A),
        glass = GlassWhite,
        glassBorder = GlassBorder,
        chartColors = ChartColors,
        heatmapZero = HeatmapZero,
        heatmapLow = HeatmapLow,
        heatmapMedium = HeatmapMedium,
        heatmapHigh = HeatmapHigh,
        heatmapMax = HeatmapMax
    )
}

// ── Material3 Color Schemes ──────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = DarkBackground,
    primaryContainer = PrimaryTealSurface,
    onPrimaryContainer = PrimaryTealLight,
    secondary = ChartColor2,
    onSecondary = DarkBackground,
    secondaryContainer = Color(0xFF1A1A3D),
    onSecondaryContainer = ChartColor2,
    tertiary = ChartColor4,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder,
    outlineVariant = Color(0xFF1E1E2E),
    inverseSurface = LightSurface,
    inverseOnSurface = LightTextPrimary,
    error = ExpenseRed,
    onError = DarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5E9),
    onPrimaryContainer = Color(0xFF003822),
    secondary = Color(0xFF4A5568),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1A202C),
    tertiary = Color(0xFF805AD5),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    outlineVariant = Color(0xFFE2E4EA),
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkTextPrimary,
    error = ExpenseRedDark,
    onError = Color.White
)

// ── Extended Colors per theme ────────────────────────────

private val DarkExtendedColors = HisabExtendedColors(
    income = IncomeGreen,
    incomeSurface = IncomeGreenSurface,
    expense = ExpenseRed,
    expenseSurface = ExpenseRedSurface,
    warning = WarningAmber,
    warningSurface = WarningAmberSurface,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    cardBorder = Color(0xFF2C2C3A),
    surfaceCard = Color(0xFF161620),
    innerSurface = Color(0xFF12121A),
    glass = GlassWhite,
    glassBorder = GlassBorder,
    chartColors = ChartColors,
    heatmapZero = HeatmapZero,
    heatmapLow = HeatmapLow,
    heatmapMedium = HeatmapMedium,
    heatmapHigh = HeatmapHigh,
    heatmapMax = HeatmapMax
)

private val LightExtendedColors = HisabExtendedColors(
    income = IncomeGreenDark,
    incomeSurface = Color(0xFFE8F5E9),
    expense = ExpenseRedDark,
    expenseSurface = Color(0xFFFFEBEE),
    warning = Color(0xFFF57C00),
    warningSurface = Color(0xFFFFF3E0),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    cardBorder = Color(0xFFE2E8F0),
    surfaceCard = Color(0xFFFFFFFF),
    innerSurface = Color(0xFFF0F2F6),
    glass = GlassWhiteLight,
    glassBorder = GlassBorderLight,
    chartColors = ChartColors,
    heatmapZero = HeatmapZeroLight,
    heatmapLow = HeatmapLowLight,
    heatmapMedium = HeatmapMediumLight,
    heatmapHigh = HeatmapHighLight,
    heatmapMax = HeatmapMaxLight
)

// ── Theme Composable ─────────────────────────────────────

@Composable
fun HisabAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalHisabColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HisabTypography,
            content = content
        )
    }
}

/**
 * Convenience accessor for extended colors from any composable.
 */
object HisabTheme {
    val colors: HisabExtendedColors
        @Composable
        get() = LocalHisabColors.current
}