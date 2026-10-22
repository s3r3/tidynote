package com.notarapi.tidynote.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.notarapi.tidynote.core.prefs.ThemeMode
import com.notarapi.tidynote.ui.design.Motion

/** Apakah tema gelap sedang aktif. Dipakai komponen yang perlu alpha berbeda. */
val LocalTidyDark = staticCompositionLocalOf { false }

@Composable
fun TidyNoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    CompositionLocalProvider(LocalTidyDark provides dark) {
        MaterialTheme(
            // Crossfade penuh lewat `Crossfade(targetState = dark)` akan membuang
            // seluruh subtree termasuk state navigasi. Sebagai gantinya warna yang
            // paling terlihat dipermulus; sisanya berganti instan.
            colorScheme = (if (dark) TidyDarkScheme else TidyLightScheme).animated(),
            typography = TidyNoteTypography,
            shapes = TidyNoteShapes,
            content = content,
        )
    }
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = Motion.MEDIUM, easing = Motion.FastOutSlowIn)
    // animateColorAsState harus dipanggil langsung di scope composable — helper
    // lokal non-@Composable tidak bisa memanggilnya. Karena itu setiap field
    // ditulis eksplisit meski terlihat berulang.
    return copy(
        primary = animateColorAsState(primary, spec, label = "primary").value,
        primaryContainer = animateColorAsState(primaryContainer, spec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, spec, label = "onPrimaryContainer").value,
        background = animateColorAsState(background, spec, label = "background").value,
        surface = animateColorAsState(surface, spec, label = "surface").value,
        onSurface = animateColorAsState(onSurface, spec, label = "onSurface").value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        surfaceContainerLow = animateColorAsState(surfaceContainerLow, spec, label = "surfaceContainerLow").value,
        surfaceContainer = animateColorAsState(surfaceContainer, spec, label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, spec, label = "surfaceContainerHigh").value,
        outlineVariant = animateColorAsState(outlineVariant, spec, label = "outlineVariant").value,
    )
}
