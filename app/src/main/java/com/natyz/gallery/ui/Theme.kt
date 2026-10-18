package com.natyz.gallery.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFE53935)

private val DarkColors = darkColorScheme(
    primary = Red,
    secondary = Color(0xFFEF9A9A)
)

private val LightColors = lightColorScheme(
    primary = Red,
    secondary = Color(0xFFC62828)
)

@Composable
fun NatyzTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
