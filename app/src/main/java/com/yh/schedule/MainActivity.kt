package com.yh.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.yh.schedule.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容绘制到状态栏后面：状态栏区域始终由 Compose 根背景填充，
        // 下拉刷新/窗口动画时不会露出系统窗口的白色底。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as CdutApp
        setContent {
            // 主题在 AppRoot 内部包裹：深色模式是用户偏好，需要读取 DataStore
            AppRoot(app)
        }
    }
}
