package com.clothcall.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ClothCallColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = AccentLight,
    onPrimaryContainer = NavyDark,
    secondary = Accent,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = Surface,
    onBackground = OnSurface,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = OnSurface,
    outline = Muted,
)

@Composable
fun ClothCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClothCallColorScheme,
        typography = Typography,
        content = content
    )
}
