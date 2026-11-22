package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class TranscribeExtendedColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val card2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val onAccentMuted: Color,
    val soft: Color,
    val softText: Color,
    val accentText: Color,
    val good: Color,
    val goodBg: Color,
    val warn: Color,
    val warnBg: Color,
    val danger: Color,
    val stage: Color,
    val waveOff: Color,
    val toggleOff: Color,
    val overlay: Color
)

private val DarkExtended = TranscribeExtendedColors(
    isDark = true,
    bg = DarkBg,
    card = DarkCard,
    card2 = DarkCard2,
    line = DarkLine,
    text = DarkText,
    muted = DarkMuted,
    accent = DarkAccent,
    onAccent = DarkOnAccent,
    onAccentMuted = DarkOnAccentMuted,
    soft = DarkSoft,
    softText = DarkSoftText,
    accentText = DarkAccentText,
    good = DarkGood,
    goodBg = DarkGoodBg,
    warn = DarkWarn,
    warnBg = DarkWarnBg,
    danger = DarkDanger,
    stage = DarkStage,
    waveOff = DarkWaveOff,
    toggleOff = DarkToggleOff,
    overlay = DarkOverlay
)

private val LightExtended = TranscribeExtendedColors(
    isDark = false,
    bg = LightBg,
    card = LightCard,
    card2 = LightCard2,
    line = LightLine,
    text = LightText,
    muted = LightMuted,
    accent = LightAccent,
    onAccent = LightOnAccent,
    onAccentMuted = LightOnAccentMuted,
    soft = LightSoft,
    softText = LightSoftText,
    accentText = LightAccentText,
    good = LightGood,
    goodBg = LightGoodBg,
    warn = LightWarn,
    warnBg = LightWarnBg,
    danger = LightDanger,
    stage = LightStage,
    waveOff = LightWaveOff,
    toggleOff = LightToggleOff,
    overlay = LightOverlay
)

val LocalTranscribeColors = staticCompositionLocalOf { DarkExtended }

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkSoft,
    onPrimaryContainer = DarkSoftText,
    secondary = DarkAccentText,
    onSecondary = DarkOnAccent,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkCard2,
    onSurfaceVariant = DarkMuted,
    outline = DarkLine,
    outlineVariant = DarkLine,
    error = DarkDanger,
    onError = DarkOnAccent
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightSoft,
    onPrimaryContainer = LightSoftText,
    secondary = LightAccentText,
    onSecondary = LightOnAccent,
    background = LightBg,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightCard2,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    outlineVariant = LightLine,
    error = LightDanger,
    onError = LightOnAccent
)

object TranscribeThemeTokens {
    val colors: TranscribeExtendedColors
        @Composable get() = LocalTranscribeColors.current
}

@Composable
fun TranscribeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalTranscribeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TranscribeTypography,
            content = content
        )
    }
}
