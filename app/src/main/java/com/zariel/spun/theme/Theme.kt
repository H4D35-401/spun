package com.zariel.spun.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Spun Material3 color schemes
//
// Dark scheme is the primary design target. Light scheme is provided so the
// app behaves sensibly when the user switches to system light mode.
//
// Dynamic color (Material You) is disabled by default — Spun's identity is
// the amber/warm-neutral palette, not the user's wallpaper color.
// ---------------------------------------------------------------------------

private val SpunDarkColorScheme = darkColorScheme(
    primary          = SpunAmber,
    onPrimary        = SpunBlack,
    primaryContainer = SpunAmberDim,
    onPrimaryContainer = SpunOnSurface,

    secondary        = SpunOnSurfaceMed,
    onSecondary      = SpunBlack,
    secondaryContainer = SpunSurfaceHigh,
    onSecondaryContainer = SpunOnSurface,

    tertiary         = SpunSuccess,
    onTertiary       = SpunBlack,

    background       = SpunBlack,
    onBackground     = SpunOnSurface,

    surface          = SpunSurface,
    onSurface        = SpunOnSurface,
    surfaceVariant   = SpunSurfaceMid,
    onSurfaceVariant = SpunOnSurfaceMed,

    outline          = SpunOutline,
    outlineVariant   = SpunOutlineWeak,

    error            = SpunError,
    onError          = SpunBlack,
)

private val SpunLightColorScheme = lightColorScheme(
    primary          = SpunAmberDim,
    onPrimary        = SpunLightSurface,
    primaryContainer = SpunAmber,
    onPrimaryContainer = SpunLightOnSurface,

    secondary        = SpunLightOnSurfaceMed,
    onSecondary      = SpunLightSurface,
    secondaryContainer = SpunLightSurfaceMid,
    onSecondaryContainer = SpunLightOnSurface,

    background       = SpunLightBg,
    onBackground     = SpunLightOnSurface,

    surface          = SpunLightSurface,
    onSurface        = SpunLightOnSurface,
    surfaceVariant   = SpunLightSurfaceMid,
    onSurfaceVariant = SpunLightOnSurfaceMed,

    outline          = SpunLightOutline,

    error            = SpunError,
)

@Composable
fun SpunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — Spun has its own visual identity.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SpunDarkColorScheme
        else      -> SpunLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SpunTypography,
        content     = content,
    )
}
