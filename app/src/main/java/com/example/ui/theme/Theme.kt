package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BroadcastColorScheme = darkColorScheme(
    primary = CrimsonLive,
    onPrimary = Color.White,
    primaryContainer = CrimsonLiveDark,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldActive,
    onSecondary = Color.Black,
    secondaryContainer = EmeraldActiveDark,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanAccent,
    onTertiary = Color.Black,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BroadcastColorScheme,
        typography = Typography,
        content = content
    )
}
