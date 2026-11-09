package com.fluxie.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fluxie.browser.common.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = FluxieInk,
    onPrimary = Color.White,
    primaryContainer = FluxieLightHighest,
    onPrimaryContainer = FluxieInk,
    background = FluxieLightSurface,
    onBackground = FluxieInk,
    surface = FluxieLightSurface,
    onSurface = FluxieInk,
    surfaceVariant = FluxieLightHigh,
    onSurfaceVariant = FluxieLightGrey,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FluxieLightLow,
    surfaceContainer = FluxieLightMid,
    surfaceContainerHigh = FluxieLightHigh,
    surfaceContainerHighest = FluxieLightHighest,
    outline = FluxieLightOutline,
    outlineVariant = FluxieLightHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = FluxieWhite,
    onPrimary = FluxieBlack,
    primaryContainer = FluxieSurfaceHighest,
    onPrimaryContainer = FluxieWhite,
    background = FluxieBlack,
    onBackground = FluxieWhite,
    surface = FluxieSurface,
    onSurface = FluxieWhite,
    surfaceVariant = FluxieSurfaceHigh,
    onSurfaceVariant = FluxieGrey,
    surfaceContainerLowest = FluxieBlack,
    surfaceContainerLow = FluxieSurfaceLow,
    surfaceContainer = FluxieSurfaceMid,
    surfaceContainerHigh = FluxieSurfaceHigh,
    surfaceContainerHighest = FluxieSurfaceHighest,
    outline = FluxieOutline,
    outlineVariant = FluxieOutlineVariant,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** True when [themeMode] resolves to the dark theme (SYSTEM follows the device). */
@Composable
fun isDarkFor(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FluxieTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isDarkFor(themeMode)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
