# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve debugging line numbers and source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# Room Database Keep Rules
# ─────────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl {
    <init>();
    *;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * implements androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# ─────────────────────────────────────────────────────────────────────────────
# Hilt / Dagger Keep Rules
# ─────────────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentBuilder { *; }
-keep class * extends dagger.internal.DoubleCheck { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# Kotlinx Serialization & Ktor Keep Rules
# ─────────────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName *;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.json.** { *; }
-keep class androidx.navigation.** { *; }
-keep class com.example.persona.navigation.** { *; }
-keep class com.example.persona.features.settings.presentation.SettingsRoute* { *; }
-keep class com.example.persona.features.settings.presentation.SettingsSpoke* { *; }
-keep class com.example.persona.features.settings.presentation.model.SettingKey* { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ─────────────────────────────────────────────────────────────────────────────
# JNI / Native Method Keep Rules
# ─────────────────────────────────────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─────────────────────────────────────────────────────────────────────────────
# SLF4J (transitive via Ktor / kotlinx-coroutines) — no binding is bundled
# ─────────────────────────────────────────────────────────────────────────────
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.lang3.**

