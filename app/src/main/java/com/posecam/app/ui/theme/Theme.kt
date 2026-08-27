package com.posecam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PoseCamDark = darkColorScheme(
    primary = Color(0xFFFFD166),
    onPrimary = Color(0xFF14141A),
    secondary = Color(0xFFEF476F),
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF15151B),
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF23232B),
    onSurfaceVariant = Color(0xFFB9B9C3),
    outline = Color(0xFF3A3A44)
)

@Composable
fun PoseCamTheme(content: @Composable () -> Unit) {
    // 相机应用保持深色界面，忽略系统浅色模式
    MaterialTheme(
        colorScheme = PoseCamDark,
        typography = Typography(),
        content = content
    )
}
