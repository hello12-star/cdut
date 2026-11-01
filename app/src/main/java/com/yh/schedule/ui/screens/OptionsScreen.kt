package com.yh.schedule.ui.screens

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yh.schedule.core.SchoolCalendar
import com.yh.schedule.core.WebConst
import com.yh.schedule.data.Repository
import kotlinx.coroutines.launch

/**
 * 选项页：参考源码 HomeSettingScreen 的框架 —— 分组卡片 + 图标列表项。
 *
 * 原来的「服务」页已取消：它的办事入口（砚湖易办、智慧后勤、缴费平台、学生邮箱）
 * 已按源码的做法并入「查询中心」，这里只保留账号、接入方式与关于。
 */
@Composable
fun OptionsScreen(
    repo: Repository,
    demo: Boolean,
    onRelogin: () -> Unit,
    /** 打开「维护与关于」页（开源引用 / 用户协议） */
    onOpenLicenses: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }
    var showResetTerm by remember { mutableStateOf(false) }
    var termStart by remember { mutableStateOf(repo.currentTermStartMonday()) }
    LaunchedEffect(Unit) { account = repo.currentAccount() }

    val themeMode by repo.themeMode.collectAsState(initial = "system")
    val showWeekend by repo.showWeekendDefault.collectAsState(initial = false)
    val showOverdue by repo.showOverdueDefault.collectAsState(initial = false)
    val webViewDark by repo.webViewDark.collectAsState(initial = false)

    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "选项",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "账号、接入方式与应用信息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item {
            OptionSection("账号与登录") {
                OptionRow(
                    icon = Icons.Filled.Person,
                    title = "当前账号",
                    subtitle = when {
                        account.isNullOrBlank() -> "未登录"
                        demo -> "演示模式 · $account"
                        else -> account!!
                    },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Refresh,
                    title = "刷新登录状态 / 切换账号",
                    subtitle = "重新走统一认证，可改用另一个账号",
                    onClick = {
                        // 先清掉旧会话 Cookie：否则 CAS 会认旧登录态直接跳回去，换不了账号
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        onRelogin()
                    },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Logout,
                    title = "退出登录",
                    subtitle = "清除会话、课表缓存与网页 Cookie",
                    danger = true,
                    onClick = { showLogout = true },
                )
            }
        }

        item {
            OptionSection("外观") {
                OptionRow(
                    icon = Icons.Filled.DarkMode,
                    title = "深色模式",
                    subtitle = themeLabel(themeMode),
                    onClick = { showThemePicker = true },
                )
                OptionDivider()
                OptionSwitchRow(
                    icon = Icons.Filled.Web,
                    title = "网页强制深色",
                    subtitle = "仅在深色主题下生效；部分教务网页可能不支持",
                    checked = webViewDark,
                ) { on -> scope.launch { repo.setWebViewDark(on) } }
            }
        }

        item {
            OptionSection("显示") {
                OptionSwitchRow(
                    icon = Icons.Filled.DateRange,
                    title = "默认展开周末",
                    subtitle = "课表固定按 7 天显示；关闭时仅本周有周末课才展开",
                    checked = showWeekend,
                ) { on -> scope.launch { repo.setShowWeekendDefault(on) } }
                OptionDivider()
                OptionSwitchRow(
                    icon = Icons.Filled.History,
                    title = "聚焦页展开已过期",
                    subtitle = "进入聚焦页时默认展开已过期日程",
                    checked = showOverdue,
                ) { on -> scope.launch { repo.setShowOverdueDefault(on) } }
            }
        }

        item {
            OptionSection("网络接入") {
                OptionRow(
                    icon = Icons.Filled.Language,
                    title = "接入方式",
                    subtitle = if (WebConst.USE_VPN) {
                        "校园 VPN 代理域"
                    } else {
                        "校园网直连 · 校外需先连校园 VPN"
                    },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.CalendarMonth,
                    title = "学期起始日",
                    subtitle = "当前 ${termStart.monthValue}月${termStart.dayOfMonth}日；点按恢复默认 " +
                            "${SchoolCalendar.DEFAULT_TERM_START_MONDAY.monthValue}月" +
                            "${SchoolCalendar.DEFAULT_TERM_START_MONDAY.dayOfMonth}日",
                    onClick = { showResetTerm = true },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Delete,
                    title = "清除课表缓存",
                    subtitle = "删除本地课表缓存并重新抓取，不会退出登录",
                    onClick = { showClearCache = true },
                )
            }
        }

        item {
            OptionSection("维护与关于") {
                OptionRow(
                    icon = Icons.Filled.Email,
                    title = "反馈",
                    subtitle = "2145324169@qq.com",
                    onClick = { openExternal(context, "mailto:2145324169@qq.com") },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub 仓库",
                    subtitle = "github.com/hello12-star/cdut",
                    onClick = { openExternal(context, "https://github.com/hello12-star/cdut") },
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.MenuBook,
                    title = "开源引用",
                    subtitle = "本项目使用的开源库与用户协议",
                    onClick = onOpenLicenses,
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Person,
                    title = "开发人员",
                    subtitle = "hello12-star",
                )
                OptionDivider()
                OptionRow(
                    icon = Icons.Filled.Info,
                    title = "版本",
                    subtitle = versionName.ifBlank { "未知" },
                )
            }
        }
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("深色模式") },
            text = {
                Column {
                    listOf(
                        "system" to "跟随系统",
                        "light" to "始终浅色",
                        "dark" to "始终深色",
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repo.setThemeMode(value) }
                                    showThemePicker = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    scope.launch { repo.setThemeMode(value) }
                                    showThemePicker = false
                                },
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("取消") }
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录") },
            text = { Text("将清除登录态、课表缓存与网页 Cookie；自定义日程不会随登出删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    scope.launch {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        repo.logout()
                    }
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("取消") }
            },
        )
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("清除课表缓存") },
            text = { Text("将删除本地课表缓存，下次进入聚焦页会重新抓取；不会退出登录。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCache = false
                    scope.launch {
                        repo.clearTimetableCache()
                        Toast.makeText(context, "课表缓存已清除", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text("取消") }
            },
        )
    }

    if (showResetTerm) {
        AlertDialog(
            onDismissRequest = { showResetTerm = false },
            title = { Text("恢复默认学期起始日") },
            text = {
                Text(
                    "将恢复为 ${SchoolCalendar.DEFAULT_TERM_START_MONDAY}，课表周次会按默认日期重算。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetTerm = false
                    scope.launch {
                        repo.resetTermStartMonday()
                        termStart = repo.currentTermStartMonday()
                        Toast.makeText(context, "已恢复默认学期起始日", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetTerm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OptionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun OptionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 深色模式枚举值 → 展示文案 */
private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "始终浅色"
    "dark" -> "始终深色"
    else -> "跟随系统"
}

@Composable
private fun OptionSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
