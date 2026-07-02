# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK's tools/proguard/proguard-android-optimize.txt

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.vtempe.**$$serializer { *; }
-keepclassmembers class com.vtempe.** { *** Companion; }
-keepclasseswithmembers class com.vtempe.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Koin
-keep class org.koin.** { *; }

# SQLDelight
-keep class app.cash.sqldelight.** { *; }

# Keep BuildConfig
-keep class com.vtempe.BuildConfig { *; }

# Ktor / SLF4J missing classes (not available on Android)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder

# Firebase Crashlytics — keep line numbers and exception types so stack traces stay readable
# (the Crashlytics Gradle plugin uploads the mapping file separately for de-obfuscation, but
# these keep rules are still needed for the exception hierarchy to symbolicate correctly).
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
