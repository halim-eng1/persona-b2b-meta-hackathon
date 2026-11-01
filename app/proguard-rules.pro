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
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ─────────────────────────────────────────────────────────────────────────────
# ONNX Runtime JNI Keep Rules
# ─────────────────────────────────────────────────────────────────────────────
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# ─────────────────────────────────────────────────────────────────────────────
# DJL / HuggingFace Tokenizers Keep Rules (Native JNI bindings)
# ─────────────────────────────────────────────────────────────────────────────
-keep class ai.djl.** { *; }
-keepclassmembers class ai.djl.** { *; }
-keep class ai.djl.huggingface.** { *; }
-keepclassmembers class ai.djl.huggingface.** { *; }
-keep class ai.djl.huggingface.tokenizers.** { *; }
-keepclassmembers class ai.djl.huggingface.tokenizers.** { *; }
-keep class ai.djl.huggingface.tokenizers.jni.** { *; }
-keepclassmembers class ai.djl.huggingface.tokenizers.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn ai.djl.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.lang3.**

