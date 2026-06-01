import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

// Read secrets from local.properties (gitignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val appToken: String = localProps.getProperty("APP_TOKEN", "")

android {
    namespace = "com.vtempe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vtempe"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://api.example.com\"")
        buildConfigField("String", "APP_TOKEN", "\"$appToken\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8081\"")
        }
        release {
            isMinifyEnabled = true   // R8/ProGuard obfuscates the token
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose (AndroidX)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    // DI
    implementation(libs.koin.android)

    // SQLDelight driver (for creating driver in Android app)
    implementation(libs.sqldelight.driver.android)

    // Billing
    implementation(libs.play.billing)
}

