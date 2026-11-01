package com.yh.schedule.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 运行期接入方式判定。
 *
 * 用户约定：
 * - 校内校园网（WiFi）→ 直连 `jw.cdut.edu.cn`；
 * - 校内但用蜂窝流量 → 走 WebVPN；
 * - 校外（蜂窝或别人的 WiFi）→ 走 WebVPN。
 *
 * WiFi 无法直接区分「校园网」和「校外 WiFi」，所以用一次很短的探测：
 * 只要能拿到 `jw.cdut.edu.cn` 的任意 HTTP 响应（302/412 也算），
 * 就说明当前网络已经能进教务，按校园网直连处理；否则回落到 VPN。
 *
 * 冷启动时由 AppRoot 等待判定完成后再渲染界面；运行中网络切换由
 * ConnectivityManager 回调触发刷新，[version] 随之递增，供界面做一次静默重登。
 */
object NetworkAccess {

    private const val PROBE_URL = "https://jw.cdut.edu.cn/jsxsd/"
    private const val PROBE_TIMEOUT_MS = 2500

    private val _version = MutableStateFlow(0)

    /** 接入方式每变化一次 +1（初始判定不算变化） */
    val version: StateFlow<Int> = _version

    suspend fun refresh(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = caps != null
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val useVpn = when {
            !connected -> true          // 没网时保持 VPN，等有网再判
            !wifi -> true               // 蜂窝流量：按约定走 VPN
            else -> withContext(Dispatchers.IO) { !probeDirect() }
        }
        apply(useVpn)
    }

    private fun apply(useVpn: Boolean) {
        if (WebConst.USE_VPN == useVpn) return
        WebConst.setVpnEnabled(useVpn)
        _version.value += 1
    }

    /** 直连可达性探测：任意 HTTP 响应都算可达；连接失败/超时算不可达 */
    private fun probeDirect(): Boolean = runCatching {
        val conn = URL(PROBE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = PROBE_TIMEOUT_MS
        conn.readTimeout = PROBE_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            code in 200..599
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
