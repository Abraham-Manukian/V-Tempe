import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    val xcf = XCFramework("AppIos")

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "AppIos"
            // Compose Multiplatform resources (Res.string/...) require a resources bundle,
            // which is not supported by static frameworks.
            isStatic = false
            // SQLDelight Native driver (touchlab sqliter) needs sqlite3 symbols at link time.
            linkerOpts("-lsqlite3")
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":ui"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }
    }
}

// Compose Multiplatform resources for iOS must be present in the final app bundle.
// We unpack them from the :ui "zip-for-publication" output into a folder that Xcode can copy.
val uiIosResourcesZip = project(":ui").layout.buildDirectory.file(
    "kotlin-multiplatform-resources/zip-for-publication/iosSimulatorArm64/${project(":ui").name}.kotlin_resources.zip"
)
val coreDesignsystemIosResourcesZip = project(":core-designsystem").layout.buildDirectory.file(
    "kotlin-multiplatform-resources/zip-for-publication/iosSimulatorArm64/${project(":core-designsystem").name}.kotlin_resources.zip"
)

tasks.register<Copy>("prepareComposeResourcesForXcode") {
    group = "compose"
    description = "Unpacks Compose Multiplatform resources for iOS into build/composeResources for Xcode."

    dependsOn(":ui:iosSimulatorArm64ZipMultiplatformResourcesForPublication")
    dependsOn(":core-designsystem:iosSimulatorArm64ZipMultiplatformResourcesForPublication")

    from(uiIosResourcesZip.map { zipTree(it.asFile) })
    from(coreDesignsystemIosResourcesZip.map { zipTree(it.asFile) })
    // Compose resources loader on iOS looks for `compose-resources/composeResources/...` inside the app bundle.
    into(layout.buildDirectory.dir("compose-resources"))
}
