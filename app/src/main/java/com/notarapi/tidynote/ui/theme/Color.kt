package com.notarapi.tidynote.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Palet "Emerald Fintech".
// Hijau = uang & trust; amber dipakai untuk status yang perlu perhatian
// (PENDING), bukan merah — PENDING bukan error.
// ---------------------------------------------------------------------------

val TidyLightScheme =
    lightColorScheme(
        primary = Color(0xFF006B56),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF8FF6D6),
        onPrimaryContainer = Color(0xFF002019),
        inversePrimary = Color(0xFF73DABE),
        secondary = Color(0xFF4A635C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8DF),
        onSecondaryContainer = Color(0xFF06201A),
        tertiary = Color(0xFF7A5900),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF5BF48),
        onTertiaryContainer = Color(0xFF261A00),
        background = Color(0xFFF5FAF7),
        onBackground = Color(0xFF171D1B),
        surface = Color(0xFFF5FAF7),
        onSurface = Color(0xFF171D1B),
        surfaceVariant = Color(0xFFDBE5E0),
        onSurfaceVariant = Color(0xFF3F4946),
        surfaceTint = Color(0xFF006B56),
        inverseSurface = Color(0xFF2B3230),
        inverseOnSurface = Color(0xFFECF2EF),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6F7975),
        outlineVariant = Color(0xFFBFC9C4),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5FAF7),
        surfaceDim = Color(0xFFD5DEDA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F6F2),
        surfaceContainer = Color(0xFFEAF1ED),
        surfaceContainerHigh = Color(0xFFE4ECE8),
        surfaceContainerHighest = Color(0xFFDEE7E2),
    )

val TidyDarkScheme =
    darkColorScheme(
        primary = Color(0xFF55DEBE),
        onPrimary = Color(0xFF00382D),
        primaryContainer = Color(0xFF005142),
        onPrimaryContainer = Color(0xFF73FBD8),
        inversePrimary = Color(0xFF006B56),
        secondary = Color(0xFFB1CCC2),
        onSecondary = Color(0xFF1B3530),
        secondaryContainer = Color(0xFF324B45),
        onSecondaryContainer = Color(0xFFCCE8DF),
        tertiary = Color(0xFFF5BF48),
        onTertiary = Color(0xFF412D00),
        tertiaryContainer = Color(0xFF5D4200),
        onTertiaryContainer = Color(0xFFFFDF9C),
        background = Color(0xFF0B1411),
        onBackground = Color(0xFFDEE4E0),
        surface = Color(0xFF0B1411),
        onSurface = Color(0xFFDEE4E0),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFBFC9C4),
        surfaceTint = Color(0xFF55DEBE),
        inverseSurface = Color(0xFFDEE4E0),
        inverseOnSurface = Color(0xFF2B3230),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF89938F),
        outlineVariant = Color(0xFF3F4946),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF313A36),
        surfaceDim = Color(0xFF0B1411),
        surfaceContainerLowest = Color(0xFF060F0C),
        surfaceContainerLow = Color(0xFF141C19),
        surfaceContainer = Color(0xFF171F1C),
        surfaceContainerHigh = Color(0xFF212A26),
        surfaceContainerHighest = Color(0xFF2C3531),
    )

/** Warna aksen untuk grafik kategori; dipakai Dashboard, bukan bagian ColorScheme. */
val ChartPalette =
    listOf(
        Color(0xFF2FBF9E),
        Color(0xFF5B9DF9),
        Color(0xFFF5BF48),
        Color(0xFFB58CF0),
        Color(0xFFEF7C6B),
        Color(0xFF6F8F88),
    )
