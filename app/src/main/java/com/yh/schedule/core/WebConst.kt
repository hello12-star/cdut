package com.yh.schedule.core

/**
 * 全局 WebView 常量与链路开关。
 *
 * ## UA 策略（实测结论，2026-10-18）
 * 成理 CAS 部署了瑞数（RiverSecurity）动态防护，其 Cookie 与 UA 指纹绑定。
 * - 统一使用 PC UA → CAS 移动端登录页白屏；
 * - 登录用移动 UA、抓取用 PC UA → 瑞数判定为两个客户端，接口返回
 *   `{"flag1":2,"msgContent":"..."}`（未登录）。
 * 因此**所有 WebView 一律使用系统默认 UA**（不调用 userAgentString），
 * 保证登录、容器、抓取处于同一指纹下。
 *
 * ## 直连 / VPN 双通道
 * 校外无法直连 `jw.cdut.edu.cn`（教育网 IP），需走校园 WebVPN；校内校园网则可直连。
 * 运行期由 [NetworkAccess] 自动判定：WiFi 下先探测直连是否可达，
 * 可达走直连，不可达（校外 WiFi）改走 VPN；蜂窝流量一律走 VPN。
 *
 * VPN 链路（2026-11-01 已打通）：
 * `vpn.cdut.edu.cn/public/cas_login` → CAS 代理域 → VPN 门户 → 「教务管理系统」
 * → 代理域 `jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118` → SSO → 课表页。
 */
object WebConst {

    /**
     * 是否走校园 WebVPN。运行期由 [NetworkAccess] 根据网络自动维护：
     * 校园网 WiFi 直连，蜂窝流量 / 校外 WiFi 走 VPN。
     *
     * 保留 `USE_VPN` 这个名字，现有调用点（登录、抓取、容器、选项页）无需改动。
     */
    @Volatile
    private var vpnEnabled: Boolean = true

    val USE_VPN: Boolean get() = vpnEnabled

    internal fun setVpnEnabled(on: Boolean) {
        vpnEnabled = on
    }

    // ---------- VPN 侧 ----------

    /** VPN 反向代理域（教务入口） */
    const val VPN_PROXY_HOST = "jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118"

    /**
     * 智慧校园门户（办事大厅）。
     * VPN 登录后落在这里；教务必须从它的「推荐系统 → 教务管理系统」走 SSO 进去，
     * 直连 jw 代理域会因缺少 SSO 票据报「未取到会话凭据」。
     */
    const val PORTAL_URL = "http://bsdt-cdut-edu-cn-s.vpn.cdut.edu.cn:8118/EIP/nonlogin/homePage.htm"

    /** VPN 的 CAS 登录入口：认证后拿到 VPN 会话，同时持有 TGC 供教务复用 */
    const val VPN_CAS_LOGIN_URL = "https://vpn.cdut.edu.cn/public/cas_login?entry_id=2"

    // ---------- 直连侧 ----------

    /** 教务自己的 CAS 登录入口 */
    const val JW_CAS_LOGIN_URL =
        "https://cas.paas.cdut.edu.cn/cas/login?service=http%3A%2F%2Fjw.cdut.edu.cn%2Fsso%2Flogin.jsp" +
                "%3FtargetUrl%3Dbase64aHR0cDovL2p3LmNkdXQuZWR1LmNuL0xvZ29uLmRvP21ldGhvZD1sb2dvblNTT2NkbGdkeA%3D%3D"

    // ---------- 按开关派生的地址 ----------

    /** 当前生效的 CAS 登录入口 */
    val CAS_LOGIN_URL: String
        get() = if (USE_VPN) VPN_CAS_LOGIN_URL else JW_CAS_LOGIN_URL

    private const val JW_DIRECT_HOST = "https://jw.cdut.edu.cn"
    private const val VPN_HTTP_HOST = "http://jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118"

    private val host: String
        get() = if (USE_VPN) VPN_HTTP_HOST else JW_DIRECT_HOST

    val JW_TIMETABLE_URL: String get() = "$host/jsxsd/xskb/xskb_list.do"
    val JW_HOME_URL: String get() = "$host/jsxsd/"

    /** 抓取起始页：VPN 模式先到门户，直连模式直接进教务 */
    val SCRAPE_ENTRY_URL: String get() = if (USE_VPN) PORTAL_URL else JW_HOME_URL

    /**
     * 把直连地址改写成 VPN 反向代理地址；已经是代理域则原样返回。
     *
     * 门户的「教务管理系统」卡片 onClick 里写的是 `openExpress('https://jw.cdut.edu.cn/sso/login.jsp',...)`
     * 这种**直连**地址。在 VPN 会话下直接打开它会落到 `cas.paas.cdut.edu.cn` 的登录页，
     * 因为 VPN 登录拿到的 TGC 只存在于代理域 `cas-paas-...:8118` 上，直连域没有。
     * 改写成 jw 代理域后，由代理转发给教务，教务再跳代理 CAS，才能凭 TGC 免登录进课表。
     */
    fun proxify(url: String): String {
        if (!USE_VPN) return url
        if (url.contains("-s.vpn.cdut.edu.cn")) return url
        // WebVPN 的反代端口 8118 只接 http；cas 域例外，代理侧仍是 https
        fun httpOf(u: String): String =
            if (u.startsWith("https://")) "http://" + u.removePrefix("https://") else u
        return when {
            url.contains("jw.cdut.edu.cn") ->
                httpOf(url).replace("jw.cdut.edu.cn", "jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118")
            url.contains("cas.paas.cdut.edu.cn") ->
                url.replace("cas.paas.cdut.edu.cn", "cas-paas-cdut-edu-cn-s.vpn.cdut.edu.cn:8118")
            url.contains("bsdt.cdut.edu.cn") ->
                httpOf(url).replace("bsdt.cdut.edu.cn", "bsdt-cdut-edu-cn-s.vpn.cdut.edu.cn:8118")
            else -> url
        }
    }
}
