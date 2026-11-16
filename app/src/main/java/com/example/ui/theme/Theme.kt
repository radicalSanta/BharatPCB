package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Immutable
data class PcbSemanticColors(
    val isDark: Boolean,
    val canvas: Color,
    val surfaceCard: Color,
    val surfaceSubtle: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Shared anchor role (Overview card, Primary Selected CTA, Selected Nav Pill)
    val anchorSurface: Color,
    val onAnchorSurface: Color,
    val anchorSubtleText: Color,
    val anchorDivider: Color,
    // Navigation bar / rail tokens
    val navContainer: Color,
    val navBorder: Color,
    val navSelectedPill: Color,
    val onNavSelectedPill: Color,
    val navUnselected: Color,
    // Secondary tonal action tokens
    val tonalButtonSurface: Color,
    val onTonalButtonSurface: Color,
    // Status semantics
    val passFg: Color,
    val passBg: Color,
    val reviewFg: Color,
    val reviewBg: Color,
    val rejectFg: Color,
    val rejectBg: Color,
    val incompleteFg: Color,
    val incompleteBg: Color
)

private val LightPcbColors = PcbSemanticColors(
    isDark = false,
    canvas = LightCanvas,
    surfaceCard = WhiteSurface,
    surfaceSubtle = SecondarySurface,
    divider = LightDivider,
    textPrimary = DeepestNavy,
    textSecondary = Slate,
    textMuted = Slate,
    anchorSurface = DeepNavy,
    onAnchorSurface = WhiteSurface,
    anchorSubtleText = PaleGrey,
    anchorDivider = Color(0xFF2B3F4E),
    navContainer = WhiteSurface,
    navBorder = LightDivider,
    navSelectedPill = DeepNavy,
    onNavSelectedPill = WhiteSurface,
    navUnselected = Slate,
    tonalButtonSurface = SecondarySurface,
    onTonalButtonSurface = DeepNavy,
    passFg = StatusPassLight,
    passBg = StatusPassLightBg,
    reviewFg = StatusReviewLight,
    reviewBg = StatusReviewLightBg,
    rejectFg = StatusRejectLight,
    rejectBg = StatusRejectLightBg,
    incompleteFg = StatusIncompleteLight,
    incompleteBg = StatusIncompleteLightBg
)

private val DarkPcbColors = PcbSemanticColors(
    isDark = true,
    canvas = DeepestNavy,
    surfaceCard = DeepNavy,
    surfaceSubtle = BlueGrey,
    divider = DarkDivider,
    textPrimary = WhiteSurface,
    textSecondary = PaleGrey,
    textMuted = MutedGrey,
    anchorSurface = DarkAnchorSurface,
    onAnchorSurface = DeepestNavy,
    anchorSubtleText = Slate,
    anchorDivider = Color(0xFFC4C9C8),
    navContainer = DeepNavy,
    navBorder = BlueGrey,
    navSelectedPill = DarkAnchorSurface,
    onNavSelectedPill = DeepestNavy,
    navUnselected = MutedGrey,
    tonalButtonSurface = BlueGrey,
    onTonalButtonSurface = WhiteSurface,
    passFg = StatusPassDark,
    passBg = StatusPassDarkBg,
    reviewFg = StatusReviewDark,
    reviewBg = StatusReviewDarkBg,
    rejectFg = StatusRejectDark,
    rejectBg = StatusRejectDarkBg,
    incompleteFg = StatusIncompleteDark,
    incompleteBg = StatusIncompleteDarkBg
)

val LocalPcbColors = staticCompositionLocalOf { LightPcbColors }

private val LightMaterialColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = WhiteSurface,
    primaryContainer = SecondarySurface,
    onPrimaryContainer = DeepestNavy,
    secondary = BlueGrey,
    onSecondary = WhiteSurface,
    secondaryContainer = SecondarySurface,
    onSecondaryContainer = DeepNavy,
    tertiary = Slate,
    onTertiary = WhiteSurface,
    background = LightCanvas,
    onBackground = DeepestNavy,
    surface = WhiteSurface,
    onSurface = DeepestNavy,
    surfaceVariant = SecondarySurface,
    onSurfaceVariant = Slate,
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = StatusRejectLight,
    onError = WhiteSurface,
    errorContainer = StatusRejectLightBg,
    onErrorContainer = StatusRejectLight
)

private val DarkMaterialColorScheme = darkColorScheme(
    primary = DarkAnchorSurface,
    onPrimary = DeepestNavy,
    primaryContainer = BlueGrey,
    onPrimaryContainer = WhiteSurface,
    secondary = PaleGrey,
    onSecondary = DeepestNavy,
    secondaryContainer = BlueGrey,
    onSecondaryContainer = WhiteSurface,
    tertiary = MutedGrey,
    onTertiary = DeepestNavy,
    background = DeepestNavy,
    onBackground = WhiteSurface,
    surface = DeepNavy,
    onSurface = WhiteSurface,
    surfaceVariant = BlueGrey,
    onSurfaceVariant = PaleGrey,
    outline = Slate,
    outlineVariant = DarkDivider,
    error = StatusRejectDark,
    onError = DeepestNavy,
    errorContainer = StatusRejectDarkBg,
    onErrorContainer = StatusRejectDark
)

@Composable
fun BharatPcbTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkMaterialColorScheme else LightMaterialColorScheme
    val semanticColors = if (isDark) DarkPcbColors else LightPcbColors

    CompositionLocalProvider(LocalPcbColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BharatTypography,
            shapes = BharatShapes,
            content = content
        )
    }
}
