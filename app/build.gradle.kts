plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.mtcore"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.mtcore"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "5.0"
    }
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("app/release.keystore").takeIf { it.exists() }
                ?: rootProject.file("release.keystore").takeIf { it.exists() }
            if (keystoreFile != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD")
                    ?: (project.findProperty("STORE_PASSWORD") as? String)
                    ?: ""
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: (project.findProperty("KEY_ALIAS") as? String)
                    ?: ""
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: (project.findProperty("KEY_PASSWORD") as? String)
                    ?: ""
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val keystoreFile = rootProject.file("app/release.keystore").takeIf { it.exists() }
                ?: rootProject.file("release.keystore").takeIf { it.exists() }
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Keep default debug build
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
