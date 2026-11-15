import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.java.myapplication"
    // v2.10.0（ui-miuix）：miuix 0.9.4 的传递依赖（compose 1.12.x、material3 1.5.0-alpha）
    // 要求 compileSdk >= 37，否则 AGP 在 checkDebugAarMetadata 阶段直接失败。
    // 只提 compileSdk，不动 targetSdk —— 后者会改变运行时行为，是另一件事。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.java.myapplication"
        minSdk = 24
        targetSdk = 35
        // v2.9.0：跨过一个只在代码注释里存在的小版本序列。
        // v2.7.2–v2.8.9 的改动从未单独出包，它们累积在这次发布里；
        // 版本号跳过 2.8.x 是因为那段序列已被内部注释占用，回填会造成混淆。
        versionCode = 37
        versionName = "2.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("local") {
            // 仅用于本地产物交付（可直接安装的 APK）；凭据不存在时不影响其他构建。
            //
            // 优先级：环境变量 EYECARE_KEYSTORE > ~/.android/debug.keystore。
            // 只按 user.home 推导，不写死任何绝对路径：user.home 本身已经覆盖了
            // 常见位置，再追加一条固定路径既多余，也等于把开发机的目录结构写进仓库。
            val home = System.getProperty("user.home")
            val candidates = listOfNotNull(
                System.getenv("EYECARE_KEYSTORE")?.let { File(it) },
                home?.let { File(it, ".android/debug.keystore") },
            )
            val ks = candidates.firstOrNull { it.exists() }
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

    // MIUI 风格组件库（v2.10.0 起，见 ui-miuix 分支）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
