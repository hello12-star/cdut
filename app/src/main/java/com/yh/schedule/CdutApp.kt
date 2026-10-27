package com.yh.schedule

import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局 Application：持有共享 OkHttpClient（带 CookieJar 的会话由各客户端自管）。
 */
class CdutApp : Application() {

    lateinit var http: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()

        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
