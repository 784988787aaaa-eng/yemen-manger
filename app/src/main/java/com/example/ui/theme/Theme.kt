package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = YemenTokens.DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D2D5E),
    onPrimaryContainer = YemenTokens.DarkTextPrimary,
    secondary = YemenTokens.DarkSecondary,
    onSecondary = Color(0xFF062922),
    secondaryContainer = Color(0xFF113D34),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = YemenTokens.DarkWarning,
    onTertiary = Color(0xFF281B00),
    background = YemenTokens.DarkBackground,
    onBackground = YemenTokens.DarkTextPrimary,
    surface = YemenTokens.DarkSurface,
    onSurface = YemenTokens.DarkTextPrimary,
    surfaceVariant = YemenTokens.DarkSurfaceVariant,
    onSurfaceVariant = YemenTokens.DarkTextSecondary,
    error = YemenTokens.DarkError,
    onError = Color.White,
    outline = YemenTokens.DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = YemenTokens.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF091B57),
    secondary = YemenTokens.LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = YemenTokens.LightWarning,
    onTertiary = Color.White,
    background = YemenTokens.LightBackground,
    onBackground = YemenTokens.LightTextPrimary,
    surface = YemenTokens.LightSurface,
    onSurface = YemenTokens.LightTextPrimary,
    surfaceVariant = YemenTokens.LightSurfaceVariant,
    onSurfaceVariant = YemenTokens.LightTextSecondary,
    error = YemenTokens.LightError,
    onError = Color.White,
    outline = YemenTokens.LightOutline
)

@Composable
fun YemenManagerTheme(
    darkTheme: Boolean = true,
    isArabicRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val layoutDirection = if (isArabicRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    YemenManagerTheme(darkTheme = darkTheme, isArabicRtl = true, content = content)
}
