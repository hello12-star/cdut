package com.yh.schedule.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

    private val _ready = MutableStateFlow(false)

    /**
     * 首次接入方式判定是否完成。
     * 界面可以先用缓存内容立即渲染，但任何真正要联网的动作（抓取、静默重登）
     * 都应等这里变为 true，避免用默认的 VPN 通道去打校园网直连。
     */
    val ready: StateFlow<Boolean> = _ready

    suspend fun refresh(context: Context) {
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val connected = caps != null
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val useVpn = when {
                !connected -> true          // 没网时保持 VPN，等有网再判
                !wifi -> true               // 蜂窝流量：按约定走 VPN
                else -> withContext(Dispatchers.IO) {
                    cm == null || net == null || !isCdutNetwork(cm, net)
                }
            }
            apply(useVpn)
        } finally {
            // 判定异常也要放行，否则界面会永远停在启动圈
            _ready.value = true
        }
    }

    /**
     * 校园网判定：只有 DNS 或本机地址落在成理网段才算校内。
     *
     * 之前用「能不能打开 jw.cdut.edu.cn」来判定，这是错的——教务对公网开放，
     * 在校外（甚至别的学校校园网）也能拿到响应，于是永远显示「校园网直连」，
     * 实际却应该走 WebVPN。
     */
    private fun isCdutNetwork(cm: ConnectivityManager, net: Network): Boolean {
        val lp = runCatching { cm.getLinkProperties(net) }.getOrNull() ?: return false
        if (lp.dnsServers.any { isCdutAddress(it.hostAddress) }) return true
        if (lp.linkAddresses.any { isCdutAddress(it.address.hostAddress) }) return true
        return false
    }

    /** 成理公网出口/校内 DNS 网段（教育网 202.115/16、222.195/16） */
    private fun isCdutAddress(host: String?): Boolean {
        val ip = host?.substringBefore('%') ?: return false
        return ip.startsWith("202.115.") || ip.startsWith("222.195.")
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
