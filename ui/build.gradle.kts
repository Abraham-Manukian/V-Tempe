plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(17)
    androidTarget()

    val xcfName = "uiKit"

    iosX64 {
        binaries.framework { baseName = xcfName }
    }
    iosArm64 {
        binaries.framework { baseName = xcfName }
    }
    iosSimulatorArm64 {
        binaries.framework { baseName = xcfName }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":core-designsystem"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.datetime)
                implementation(libs.coil.compose)
            }
            kotlin.srcDirs("build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors")
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.insert-koin:koin-androidx-compose:3.5.6")
                implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
                implementation(libs.androidx.activity.compose)
                implementation("androidx.compose.material:material-icons-extended:1.7.1")
                implementation(libs.material)
                implementation(libs.androidx.credentials)
                implementation(libs.androidx.credentials.play.services.auth)
                implementation(libs.googleid)
                // Coil network fetcher — auto-registers on Android so AsyncImage can load the
                // Google account photo. iOS shows no photo (Apple doesn't share one), so no iOS
                // network fetcher is needed.
                implementation(libs.coil.network.okhttp)
            }
            kotlin.srcDirs("build/generated/compose/resourceGenerator/kotlin/androidMainResourceAccessors")
        }
        val iosMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.components.resources)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.vtempe.ui"
}

android {
    namespace = "com.vtempe.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

