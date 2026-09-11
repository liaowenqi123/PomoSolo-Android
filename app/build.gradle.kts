plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pomogrow.pomosolo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pomogrow.pomosolo"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"

        // WebRTC 只保留手机常用的 arm64-v8a 与 x86_64（模拟器/MuMu 自测用）；
        // 去掉 x86 与 armeabi-v7a（32 位老设备）——2026 年可忽略。
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ===== V1 原生层（Kotlin + Compose）=====
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 架构：ViewModel + Compose 集成
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 播放：Media3 ExoPlayer（现代、支持本地文件/在线流）
    implementation("androidx.media3:media3-exoplayer:1.3.1")

    // 网络与下载：OkHttp（流式 + 进度）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // P2P 传歌（m5.1）：WebRTC native 库（libwebrtc 预编译 + Java 绑定，BSD，AAR 41.7MB）
    // 桌面端/PWA 是"白嫖" WebView 内置的 WebRTC，原生 Android 无浏览器内核，只能自带。
    // 实测 APK 增量：+17.0MB（仅 arm64-v8a + x86_64）；详见 README「P2P 传歌与 m5.1」。
    implementation("io.github.webrtc-sdk:android:125.6422.07")
}
