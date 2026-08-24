plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.hisab"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.hisab"
        minSdk = 28
        targetSdk = 36
        versionCode = 320
        versionName = "3.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema JSONs are exported so the v7->v8 migration can be validated
        // mechanically (against Room's own generated schema) by a plain JVM unit test.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    sourceSets {
        getByName("test") {
            // Exposes the exported schema JSONs to unit tests as a classpath resource.
            resources.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            // Standard self-signed keystore for Play Protect trust compatibility
            val keyStoreFile = rootProject.file("release.keystore")
            if (keyStoreFile.exists()) {
                storeFile = keyStoreFile
                storePassword = "hisabapppassword"
                keyAlias = "hisab"
                keyPassword = "hisabapppassword"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val keyStoreFile = rootProject.file("release.keystore")
            if (keyStoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    installation {
        installOptions.addAll(listOf("-r", "-d", "-t"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
        }
    }
}

tasks.register<Exec>("cleanEmulatorTemp") {
    group = "install"
    description = "Cleans stale APK temporary files from connected Android emulator to prevent INSTALL_FAILED_INSUFFICIENT_STORAGE."
    val adbPath = "${System.getenv("LOCALAPPDATA")}\\Android\\Sdk\\platform-tools\\adb.exe"
    commandLine(adbPath, "shell", "rm -rf /data/local/tmp/* ; pm trim-caches 2000000000")
    isIgnoreExitValue = true
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Export & Backup
    implementation(libs.poi.ooxml)
    implementation(libs.gson)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Vico Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Frosted-glass backdrop blur for navigation dock
    implementation(libs.haze)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}