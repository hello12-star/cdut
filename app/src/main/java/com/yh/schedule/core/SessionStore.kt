package com.yh.schedule.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 * 安全说明（MVP）：凭据仅存于应用私有目录的 DataStore；正式版应接入
 * Android Keystore 加密后再落盘（对应原 App 的"静态不可达"项，我们直接做对）。
 */
class SessionStore(private val context: Context) {

    companion object {
        private val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
        private val KEY_ACCOUNT = stringPreferencesKey("account")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_JW_COOKIE = stringPreferencesKey("jw_cookie")
        private val KEY_CAS_TGC = stringPreferencesKey("cas_tgc")
        private val KEY_TIMETABLE_JSON = stringPreferencesKey("timetable_json")

        /** 学期第一周周一（ISO yyyy-MM-dd），由教务页面探测到的周次反推而来 */
        private val KEY_TERM_START = stringPreferencesKey("term_start_monday")

        /** 上次尝试抓课表的时间戳（ms）。0 / 不存在 = 从未抓过 */
        private val KEY_LAST_SCRAPE = longPreferencesKey("last_scrape_at")
    }

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEMO_MODE] ?: true }
    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOGGED_IN] ?: false }

    suspend fun setDemoMode(on: Boolean) {
        context.dataStore.edit { it[KEY_DEMO_MODE] = on }
    }

    suspend fun markLoggedIn(account: String, password: String?, demo: Boolean) {
        context.dataStore.edit {
            it[KEY_LOGGED_IN] = true
            it[KEY_DEMO_MODE] = demo
            if (account.isNotEmpty()) it[KEY_ACCOUNT] = account
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

    suspend fun saveJwCookie(cookie: String) {
        context.dataStore.edit { it[KEY_JW_COOKIE] = cookie }
    }

    /** WebView 统一认证登录成功后提取的 CAS 域 Cookie（含 TGC） */
    suspend fun saveCasCookie(cookie: String) {
        context.dataStore.edit { it[KEY_CAS_TGC] = cookie }
    }

    suspend fun casCookie(): String? =
        context.dataStore.data.first()[KEY_CAS_TGC]

    suspend fun jwCookie(): String? =
        context.dataStore.data.first()[KEY_JW_COOKIE]

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

    suspend fun saveCasTgc(tgc: String) {
        context.dataStore.edit { it[KEY_CAS_TGC] = tgc }
    }
}
