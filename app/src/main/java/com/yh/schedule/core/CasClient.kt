package com.yh.schedule.core

import com.yh.schedule.CdutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * 成都理工大学统一身份认证（CAS）客户端 —— 真实登录模式的骨架实现。
 *
 * 流程（与"聚在工大"的 CasLoginRepository 行为语义一致，契约换成成理）：
 *   1) GET  {cas}/cas/login?service=...            → 取登录页 execution（CAS 3.x 表单）
 *   2) POST {cas}/cas/login                        → 账号密码 + execution + _eventId=submit
 *   3) 成功后由容器 CAS 发 TGC cookie；后续对子系统逐个 SSO 取票。
 *
 * 待联调校准项（README 同步列出）：
 *   - cas.paas.cdut.edu.cn 的 CAS 版本（Apereo CAS 6 表单字段可能无 lt）
 *   - service 参数各系统的注册名（需信息中心确认，避免触发风控）
 */
class CasClient(private val app: CdutApp) {

    companion object {
        const val CAS_BASE = "https://cas.paas.cdut.edu.cn"
        const val CAS_LOGIN = "$CAS_BASE/cas/login"
        /** MVP 仅演示门户 SSO；其余子系统 service 参数待确认后在此登记 */
        const val SERVICE_BSDT = "https://bsdt.cdut.edu.cn/"
    }

    /** 内存会话 cookie（含 TGC） */
    private val cookieJar = object : okhttp3.CookieJar {
        private val store = linkedMapOf<String, MutableList<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store[url.host] = cookies.toMutableList()
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
            store[url.host] ?: emptyList()

        fun hasTgc(): Boolean = store.any { (_, list) ->
            list.any { it.name.equals("TGC", ignoreCase = true) }
        }
    }

    private val client = app.http.newBuilder().cookieJar(cookieJar).build()

    /**
     * 账号密码登录。成功返回 Result.success(TGC 是否取得)；
     * 失败返回 Result.failure(含页面错误摘要)。
     */
    suspend fun login(username: String, password: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 1) 登录页，抓 execution
                val pageReq = Request.Builder()
                    .url("$CAS_LOGIN?service=$SERVICE_BSDT")
                    .header("User-Agent", PC_UA)
                    .build()
                client.newCall(pageReq).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    val doc = Jsoup.parse(html)
                    val execution = doc.selectFirst("input[name=execution]")?.attr("value")
                        ?: error("登录页解析失败：未找到 execution 字段（CAS 版本待校准）")
                    val lt = doc.selectFirst("input[name=lt]")?.attr("value")

                    // 2) 提交凭据
                    val form = FormBody.Builder()
                        .add("username", username)
                        .add("password", password)
                        .add("_eventId", "submit")
                        .apply {
                            execution.let { add("execution", it) }
                            lt?.let { add("lt", it) }
                        }.build()
                    val postReq = Request.Builder()
                        .url("$CAS_LOGIN?service=$SERVICE_BSDT")
                        .header("User-Agent", PC_UA)
                        .post(form)
                        .build()
                    client.newCall(postReq).execute().use { post ->
                        val body = post.body?.string().orEmpty()
                        val failed = body.contains("凭据未通过认证") ||
                                body.contains("Invalid credentials") ||
                                body.contains("认证失败")
                        if (failed) error("账号或密码错误（页面返回认证失败）")
                        cookieJar.hasTgc()
                    }
                }
            }
        }

    private val PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}
