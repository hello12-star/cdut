package com.yh.schedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    val bgColor = if (darkTheme) DarkColors.background else LightColors.background
    if (!view.isInEditMode) {
        // 只在明暗主题切换时刷新系统栏；每次重组都设置会触发状态栏颜色过渡，
        // 下拉刷新时表现为顶部先闪白再渐变回深色。
        LaunchedEffect(darkTheme) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            // 状态栏/导航栏区域实际透出的是 Window 背景；只改图标明暗不够，
            // 必须把 Window 背景也刷成主题底色，否则深色主题顶部会留一条白边。
            window.setBackgroundDrawable(ColorDrawable(bgColor.toArgb()))
            // 下拉/回弹时状态栏区域会短暂露出 DecorView 的默认底色，
            // 只改 Window 背景不够，DecorView 也要同步刷成主题色。
            window.decorView.setBackgroundColor(bgColor.toArgb())
            @Suppress("DEPRECATION")
            window.statusBarColor = bgColor.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = bgColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
