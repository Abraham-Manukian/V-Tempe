import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

// Firebase Crashlytics/Analytics only activate once google-services.json is present
// (get it from https://console.firebase.google.com — register an Android app with
// applicationId "com.vtempe" and download the file into this module's root).
// Without it the build stays green for anyone who hasn't set up Firebase yet.
val googleServicesJsonPresent = file("google-services.json").exists()
if (googleServicesJsonPresent) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Read secrets from local.properties (gitignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val appToken: String = localProps.getProperty("APP_TOKEN", "")

// The Google OAuth "web" client id from google-services.json (client_type 3 under the app's
// oauth_client entries) — needed by Credential Manager's GetGoogleIdOption to request a Google
// ID token. Not itself secret (OAuth client ids are public identifiers), but it's per-project
// and google-services.json is gitignored, so it's read from local.properties like the other
// per-developer values here rather than hardcoded.
//
// It used to be read from local.properties only, which failed badly: a missing entry baked an
// empty string into BuildConfig, the build stayed green, and Google sign-in died at runtime with
// nothing pointing at the cause. Since google-services.json already carries the exact same value,
// fall back to reading it from there and only fail when neither source has it.
fun webClientIdFromGoogleServices(): String {
    val json = file("google-services.json")
    if (!json.exists()) return ""
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(json) as Map<String, Any?>
        (root["client"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("oauth_client") as? List<*> }
            .flatten()
            .mapNotNull { it as? Map<*, *> }
            .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
            ?.get("client_id") as? String
            ?: ""
    }.getOrDefault("")
}

val googleWebClientId: String = localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
    .ifBlank { providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orNull.orEmpty() }
    .ifBlank { webClientIdFromGoogleServices() }
val keystorePath: String = localProps.getProperty("KEYSTORE_PATH", "")
val keystorePass: String = localProps.getProperty("KEYSTORE_PASS", "")
val releaseKeyAlias: String = localProps.getProperty("KEY_ALIAS", "")
val releaseKeyPass: String = localProps.getProperty("KEY_PASS", "")

android {
    namespace = "com.vtempe"
    compileSdk = 36

    signingConfigs {
        if (keystorePath.isNotEmpty()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
            }
        }
    }

    defaultConfig {
        applicationId = "com.vtempe"
        minSdk = 24
        targetSdk = 36
        // 2 was uploaded to Play before the empty-client-id fix and can never be reused, even
        // though that build was never rolled out — Play retires a versionCode on upload.
        versionCode = 3
        versionName = "1.1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://vtempe-server-6uzlqtekka-ew.a.run.app\"")
        buildConfigField("String", "APP_TOKEN", "\"$appToken\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        debug {
            // Temporarily pointing to prod Cloud Run for testing without local server.
            // Switch back to "http://10.0.2.2:8081" when local server needed.
            buildConfigField("String", "API_BASE_URL", "\"https://vtempe-server-6uzlqtekka-ew.a.run.app\"")
        }
        release {
            isMinifyEnabled = true   // R8/ProGuard obfuscates the token
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// A release build needs a working sign-in (VT-AUD-078): the Firebase app config AND the Google
// web client id. A client id alone used to pass, and without google-services.json the app then
// fell back to a stub at runtime — every sign-in UNAVAILABLE, nothing in the build said so.
// Checked before any release variant is built (every release packaging task depends on
// preReleaseBuild), so direct bundle/package tasks cannot skip it. Debug builds stay usable
// without Firebase (guest/offline development).
val releaseAuthProblems: List<String> = buildList {
    if (!googleServicesJsonPresent) {
        add("app-android/google-services.json is missing: Firebase (sign-in, Crashlytics) would not start")
    }
    if (googleWebClientId.isBlank()) {
        add("GOOGLE_WEB_CLIENT_ID is empty: Google sign-in would fail (set it in local.properties or -P, or use a google-services.json with a client_type 3 oauth_client)")
    }
}
val verifyReleaseAuthConfig = tasks.register("verifyReleaseAuthConfig") {
    description = "Fails a release build that would ship without a working Firebase sign-in."
    val problems = releaseAuthProblems
    doLast {
        if (problems.isNotEmpty()) error("Release sign-in is not configured:\n- " + problems.joinToString("\n- "))
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseAuthConfig) }

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

    // Crashlytics/Analytics SDK — always on the classpath (pure library, compiles fine without
    // google-services.json). The google-services/crashlytics GRADLE PLUGINS above are what
    // actually need the json file, so only those are conditional. At runtime,
    // FirebaseAnalyticsRepository init is wrapped in runCatching (see AppModule.kt) and falls
    // back to a no-op logger if the Firebase project isn't configured yet.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

