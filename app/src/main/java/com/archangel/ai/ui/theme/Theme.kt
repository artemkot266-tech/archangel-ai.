package com.archangel.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArchangelColors = darkColorScheme(
    primary = NeonBlue,
    onPrimary = NightBackground,
    secondary = NeonMint,
    onSecondary = NightBackground,
    tertiary = Amber,
    background = NightBackground,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Panel,
    outline = StrokeColor,
    error = Danger,
)

@Composable
fun ArchangelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ArchangelColors, content = content)
}
