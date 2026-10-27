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
 */
object WebConst {
    const val JW_TIMETABLE_URL = "https://jw.cdut.edu.cn/jsxsd/xskb/xskb_list.do"
    const val JW_GRADE_URL = "https://jw.cdut.edu.cn/jsxsd/kscj/cjcx_frm"
    const val JW_HOME_URL = "https://jw.cdut.edu.cn/jsxsd/"
}

