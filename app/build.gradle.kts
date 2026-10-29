import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads secrets.properties and exposes its entries as configuration inputs.
 *
 * Implemented as a [ValueSource] so the Configuration Cache treats the file as a
 * tracked invalidation input: when any key changes, the cached configuration is
 * invalidated and the new values land in BuildConfig. A plain
 * `Properties.load(FileInputStream)` inside the script is untracked — the stale
 * cached configuration would keep reintroducing the old key values.
 */
abstract class SecretsValueSource : ValueSource<Properties, SecretsValueSource.Params> {
    interface Params : ValueSourceParameters {
        @get:org.gradle.api.tasks.InputFile
        @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
        val secretsFile: org.gradle.api.file.RegularFileProperty
    }

    override fun obtain(): Properties = Properties().apply {
        val file = parameters.secretsFile.get().asFile
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val secretsProperties: Properties = providers.of(SecretsValueSource::class) {
    parameters {
        secretsFile.set(rootProject.file("secrets.properties"))
    }
}.get()

fun resolveSecret(name: String): String? {
    return System.getenv(name)
        ?: secretsProperties.getProperty(name)
        ?: project.findProperty(name)?.toString()
}

android {
    namespace = "com.example.persona"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.persona"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            stl = "c++_shared"
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a"))
        }

        // OpenRouter credentials — resolved from environment or secrets properties
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${resolveSecret("OPENROUTER_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY_V2",
            "\"${resolveSecret("OPENROUTER_API_KEY_V2") ?: ""}\""
        )
        buildConfigField(
            "String",
            "OPENROUTER_BASE_URL",
            "\"${resolveSecret("OPENROUTER_BASE_URL") ?: "https://openrouter.ai/api/v1/chat/completions"}\""
        )

        // Groq Cloud credentials — resolved from environment or secrets properties
        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${resolveSecret("GROQ_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "GROQ_API_KEY_V2",
            "\"${resolveSecret("GROQ_API_KEY_V2") ?: ""}\""
        )
        buildConfigField(
            "String",
            "GROQ_BASE_URL",
            "\"${resolveSecret("GROQ_BASE_URL") ?: "https://api.groq.com/openai/v1/chat/completions"}\""
        )

        // NVIDIA NIM credentials — resolved from environment or secrets properties
        val resolvedNvidiaKey = resolveSecret("NVIDIA_API_KEY") ?: resolveSecret("NIVIDIA_API_KEY") ?: ""
        buildConfigField(
            "String",
            "NVIDIA_API_KEY",
            "\"$resolvedNvidiaKey\""
        )
        buildConfigField(
            "String",
            "NVIDIA_BASE_URL",
            "\"${resolveSecret("NVIDIA_BASE_URL") ?: "https://integrate.api.nvidia.com/v1/chat/completions"}\""
        )

        // QwenCloud / DashScope credentials — resolved from environment or secrets properties
        val resolvedQwenKey = resolveSecret("QWEN_API_KEY") ?: resolveSecret("DASHSCOPE_API_KEY") ?: ""
        buildConfigField(
            "String",
            "QWEN_API_KEY",
            "\"$resolvedQwenKey\""
        )
        buildConfigField(
            "String",
            "QWEN_BASE_URL",
            "\"${resolveSecret("QWEN_BASE_URL") ?: "https://ap-southeast-1.maas.qwencloudapi.com/compatible-mode/v1/chat/completions"}\""
        )
        buildConfigField(
            "String",
            "QWEN_ASR_BASE_URL",
            "\"${resolveSecret("QWEN_ASR_BASE_URL") ?: "https://ap-southeast-1.maas.qwencloudapi.com/api/v1/services/aigc/multimodal-generation/generation"}\""
        )

        // Google Gemini credentials — resolved from environment or secrets properties
        val resolvedGeminiKey = resolveSecret("GEMINI_API_KEY") ?: resolveSecret("GOOGLE_API_KEY") ?: ""
        val resolvedGeminiKey2 = resolveSecret("GEMINI_API_KEY_2") ?: resolveSecret("GEMINI_API_KEY_V2") ?: "gemini_placeholder_key_2"
        buildConfigField("String", "GEMINI_API_KEY", "\"$resolvedGeminiKey\"")
        buildConfigField("String", "GEMINI_API_KEY_2", "\"$resolvedGeminiKey2\"")
        buildConfigField("String", "GEMINI_BASE_URL", "\"${resolveSecret("GEMINI_BASE_URL") ?: "https://generativelanguage.googleapis.com/v1beta"}\"")

        // Liquid API credentials — resolved from environment or secrets properties
        val resolvedLiquidKey = resolveSecret("LIQUID_API_KEY") ?: resolveSecret("LIQUD_API_KEY") ?: ""
        val resolvedLiquidKey2 = resolveSecret("LIQUID_API_KEY_2") ?: resolveSecret("LIQUD_API_KEY_V2") ?: "liquid_placeholder_key_2"
        buildConfigField("String", "LIQUID_API_KEY", "\"$resolvedLiquidKey\"")
        buildConfigField("String", "LIQUID_API_KEY_2", "\"$resolvedLiquidKey2\"")
        buildConfigField("String", "LIQUID_BASE_URL", "\"${resolveSecret("LIQUID_BASE_URL") ?: "https://api.liquid.ai/decisions/v1/systemone"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-language-version", "1.9", "-api-version", "1.9")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core & AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Extended icon set (Orders dashboard nav icon + richer status iconography).
    implementation(libs.androidx.compose.material.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)

    // ONNX Runtime
    implementation(libs.onnxruntime.android)

    // DJL Tokenizer
    implementation(libs.djl.tokenizers)
    implementation(libs.djl.android.tokenizer.native)


    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Serialization & Datetime
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    // Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
