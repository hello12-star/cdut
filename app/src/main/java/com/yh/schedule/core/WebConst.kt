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
 * 校外无法直连 `jw.cdut.edu.cn`（教育网 IP，实测 ping 100% 丢包），需走校园 WebVPN。
 * 但 VPN 链路要求「登录 VPN → 进智慧校园门户 → 点教务管理系统 → SSO 跳转」，
 * 该流程**尚未打通**（仍报「未取到会话凭据」，原因未定位）。
 *
 * 因此 [USE_VPN] 默认 false 走校园网内直连，保证基本功能可用；
 * VPN 的常量、门户点击逻辑、URL 轨迹诊断全部保留，链路验证通过后
 * **只需把 USE_VPN 改成 true** 即可整体切换。
 */
object WebConst {

    /** 是否走校园 WebVPN。切换整条链路的唯一开关。 */
    const val USE_VPN = false

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
}
