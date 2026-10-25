import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.java.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.java.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 36
        versionName = "2.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("local") {
            // 仅用于本地产物交付（可直接安装的 APK）；凭据不存在时不影响其他构建。
            // 优先级：环境变量 EYECARE_KEYSTORE > user.home > /root/.android/debug.keystore
            val candidates = listOfNotNull(
                System.getenv("EYECARE_KEYSTORE")?.let { File(it) },
                System.getProperty("user.home")?.let { File(it, ".android/debug.keystore") },
                File("/root/.android/debug.keystore"),
            )
            val ks = candidates.firstOrNull { it != null && it.exists() }
            if (ks != null) {
                storeFile = ks
                storePassword = System.getenv("EYECARE_STORE_PASS") ?: "android"
                keyAlias = System.getenv("EYECARE_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("EYECARE_KEY_PASS") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val local = signingConfigs.findByName("local")
            if (local != null && local.storeFile?.exists() == true) {
                signingConfig = local
            }
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// aarch64 主机的 AAPT2 在这里**不做**依赖替换。
//
// 曾经这里有一条 useTarget("...:aapt2:$version:linux-aarch64")，但 AGP 9 的
// aapt2 根本没有 linux-aarch64 分类器（只有无分类器 / -linux / -osx / -windows，
// 实测前者 404），所以那条规则从未生效 —— 它只是让构建看起来"已经处理过"，
// 真正失败时抛出的是 "AAPT2 Daemon startup failed"，完全不指向原因。
//
// 正确做法是让 AGP 用一个外部可执行的 aapt2（见 README「aarch64 主机的 AAPT2」），
// 由用户级 ~/.gradle/gradle.properties 注入：
//   android.aapt2FromMavenOverride=/path/to/bin/aapt2
// 仓库不硬编码任何机器相关路径，x86-64 / 本机自带 aapt2 的环境不受影响。

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
