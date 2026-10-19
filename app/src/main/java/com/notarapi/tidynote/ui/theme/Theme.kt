package com.notarapi.tidynote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF6750A4),
        secondary = Color(0xFF625B71),
        tertiary = Color(0xFF7D5260),
        surface = Color(0xFF1C1B1F),
        background = Color(0xFF1C1B1F),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onBackground = Color(0xFFFFFFFF),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF6750A4),
        secondary = Color(0xFF625B71),
        tertiary = Color(0xFF7D5260),
        surface = Color(0xFFFFFBFE),
        background = Color(0xFFFFFBFE),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C1B1F),
        onBackground = Color(0xFF1C1B1F),
    )

@Composable
fun TidyNoteTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TidyNoteTypography,
        shapes = TidyNoteShapes,
        content = content,
    )
}
