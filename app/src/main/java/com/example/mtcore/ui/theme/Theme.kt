package com.example.mtcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPalette = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF2E7D32),
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111)
)

@Composable
fun MTCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightPalette, content = content)
}
