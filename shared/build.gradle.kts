import app.cash.sqldelight.gradle.VerifyMigrationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.koin.core)

                // HTTP
                api(libs.ktor.client.core)
                api(libs.ktor.client.logging)
                api(libs.ktor.client.contentnegotiation)
                api(libs.ktor.serialization.kotlinx.json)

                // DB & Settings
                api(libs.sqldelight.runtime)
                api(libs.sqldelight.coroutines)
                api(libs.multiplatform.settings)
                api(libs.multiplatform.settings.noarg)

                // Logging
                api(libs.napier)

                // Compose common UI for shared components
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
            }
        }
        androidMain {
            dependencies {
                api(libs.ktor.client.okhttp)
                api(libs.sqldelight.driver.android)
            }
        }
        iosMain {
            dependencies {
                api(libs.ktor.client.darwin)
                api(libs.sqldelight.driver.native)
            }
        }
    }
}

android {
    namespace = "com.vtempe.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.vtempe.shared.db")
            verifyMigrations.set(false)
        }
    }
}
// Windows CI/local builds can fail loading sqlite native libs for verification.
tasks.withType<VerifyMigrationTask>().configureEach {
    enabled = false
}
