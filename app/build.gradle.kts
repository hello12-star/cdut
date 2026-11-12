import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.yh.schedule"
    compileSdk = 35

    // 发布签名：keystore.properties 不进仓库；缺失时回落到 debug 签名，保证别人 clone 下来也能构建
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.yh.schedule"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.2.2"
    }

    buildTypes {
        release {
            // 参考 HFUT-Schedule：release 开 R8 + 资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // ABI 拆分：按架构产出三份 APK + 一个 universal 兜底（直装用 universal）
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/**
 * 分发用的 APK 文件名：用户直接把 APK 发给同学，文件名也应是「掌上成理」。
 * 保留 app-debug.apk 作为构建产物，另复制一份可分发文件到 app/build/dist/。
 */
tasks.register<Copy>("copyDebugApk") {
    // ABI 拆分后没有单一的 app-debug.apk，分发取 universal 那份
    from(layout.buildDirectory.file("outputs/apk/debug/app-universal-debug.apk"))
    into(layout.buildDirectory.dir("dist"))
    rename { "掌上成理-debug.apk" }
}

tasks.configureEach {
    if (name == "assembleDebug") finalizedBy("copyDebugApk")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jsoup:jsoup:1.18.1")

    // 本地存储
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // 查询中心入口拖拽排序
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // 桌面小组件（与参考源码 HFUT-Schedule 的 FocusWidgetReceiver 同方案）
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
