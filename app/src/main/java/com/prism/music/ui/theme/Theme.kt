package com.prism.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentWhite,
    secondary = MutedText,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkBackground,
    onSecondary = AccentWhite,
    onBackground = AccentWhite,
    onSurface = AccentWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedText
)

@Composable
fun PrismMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
