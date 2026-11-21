package com.archangel.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Colors = darkColorScheme(
    primary = Ember,
    onPrimary = Obsidian,
    secondary = Bone,
    onSecondary = Obsidian,
    background = Obsidian,
    onBackground = Bone,
    surface = Surface1,
    onSurface = Bone,
    surfaceVariant = Surface2,
    onSurfaceVariant = Ash,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Hairline,
    outlineVariant = HairlineSoft,
    error = Ember,
)

private val Types = Typography(
    bodyLarge = TextStyles.Body,
    bodyMedium = TextStyles.BodySmall.copy(color = Bone),
    bodySmall = TextStyles.BodySmall,
    labelLarge = TextStyles.Button,
    labelMedium = TextStyles.Label,
    labelSmall = TextStyles.Label,
    titleLarge = TextStyles.Title,
    headlineLarge = TextStyles.Display,
)

@Composable
fun ArchangelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Types, content = content)
}
