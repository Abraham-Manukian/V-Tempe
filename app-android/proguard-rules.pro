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
