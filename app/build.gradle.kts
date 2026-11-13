plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.musicpartner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.musicpartner"
        // Android 10 (API 29) is the first version with AudioPlaybackCapture.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "edition"
    productFlavors {
        // Full: shows song title/app via notification access. Install over USB (Play Protect
        // blocks sideloading apps that request notification access).
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "FULL_EDITION", "true")
            resValue("string", "app_name", "MusicPartner")
        }
        // Lite: no notification access -> can be shared as an APK (WhatsApp, Drive...).
        // Controls still work via media-button events; song title isn't shown.
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "FULL_EDITION", "false")
            resValue("string", "app_name", "In Sync With You")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
