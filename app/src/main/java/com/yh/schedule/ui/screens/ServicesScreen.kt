package com.yh.schedule.ui.screens

import android.webkit.CookieManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yh.schedule.data.Repository
import kotlinx.coroutines.launch

private data class ServiceEntry(
    val title: String,
    val desc: String,
    val url: String,
    val icon: ImageVector,
)

private const val JW = "https://jw.cdut.edu.cn/jsxsd"

private val SERVICES = listOf(
    ServiceEntry("考试安排", "考场 · 时间", "$JW/xsks/xsksap_query", Icons.Filled.Event),
    ServiceEntry("培养方案", "学分完成情况", "$JW/pyfa/topyfamx", Icons.Filled.MenuBook),
    ServiceEntry("选课结果", "本学期选课", "$JW/xkgl/xsxkjgcx", Icons.Filled.Checklist),
    ServiceEntry("已收公告", "教务通知", "$JW/ggly/ysgg_query", Icons.Filled.Campaign),
    ServiceEntry("砚湖易办", "一站式办事 · 可信凭证",
        "https://bsdt.cdut.edu.cn/EIP/nonlogin/homePage.htm", Icons.Filled.AccountBalance),
    ServiceEntry("智慧后勤", "水电缴费 · 网上报修",
        "https://clhq.cdut.edu.cn/index.htm", Icons.Filled.Build),
    ServiceEntry("缴费平台", "学杂费线上缴纳",
        "https://wsjf.cdut.edu.cn/payment/", Icons.Filled.Payments),
    ServiceEntry("图书馆", "馆藏检索 · 研修室预约",
        "https://www.lib.cdut.edu.cn/", Icons.Filled.LocalLibrary),
    ServiceEntry("学生邮箱", "edu 邮箱（首次需激活）",
        "http://mail.stu.cdut.edu.cn", Icons.Filled.Email),
)

/**
 * 服务聚合页。
 *
 * 联调结论（2026-10-18）：强智新版菜单页（/jsxsd/ 与 framework/xsMainV.htmlx）的
 * layout.js 在 WebView 下因 tab 栏未渲染而报错白屏；**功能页是独立 HTML，可正常渲染**，
 * 因此全部入口均指向功能页（URL 取自教务菜单 data-url）。
 *
 * 底部提供「退出登录 / 切换账号」：清登录态与全部 Cookie，无需清空应用数据。
 */
@Composable
fun ServicesScreen(
    repo: Repository,
    openWeb: (String, String) -> Unit,
    isRealLogin: Boolean = false,
    /** 打开「刷新登录状态」：由 AppRoot 弹出带预填账密的登录页 */
    onRelogin: () -> Unit = {},
) {
    var showLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text(
            "服务",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Text(
            if (isRealLogin) "已通过统一认证登录，以下入口自动携带登录状态。"
            else "容器内登录为真实账号操作，登录一次后课表/成绩自动可见。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, end = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 直达入口（跳过白屏的菜单页）
            item {
                DirectCard(
                    title = "我的课表",
                    desc = "教务 · 学期理论课表",
                    url = "$JW/xskb/xskb_list.do",
                    icon = Icons.Filled.School,
                    onOpen = openWeb,
                )
            }
            item {
                DirectCard(
                    title = "我的成绩",
                    desc = "教务 · 课程成绩查询",
                    url = "$JW/kscj/cjcx_frm",
                    icon = Icons.Filled.School,
                    onOpen = openWeb,
                )
            }
            items(SERVICES) { s ->
                ServiceCard(s, openWeb)
            }

            // 刷新登录状态 / 切换账号：直接打开登录页并带出已保存的账号密码，
            // 想换账号就在原值上改，不用从空开始敲
            item(span = { GridItemSpan(2) }) {
                OutlinedButton(
                    onClick = {
                        // 先清掉旧会话 Cookie：否则 CAS 会认旧登录态直接跳回去，换不了账号
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        onRelogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新登录状态 / 切换账号")
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(s: ServiceEntry, openWeb: (String, String) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable { openWeb(s.url, s.title) },
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxSize(),
        ) {
            Icon(
                s.icon,
                contentDescription = s.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                s.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DirectCard(
    title: String,
    desc: String,
    url: String,
    icon: ImageVector,
    onOpen: (String, String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable { onOpen(url, title) },
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxSize(),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
