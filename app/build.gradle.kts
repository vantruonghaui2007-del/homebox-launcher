import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chữ ký phát hành: đọc keystore.properties (không đưa lên GitHub) hoặc biến môi trường,
// mặc định dùng khoá cố định keystore/homebox.jks để bản mới cài đè được bản cũ.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "vn.homekara.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.homekara.launcher"
        minSdk = 19          // Android 4.4.2 trên VNPT SmartBox 2
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        // Icon vector (res/drawable/ic_*.xml) được AGP tự đổi sang PNG cho Android 4.4.
    }

    signingConfigs {
        create("homebox") {
            storeFile = rootProject.file(signingValue("storeFile", "HOMEBOX_KEYSTORE") ?: "keystore/homebox.jks")
            storePassword = signingValue("storePassword", "HOMEBOX_KEYSTORE_PASSWORD") ?: "homebox123"
            keyAlias = signingValue("keyAlias", "HOMEBOX_KEY_ALIAS") ?: "homebox"
            keyPassword = signingValue("keyPassword", "HOMEBOX_KEY_PASSWORD") ?: "homebox123"
            enableV1Signing = true   // BẮT BUỘC cho Android 4.4
            enableV2Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("homebox")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("homebox")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures { buildConfig = true }

    packaging {
        resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }

    lint {
        // Chỉ chặn build khi dùng API mới hơn Android 4.4 mà không kiểm tra phiên bản.
        checkOnly += setOf("NewApi", "InlinedApi", "UnusedAttribute")
        error += setOf("NewApi")
        abortOnError = true
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
