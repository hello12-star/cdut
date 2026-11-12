package com.yh.schedule.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.xah.bsdiffs.BsdiffJNI
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
    private const val PATCH_FILE_NAME = "$APK_FILE_NAME.patch"
    private const val NEW_APK_FILE_NAME = "$APK_FILE_NAME.new"

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
        data class Downloading(
            val info: UpdateInfo,
            val progress: Int,
            val incremental: Boolean = false,
        ) : UpdateState
        data class Merging(val info: UpdateInfo) : UpdateState
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
        // 增量包：只有当前安装版本等于 patchBaseVersionCode 时才尝试，失败自动回退全量
        val patchUrl: String = "",
        val patchSha256: String = "",
        val patchSize: Long = 0,
        val patchBaseVersionCode: Long = 0,
        val changelog: String = "",
        val publishedAt: String = "",
    )

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    suspend fun check(context: Context) {
        val current = _state.value
        if (current is UpdateState.Checking ||
            current is UpdateState.Downloading ||
            current is UpdateState.Merging
        ) {
            return
        }
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
            // 只有补丁基线正好是当前安装版本时才尝试增量；失败会自动回退全量
            val canPatch = info.patchUrl.isNotBlank() &&
                info.patchBaseVersionCode == currentVersionCode(app) &&
                info.patchSize > 0 &&
                (info.size <= 0 || info.patchSize < info.size)
            if (canPatch && downloadPatch(app, dir, info)) return@launch
            downloadFull(app, dir, info)
        }
    }

    /** 增量路径：下载补丁 -> 校验补丁 -> 用当前安装包合成 -> 校验目标包 -> 保存为待安装 APK。 */
    private suspend fun downloadPatch(app: Context, dir: File, info: UpdateInfo): Boolean {
        val patchFile = File(dir, PATCH_FILE_NAME)
        val part = File(dir, "$PATCH_FILE_NAME.part")
        val newFile = File(dir, NEW_APK_FILE_NAME)
        patchFile.delete()
        part.delete()
        newFile.delete()
        _state.value = UpdateState.Downloading(info, 0, incremental = true)
        try {
            val outcome = downloadTo(info.patchUrl, part) { progress ->
                _state.value = UpdateState.Downloading(info, progress, incremental = true)
            }
            if (outcome is DownloadOutcome.Error) {
                _state.value = UpdateState.Failed(outcome.message, info)
                return false
            }
            if (!part.renameTo(patchFile)) {
                _state.value = UpdateState.Failed("无法保存增量包", info)
                return false
            }
            if (info.patchSize > 0 && patchFile.length() != info.patchSize) {
                _state.value = UpdateState.Failed("增量包大小不一致，已切换完整下载", info)
                return false
            }
            if (info.patchSha256.isBlank()) {
                _state.value = UpdateState.Failed("增量包缺少 SHA256，已切换完整下载", info)
                return false
            }
            if (!sha256Of(patchFile).equals(normalizeSha(info.patchSha256), ignoreCase = true)) {
                _state.value = UpdateState.Failed("增量包 SHA256 校验失败，已切换完整下载", info)
                return false
            }
            _state.value = UpdateState.Merging(info)
            val result = try {
                withContext(Dispatchers.IO) {
                    BsdiffJNI().merge(app.packageCodePath, patchFile.absolutePath, newFile.absolutePath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = UpdateState.Failed("增量合并不可用：${e.message.orEmpty()}，已切换完整下载", info)
                return false
            }
            if (result != 0) {
                _state.value = UpdateState.Failed("增量合并失败（code=$result），已切换完整下载", info)
                return false
            }
            val error = verify(app, newFile, info)
            if (error != null) {
                _state.value = UpdateState.Failed("$error，已切换完整下载", info)
                return false
            }
            val apkFile = File(dir, APK_FILE_NAME)
            apkFile.delete()
            if (!newFile.renameTo(apkFile)) {
                _state.value = UpdateState.Failed("无法保存合成后的安装包，已切换完整下载", info)
                return false
            }
            _state.value = UpdateState.Downloaded(info, apkFile)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("增量更新失败：${e.message.orEmpty()}，已切换完整下载", info)
            return false
        } finally {
            // 成功时 newFile 已改名，下面删除不会影响最终 APK
            patchFile.delete()
            part.delete()
            newFile.delete()
        }
    }

    /** 全量路径：保持原有逻辑，作为增量不可用或失败时的兜底。 */
    private suspend fun downloadFull(app: Context, dir: File, info: UpdateInfo) {
        val file = File(dir, APK_FILE_NAME)
        val part = File(dir, "$APK_FILE_NAME.part")
        file.delete()
        part.delete()
        _state.value = UpdateState.Downloading(info, 0, incremental = false)
        try {
            val outcome = downloadTo(info.apkUrl, part) { progress ->
                _state.value = UpdateState.Downloading(info, progress, incremental = false)
            }
            if (outcome is DownloadOutcome.Error) {
                _state.value = UpdateState.Failed(outcome.message, info)
                return
            }
            if (!part.renameTo(file)) {
                _state.value = UpdateState.Failed("无法保存安装包", info)
                return
            }
            val error = verify(app, file, info)
            if (error == null) {
                _state.value = UpdateState.Downloaded(info, file)
            } else {
                file.delete()
                _state.value = UpdateState.Failed(error, info)
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

    private sealed interface DownloadOutcome {
        data object Ok : DownloadOutcome
        data class Error(val message: String) : DownloadOutcome
    }

    /** 流式下载到 .part 文件；进度只在百分比变化时回调，避免高频刷新 StateFlow。 */
    private fun downloadTo(
        url: String,
        part: File,
        onProgress: (Int) -> Unit,
    ): DownloadOutcome {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "cdut-helper-updater")
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return DownloadOutcome.Error("下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: return DownloadOutcome.Error("下载失败：空响应")
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
                            onProgress(progress)
                        }
                    }
                }
            }
        }
        return DownloadOutcome.Ok
    }

    private fun verify(context: Context, file: File, info: UpdateInfo): String? {
        if (!file.exists()) return "下载完成但找不到文件"
        if (info.size > 0 && file.length() != info.size) {
            return "安装包大小不一致（${file.length()} / ${info.size}）"
        }
        if (info.sha256.isBlank()) return "update.json 缺少 sha256，已取消安装"
        val actual = runCatching { sha256Of(file) }.getOrNull()
        if (actual != normalizeSha(info.sha256)) return "安装包 SHA256 校验失败"
        return apkIdentityError(context, file)
    }

    private fun normalizeSha(value: String): String =
        value.replace(":", "").trim().lowercase(Locale.US)

    /** 流式计算 SHA256：19MB 的包不再整个读进内存，低内存机型也不会抖一下 */
    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 安装前预校验：包名必须一致、签名必须与当前安装的应用相同。
     * 系统安装器本身也会拦，但报错只有一句「应用未安装」；
     * 这里提前拦下可以给出可读原因，也避免把错包交给系统。
     */
    private fun apkIdentityError(context: Context, file: File): String? {
        val pm = context.packageManager
        val archive = runCatching { archivePackageInfo(pm, file) }.getOrNull()
            ?: return "安装包无法解析，可能已损坏"
        if (archive.packageName != context.packageName) {
            return "安装包包名不一致（${archive.packageName}），已取消安装"
        }
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull() ?: return "读取当前应用签名失败，已取消安装"

        val current = certDigests(installed)
        val next = certDigests(archive)
        if (current.isEmpty() || next.isEmpty()) return "安装包缺少签名信息，已取消安装"
        if (current != next) return "安装包签名与当前应用不一致，已取消安装"
        return null
    }

    private fun archivePackageInfo(pm: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        }

    private fun certDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 用当前签名证书而不是历史证书：历史里可能含旧证书，
            // 直接比较集合会把正常的新包误判成「签名不一致」
            info.signingInfo?.apkContentsSigners ?: return emptySet()
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()

        return signatures.mapNotNull { sig ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
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
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            listOf(
                APK_FILE_NAME,
                "$APK_FILE_NAME.part",
                PATCH_FILE_NAME,
                "$PATCH_FILE_NAME.part",
                NEW_APK_FILE_NAME,
            ).forEach { name -> runCatching { File(dir, name).delete() } }
        }
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
