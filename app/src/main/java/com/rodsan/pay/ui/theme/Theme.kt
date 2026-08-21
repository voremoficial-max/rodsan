package com.rodsan.pay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorBlack = Color(0xFF000000)
private val ColorWhite = Color(0xFFF5F5F5)

private val RodSanDarkColors = darkColorScheme(
    primary = OrangeMandarinDark,
    onPrimary = ColorBlack,
    primaryContainer = OrangeMandarinContainerDark,
    onPrimaryContainer = OrangeMandarinLight,
    secondary = OrangeMandarinLight,
    background = BackgroundDark,
    onBackground = ColorWhite,
    surface = SurfaceDark,
    onSurface = ColorWhite,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB6B6B6),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF292929)
)

@Composable
fun RodSanTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RodSanDarkColors,
        typography = Typography,
        content = content
    )
}
