package com.xah.bsdiffs

/**
 * 1.0.3 增量库的 JNI 入口。
 *
 * 只保留 native 合并方法，不引入该库的 Kotlin 2.1 元数据与 compileSdk 36 依赖；
 * libbsdiff.so 来自 com.github.Chiu-xaH:Bsdiff-Android-Library:1.0.3。
 * 方法签名必须与 native-lib.c 的 Java_com_xah_bsdiffs_BsdiffJNI_merge 一致。
 */
class BsdiffJNI {
    companion object {
        init {
            System.loadLibrary("bsdiff")
        }
    }

    external fun merge(oldFilePath: String, patchFilePath: String, newFilePath: String): Int
}
