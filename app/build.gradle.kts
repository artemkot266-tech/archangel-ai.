plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.archangel.ai"
    // Google Play с 31.08.2026 принимает только приложения для Android 16 (API 36)
    compileSdk = 36

    defaultConfig {
        applicationId = "com.archangel.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "0.21.0"
        // Офлайн-слух (Vosk) — нативная библиотека; только архитектуры телефонов, чтобы APK был меньше
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Постоянный debug-ключ в репозитории: каждая новая сборка ставится
    // поверх старой без удаления (иначе Android ругается на подпись).
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Ключ загрузки для Google Play — только из секретов CI, в репозитории его нет
        val uploadStore = System.getenv("UPLOAD_KEYSTORE_PATH")
        if (!uploadStore.isNullOrBlank() && file(uploadStore).exists()) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    // Две версии: full — APK для сайта, GitHub и других магазинов (всё включено);
    // play — для Google Play: без торговли на реальные деньги и без особых разрешений.
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
            // Тот же ключ, что у прежних версий, — обновление ставится поверх
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "true")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // Релизная сборка: не отладочная (сканеры безопасности не придираются) и заметно быстрее.
        // Подпись та же, что у прежних версий, — обновление ставится поверх.
        release {
            isMinifyEnabled = false
            isDebuggable = false
            // Подпись задаётся в версиях (flavors): full — прежний ключ, play — ключ загрузки Google Play
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Офлайн-слух: ждёт имя помощника без системного распознавателя (версии с поддержкой 16 КБ страниц)
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
