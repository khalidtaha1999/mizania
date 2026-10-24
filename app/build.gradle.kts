plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mizania.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mizania.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "5.6"
    }

    // Release signing key: from the CI secret (MIZANIA_KEYSTORE / MIZANIA_KEY_PASSWORD) when set,
    // otherwise from a local app/mizania.keystore if present. With neither, builds use the debug key.
    // Keep the same key across builds so a new APK installs over the old one and keeps the data.
    val ksPath = System.getenv("MIZANIA_KEYSTORE")?.takeIf { it.isNotBlank() } ?: "mizania.keystore"
    val ksFile = file(ksPath)
    val ksPass = System.getenv("MIZANIA_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "mizania123"
    signingConfigs {
        create("personal") {
            storeFile = ksFile
            storePassword = ksPass
            keyAlias = "mizania"
            keyPassword = ksPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile.exists()) signingConfigs.getByName("personal") else signingConfigs.getByName("debug")
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
    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
