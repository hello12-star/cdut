package com.yh.schedule.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 应用内更新：Gitee 为主，GitHub / jsDelivr 为备。
 *
 * 只拉一个很小的 update.json；APK 地址由 JSON 给出。下载走系统 DownloadManager，
 * 校验 SHA256 后再交给系统安装器。签名一致性由 Android 安装器强制检查，
 * 因此后续所有版本必须用同一个签名（当前是固定机器的 debug keystore）。
 */
object UpdateManager {
    private const val APK_FILE_NAME = "cdut-helper-debug.apk"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val info: UpdateInfo) : UpdateState
        data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState
        data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState
        data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
    }

    @Serializable
    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String = "",
        val size: Long = 0,
        val changelog: String = "",
        val publishedAt: String = "",
    )

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    suspend fun check(context: Context) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val info = withContext(Dispatchers.IO) { fetchLatest() }
        if (info == null) {
            _state.value = UpdateState.Failed("检查更新失败，请检查网络后重试")
            return
        }
        val currentCode = currentVersionCode(context)
        _state.value = if (info.versionCode > currentCode) {
            UpdateState.Available(info)
        } else {
            UpdateState.UpToDate
        }
    }

    fun download(context: Context, info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val app = context.applicationContext
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir == null) {
                _state.value = UpdateState.Failed("无法访问下载目录", info)
                return@launch
            }
            val file = File(dir, APK_FILE_NAME)
            val part = File(dir, "$APK_FILE_NAME.part")
            file.delete()
            part.delete()
            _state.value = UpdateState.Downloading(info, 0)
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "cdut-helper-updater")
                    .build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = UpdateState.Failed("下载失败：HTTP ${response.code}", info)
                        return@use
                    }
                    val body = response.body
                    if (body == null) {
                        _state.value = UpdateState.Failed("下载失败：空响应", info)
                        return@use
                    }
                    val total = body.contentLength()
                    var written = 0L
                    var lastProgress = -1
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                val progress = if (total > 0) {
                                    (written * 100 / total).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    _state.value = UpdateState.Downloading(info, progress)
                                }
                            }
                        }
                    }
                    if (!part.renameTo(file)) {
                        _state.value = UpdateState.Failed("无法保存安装包", info)
                        return@use
                    }
                    val error = verify(file, info)
                    if (error == null) {
                        _state.value = UpdateState.Downloaded(info, file)
                    } else {
                        file.delete()
                        _state.value = UpdateState.Failed(error, info)
                    }
                }
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                part.delete()
                _state.value = UpdateState.Failed("下载失败：${e.message.orEmpty()}", info)
            } finally {
                if (!file.exists()) part.delete()
            }
        }
    }

    private fun verify(file: File, info: UpdateInfo): String? {
        if (!file.exists()) return "下载完成但找不到文件"
        if (info.size > 0 && file.length() != info.size) {
            return "安装包大小不一致（${file.length()} / ${info.size}）"
        }
        if (info.sha256.isBlank()) return "update.json 缺少 sha256，已取消安装"
        val expected = info.sha256.replace(":", "").trim().lowercase(Locale.US)
        val actual = runCatching {
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                "%02x".format(it)
            }
        }.getOrNull()
        if (actual != expected) return "安装包 SHA256 校验失败"
        return null
    }

    fun install(context: Context, info: UpdateInfo) {
        val file = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, APK_FILE_NAME) }
        if (file == null || !file.exists()) {
            _state.value = UpdateState.Failed("安装包不存在，请重新下载", info)
            return
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (!opened) {
                // 少数 ROM 没有「安装未知应用」列表页，退到应用详情页让用户手动找权限
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            Toast.makeText(context, "请允许安装未知应用后返回，再点安装", Toast.LENGTH_LONG).show()
            return
        }

        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure {
            _state.value = UpdateState.Failed("打开安装器失败：${it.message.orEmpty()}", info)
        }
    }

    fun deleteDownloaded(context: Context) {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { runCatching { File(it, APK_FILE_NAME).delete() } }
        _state.value = UpdateState.Idle
    }

    private fun fetchLatest(): UpdateInfo? {
        for (url in UpdateConfig.manifestUrls) {
            val info = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "cdut-helper-updater")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) null else json.decodeFromString<UpdateInfo>(body)
                }
            }.getOrNull()
            if (info != null && info.apkUrl.isNotBlank()) return info
        }
        return null
    }

    private fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        return if (bytes < 1024 * 1024) {
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}

/**
 * 更新清单地址。
 *
 * 优先 Gitee（国内更稳），失败后自动回落 GitHub raw 和 jsDelivr。
 * Gitee 用户名/仓库如果不是 hellogitee-star/cdut，只需要改 GITEE_REPO。
 */
object UpdateConfig {
    private const val GITEE_REPO = "hellogitee-star/cdut"
    private const val GITEE_BRANCH = "main"
    private const val GITHUB_REPO = "hello12-star/cdut"
    private const val GITHUB_BRANCH = "main"

    val manifestUrls: List<String> = listOf(
        "https://gitee.com/$GITEE_REPO/raw/$GITEE_BRANCH/dist/update.json",
        "https://raw.githubusercontent.com/$GITHUB_REPO/$GITHUB_BRANCH/dist/update.json",
        "https://cdn.jsdelivr.net/gh/$GITHUB_REPO@$GITHUB_BRANCH/dist/update.json",
    )
}
