package com.yh.schedule.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 用系统浏览器 / 邮件应用打开外部链接（GitHub、mailto 等） */
internal fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private data class OpenSourceItem(val name: String, val desc: String, val url: String)

/**
 * 本项目实际依赖的开源库（对照 app/build.gradle.kts 的 dependencies）。
 * 只列真正用到的，不照搬参考源码里我们没引入的库（Haze、Glide、Zxing 等）。
 */
private val OPEN_SOURCE = listOf(
    OpenSourceItem("Kotlin", "编程语言", "https://github.com/JetBrains/kotlin"),
    OpenSourceItem("Jetpack Compose", "声明式界面框架", "https://github.com/androidx/androidx"),
    OpenSourceItem("Material 3", "设计组件", "https://github.com/material-components/material-components-android"),
    OpenSourceItem("Navigation Compose", "页面导航", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    OpenSourceItem("Room", "本地数据库", "https://developer.android.com/jetpack/androidx/releases/room"),
    OpenSourceItem("DataStore", "偏好与登录态存储", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    OpenSourceItem("OkHttp", "网络请求", "https://github.com/square/okhttp"),
    OpenSourceItem("Jsoup", "HTML 解析", "https://github.com/jhy/jsoup"),
    OpenSourceItem("kotlinx.serialization", "JSON 解析", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceItem("Material Icons", "图标资源", "https://github.com/google/material-design-icons"),
    OpenSourceItem("Reorderable", "查询中心拖拽排序", "https://github.com/Calvin-LL/Reorderable"),
)

/**
 * 维护与关于页：参考源码 AboutDeveloper 的开源引用 / 用户协议分组。
 * 开发人员与仓库信息对应本项目（hello12-star/cdut），不是照抄参考项目作者。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "维护与关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { AboutSectionTitle("关于本应用") }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "掌上成理",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "包名 ${context.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                "版本 ${versionName.ifBlank { "未知" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                item { AboutSectionTitle("开发人员") }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "hello12-star",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "反馈邮箱 2145324169@qq.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item { AboutSectionTitle("开源引用") }
                items(OPEN_SOURCE.chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { openExternal(context, item.url) },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        item.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // 奇数个时补一个空位，保持两列对齐
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                item { AboutSectionTitle("用户协议") }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "本应用仅用于成都理工大学在校学生的个人课表与日程查询，不采集、不上传账号信息；" +
                                        "登录密码经 Android Keystore 加密后仅保存在本机。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "教务数据版权归学校所有。使用本应用产生的任何后果由使用者自行承担。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}
