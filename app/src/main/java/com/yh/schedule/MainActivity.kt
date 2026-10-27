package com.yh.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yh.schedule.ui.AppRoot
import com.yh.schedule.ui.theme.YhTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CdutApp
        setContent {
            YhTheme {
                AppRoot(app)
            }
        }
    }
}
