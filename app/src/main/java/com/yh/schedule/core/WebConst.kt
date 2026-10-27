package com.yh.schedule.core

/**
 * 全局 WebView 常量。
 *
 * ⚠️ UA 策略（实测结论，2026-10-18）：
 * 成理 CAS 部署了瑞数（RiverSecurity）动态防护，其 Cookie 与 UA 指纹绑定。
 * - 统一使用 PC UA → CAS 移动端登录页白屏；
 * - 登录用移动 UA、抓取用 PC UA → 瑞数判定为两个客户端，接口返回
 *   `{"flag1":2,"msgContent":"..."}`（未登录）。
 * 因此**所有 WebView 一律使用系统默认 UA**（不调用 userAgentString），
 * 保证登录、容器、抓取处于同一指纹下。
 *
 * ⚠️ VPN 策略（2026-10-27 起）：
 * 校外无法直连 `jw.cdut.edu.cn`（教育网 IP，实测 ping 100% 丢包），
 * 改走学校 WebVPN 反向代理 `jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118`。
 * VPN 入口 `vpn.cdut.edu.cn` 是 Sangine/深澜，其登录同样走统一认证
 * （`/public/cas_login?entry_id=2`），与教务共用 TGC，因此
 * **只需登录一次 VPN，后续访问教务代理地址会自动通过 CAS**。
 */
object WebConst {

    /** VPN 反向代理域（教务的唯一可用入口） */
    const val VPN_PROXY_HOST = "jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118"

    /** VPN 的 CAS 登录入口：认证后拿到 VPN 会话，同时持有 TGC 供教务复用 */
    const val VPN_CAS_LOGIN_URL = "https://vpn.cdut.edu.cn/public/cas_login?entry_id=2"

    const val JW_TIMETABLE_URL = "http://$VPN_PROXY_HOST/jsxsd/xskb/xskb_list.do"
    const val JW_GRADE_URL = "http://$VPN_PROXY_HOST/jsxsd/kscj/cjcx_frm"
    const val JW_HOME_URL = "http://$VPN_PROXY_HOST/jsxsd/"
}
