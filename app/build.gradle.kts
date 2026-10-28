import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.linkgrab.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.linkgrab.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 354
        versionName = "3.2.3"
    }

    // 永久签名：密钥在 app/linkgrab-sign.jks（已 gitignore），密码在 local.properties
    // （已 gitignore）。debug.keystore 会被工具链自动重建导致签名漂移，禁用之；
    // 本地属性缺失时兜底回 debug 签名，保证别人 clone 也能构建。
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val keystoreFile = file(localProps.getProperty("LINKGRAB_KEYSTORE", ""))
    signingConfigs {
        create("linkgrab") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = localProps.getProperty("LINKGRAB_STORE_PASS")
                keyAlias = "linkgrab"
                keyPassword = localProps.getProperty("LINKGRAB_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            // release + 固定签名 = R8 混淆 + 可直接安装。
            // debug 包（93MB 未混淆）仅保留作开发用。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("linkgrab")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")

    // Cronet = Chromium 网络栈（TLS 指纹与真实 Chrome 一致）。
    // 小红书 Shield 会按 TLS 指纹拦截普通 HTTP 客户端，高星项目
    // JoeanAmier/XHS-Downloader 用 curl_cffi 的 impersonate="chrome*" 绕过，
    // Android 上的等价方案就是 Cronet。
    implementation("org.chromium.net:cronet-embedded:500.0.1")

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Network
    implementation(libs.okhttp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Media3 (ExoPlayer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Shimmer - Loading Animation
    implementation(libs.compose.shimmer)

    // FFmpeg Kit - Media processing (min variant for smaller size)
    implementation(libs.ffmpeg.kit.min)

    // ZXing core：从相册图片里识别二维码（纯 Java，约 0.5MB，不需要相机权限）
    implementation("com.google.zxing:core:3.5.3")
}
