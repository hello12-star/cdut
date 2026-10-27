package com.yh.schedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 成理蓝主色（参照学校视觉的深蓝），辅以暖黄强调
val CduBlue = Color(0xFF1E5AA8)
val CduBlueLight = Color(0xFFE8F0FB)
val CduAmber = Color(0xFFF2B705)
val ExamBlue = Color(0xFF1565C0)
val SurfaceSoft = Color(0xFFF3F5F9)

private val LightColors = lightColorScheme(
    primary = CduBlue,
    onPrimary = Color.White,
    primaryContainer = CduBlueLight,
    onPrimaryContainer = Color(0xFF0D2B54),
    secondary = CduAmber,
    background = SurfaceSoft,
    surface = Color.White,
    surfaceVariant = CduBlueLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC1F0),
    onPrimary = Color(0xFF0D2B54),
    primaryContainer = Color(0xFF2A4C7E),
    onPrimaryContainer = Color(0xFFD8E5F8),
    secondary = CduAmber,
    background = Color(0xFF121417),
    surface = Color(0xFF1B1E23),
    surfaceVariant = Color(0xFF232830),
)

@Composable
fun YhTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
