package com.kidsshorts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Calm, low-contrast-shock palette. No bright reward colours, no animation. */
private val Colors = lightColorScheme(
    primary = Color(0xFF3B6EA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E6F7),
    onPrimaryContainer = Color(0xFF0E2A47),
    secondary = Color(0xFF4F7F6F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EBE4),
    onSecondaryContainer = Color(0xFF123227),
    tertiary = Color(0xFF8A6D3B),
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF5C1511),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEF3),
)

@Composable
fun KidsShortsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

/** Child-mode colours (dark, gentle). */
object ChildColors {
    val background = Color(0xFF111A2B)
    val onBackground = Color(0xFFF1F4F8)
    val muted = Color(0xFFB4C0D0)
    val button = Color(0xFF2F4A73)
}
