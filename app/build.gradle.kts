plugins {
    id("com.android.application")
}

android {
    namespace = "com.pomogrow.pomosolo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pomogrow.pomosolo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    // WebViewAssetLoader：以 https 虚拟源加载 assets 内 PWA，规避 file:// 限制
    implementation("androidx.webkit:webkit:1.9.0")
}
