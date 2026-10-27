package com.yh.schedule.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭据加密（Android Keystore + AES/GCM）。
 *
 * ## 为什么要做
 *
 * DataStore / SharedPreferences 落在应用私有目录，但 **root、adb backup、
 * 部分厂商云备份都能直接读到明文**。账号密码属于"被拖库即永久失效"级别的数据，
 * 不能只依赖目录权限。
 *
 * ## 方案
 *
 * - 密钥由 **AndroidKeyStore** 生成，`SecretKey` 不可导出（连 App 自己都读不到原始字节），
 *   即使把 DataStore 文件拷走也无法解密；
 * - 算法 `AES/GCM/NoPadding`，自带完整性校验（密文被篡改会解密失败而不是返回垃圾）；
 * - 存储格式：`Base64(IV(12 字节) || 密文 || tag)`，每次加密 IV 随机，
 *   **相同明文每次密文都不同**，不会泄露"两个账号密码是否相同"。
 *
 * ## 边界
 *
 * - 解密失败（换机恢复、Keystore 被清、旧版明文数据）→ 返回 null，由调用方决定回退策略；
 * - Keystore 不可用（极少数定制 ROM）→ 加密抛异常，调用方需 catch 后降级。
 *
 * 注：本类的写法参考 Android 官方 Keystore 示例，未引入已废弃的
 * `androidx.security:security-crypto`（该库 2024 年已停止维护）。
 */
object CredentialCipher {

    private const val KEY_ALIAS = "cdut_cred_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 不要求设备解锁才能用：自动填充需要在后台静默解密
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密；Keystore 不可用时返回 null（调用方按降级处理） */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }.getOrNull()

    /** 解密；失败（旧版明文 / 换机 / 密钥被清）返回 null */
    fun decrypt(stored: String): String? = runCatching {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return null
        val iv = raw.copyOfRange(0, IV_BYTES)
        val body = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    /** 是否可安全往返（用于决定是否能启用加密存储） */
    fun selfTest(): Boolean {
        val probe = "probe-${System.nanoTime()}"
        val enc = encrypt(probe) ?: return false
        return decrypt(enc) == probe
    }
}
