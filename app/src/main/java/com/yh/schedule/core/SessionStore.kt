package com.yh.schedule.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * 会话/登录态存储。
 *
 * 安全说明：凭据存于应用私有目录的 DataStore，密码已通过 [CredentialCipher]
 * （Android Keystore + AES/GCM）加密后再落盘，不留明文。
 */
class SessionStore(private val context: Context) {

    companion object {
        private val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
        private val KEY_ACCOUNT = stringPreferencesKey("account")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        /**
         * 上一次成功登录的账号。登出 / 取消记住密码时都不删除，
         * 专门用于判断「是否换了账号」，从而决定要不要清掉课表缓存与自定义日程。
         * 不能拿 KEY_ACCOUNT 代替：logout() 和 clearCredentials() 都会把它删掉。
         */
        private val KEY_LAST_ACCOUNT = stringPreferencesKey("last_account")
        private val KEY_JW_COOKIE = stringPreferencesKey("jw_cookie")
        private val KEY_CAS_TGC = stringPreferencesKey("cas_tgc")
        private val KEY_TIMETABLE_JSON = stringPreferencesKey("timetable_json")

        /** 学期第一周周一（ISO yyyy-MM-dd），由教务页面探测到的周次反推而来 */
        private val KEY_TERM_START = stringPreferencesKey("term_start_monday")

        /** 上次尝试抓课表的时间戳（ms）。0 / 不存在 = 从未抓过 */
        private val KEY_LAST_SCRAPE = longPreferencesKey("last_scrape_at")

        // ---------- 偏好设置 ----------

        /** 深色模式：system / light / dark */
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        /** 课表默认展开周末（5/7 天开关的默认值） */
        private val KEY_SHOW_WEEKEND = booleanPreferencesKey("default_show_weekend")

        /** 聚焦页默认展开「已过期」事项 */
        private val KEY_SHOW_OVERDUE = booleanPreferencesKey("default_show_overdue")

        /** 深色模式下是否强制网页内容转为深色 */
        private val KEY_WEBVIEW_DARK = booleanPreferencesKey("webview_dark")

        /** 查询中心入口顺序：逗号分隔的入口 id；空串表示默认顺序 */
        private val KEY_QUERY_ORDER = stringPreferencesKey("query_order")

        /** 课表方块透明度（1f = 不透明） */
        private val KEY_COURSE_ALPHA = floatPreferencesKey("course_block_alpha")

        /** 课表方块文字缩放（1f = 默认） */
        private val KEY_COURSE_TEXT_SCALE = floatPreferencesKey("course_text_scale")

        /** 课表方块文字行距缩放（1f = 默认） */
        private val KEY_COURSE_LINE_SCALE = floatPreferencesKey("course_line_scale")

        /** 时间选择器是否无限循环滚动 */
        private val KEY_INFINITE_WHEEL = booleanPreferencesKey("infinite_wheel")
    }

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEMO_MODE] ?: true }
    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOGGED_IN] ?: false }

    /** 深色模式偏好：system / light / dark */
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    /** 课表是否默认展开周末 */
    val showWeekendDefault: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_WEEKEND] ?: false }

    /** 聚焦页是否默认展开已过期事项 */
    val showOverdueDefault: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_OVERDUE] ?: false }

    /** 深色模式下是否强制网页深色（默认关，避免浅色模式被意外染色） */
    val webViewDark: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_WEBVIEW_DARK] ?: false }

    /** 查询中心入口顺序；空串表示默认顺序 */
    val queryOrder: Flow<String> = context.dataStore.data.map { it[KEY_QUERY_ORDER] ?: "" }

    /** 课表方块透明度（1f = 不透明） */
    val courseAlpha: Flow<Float> =
        context.dataStore.data.map { it[KEY_COURSE_ALPHA] ?: 1f }

    /** 课表方块文字缩放（1f = 默认） */
    val courseTextScale: Flow<Float> =
        context.dataStore.data.map { it[KEY_COURSE_TEXT_SCALE] ?: 1f }

    /** 课表方块文字行距缩放（1f = 默认） */
    val courseLineScale: Flow<Float> =
        context.dataStore.data.map { it[KEY_COURSE_LINE_SCALE] ?: 1f }

    /** 时间选择器是否无限循环滚动 */
    val infiniteWheel: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_INFINITE_WHEEL] ?: true }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setShowWeekendDefault(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_WEEKEND] = on }
    }

    suspend fun setShowOverdueDefault(on: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_OVERDUE] = on }
    }

    suspend fun setWebViewDark(on: Boolean) {
        context.dataStore.edit { it[KEY_WEBVIEW_DARK] = on }
    }

    suspend fun setQueryOrder(order: String) {
        context.dataStore.edit { it[KEY_QUERY_ORDER] = order }
    }

    suspend fun setCourseAlpha(value: Float) {
        context.dataStore.edit { it[KEY_COURSE_ALPHA] = value.coerceIn(0.3f, 1f) }
    }

    suspend fun setCourseTextScale(value: Float) {
        context.dataStore.edit { it[KEY_COURSE_TEXT_SCALE] = value.coerceIn(0.75f, 1.35f) }
    }

    suspend fun setCourseLineScale(value: Float) {
        context.dataStore.edit { it[KEY_COURSE_LINE_SCALE] = value.coerceIn(0.85f, 1.6f) }
    }

    suspend fun setInfiniteWheel(on: Boolean) {
        context.dataStore.edit { it[KEY_INFINITE_WHEEL] = on }
    }

    suspend fun setDemoMode(on: Boolean) {
        context.dataStore.edit { it[KEY_DEMO_MODE] = on }
    }

    suspend fun markLoggedIn(account: String, password: String?, demo: Boolean) {
        context.dataStore.edit {
            it[KEY_LOGGED_IN] = true
            it[KEY_DEMO_MODE] = demo
            if (account.isNotEmpty()) {
                it[KEY_ACCOUNT] = account
                it[KEY_LAST_ACCOUNT] = account
            }
            if (password != null) it[KEY_PASSWORD] = password
        }
    }

    suspend fun logout() {
        context.dataStore.edit {
            it[KEY_LOGGED_IN] = false
            it.remove(KEY_JW_COOKIE)
            it.remove(KEY_CAS_TGC)
            it.remove(KEY_ACCOUNT)
            it.remove(KEY_PASSWORD)
            it.remove(KEY_TIMETABLE_JSON) // 切换账号后旧课表不得残留
            it.remove(KEY_TERM_START)     // 学期起始日也要清：否则换学期后周次全错
        }
    }

    /** 缓存的真实课表（JSON），用于离线展示与首屏秒开 */
    suspend fun saveTimetableJson(json: String) {
        context.dataStore.edit { it[KEY_TIMETABLE_JSON] = json }
    }

    suspend fun timetableJson(): String? =
        context.dataStore.data.first()[KEY_TIMETABLE_JSON]

    /** 当前保存的账号（可能为 null/空）。用于登录时判断是否换了账号 */
    suspend fun account(): String? =
        context.dataStore.data.first()[KEY_ACCOUNT]

    /** 上一次成功登录的账号，登出后仍保留。换账号判断请用这个，不要用 [account] */
    suspend fun lastAccount(): String? =
        context.dataStore.data.first()[KEY_LAST_ACCOUNT]

    /** 清掉缓存的课表。只在确实换了账号时调用；同账号重登必须保留旧课表 */
    suspend fun clearTimetable() {
        context.dataStore.edit { it.remove(KEY_TIMETABLE_JSON) }
    }

    /** 学期第一周周一；没解析出来时返回 null */
    suspend fun termStartMonday(): String? =
        context.dataStore.data.first()[KEY_TERM_START]

    suspend fun saveTermStartMonday(iso: String) {
        context.dataStore.edit { it[KEY_TERM_START] = iso }
    }

    /** 上次尝试抓课表的时间戳；0 表示从未抓过 */
    suspend fun lastScrapeAt(): Long =
        context.dataStore.data.first()[KEY_LAST_SCRAPE] ?: 0L

    suspend fun markScrapeAt(ms: Long) {
        context.dataStore.edit { it[KEY_LAST_SCRAPE] = ms }
    }

    /** WebView 统一认证登录成功后提取的 CAS 域 Cookie（含 TGC） */
    suspend fun saveCasCookie(cookie: String) {
        context.dataStore.edit { it[KEY_CAS_TGC] = cookie }
    }

    /** 明/密文以该前缀区分：老版本存的是明文，读到后顺手升级成密文 */
    private val ENC_PREFIX = "enc:"

    suspend fun credentials(): Pair<String, String>? {
        val p = context.dataStore.data.first()
        val a = p[KEY_ACCOUNT] ?: return null
        val pw = p[KEY_PASSWORD] ?: return null
        if (a.isBlank() || pw.isBlank()) return null

        // 新格式：enc:密文；老格式：明文。解密失败时按明文处理（换机/密钥被清都走这条）
        if (pw.startsWith(ENC_PREFIX)) {
            val plain = CredentialCipher.decrypt(pw.removePrefix(ENC_PREFIX))
            return if (plain != null) a to plain else null
        }
        // 读到老明文 → 静默升级为密文，下次读到的就是加密的
        CredentialCipher.encrypt(pw)?.let { enc ->
            context.dataStore.edit { it[KEY_PASSWORD] = ENC_PREFIX + enc }
        }
        return a to pw
    }

    /** 保存账号密码（对齐原版 saveInput：登录成功后落盘，下次自动填充） */
    suspend fun saveCredentials(account: String, password: String) {
        // 密码走 Keystore 加密；加密失败（极少数定制 ROM）才降级明文，并在值里不留标记以便下次重试
        val stored = CredentialCipher.encrypt(password)?.let { ENC_PREFIX + it } ?: password
        context.dataStore.edit {
            it[KEY_ACCOUNT] = account
            it[KEY_PASSWORD] = stored
        }
    }

    /** 清除已保存的账号密码（用户取消「记住密码」时） */
    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(KEY_ACCOUNT)
            it.remove(KEY_PASSWORD)
        }
    }
}
