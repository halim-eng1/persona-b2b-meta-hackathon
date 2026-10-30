package com.example.persona.features.settings.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureCredentialsRepository {

    companion object {
        private const val TAG = "SecureCredentialsRepo"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "persona_secure_api_keys"
        private const val PREFS_NAME = "persona_secure_credentials"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_PREFIX_PROVIDER_KEYS = "enc_keys_"
        private const val PREF_KEY_CLOUDFLARE_ACCOUNT_ID = "enc_cloudflare_account_id"
        private const val LEGACY_KEY_NVIDIA = "enc_nvidia_api_key"
        private const val LEGACY_KEY_QWEN = "enc_qwen_api_key"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var keystoreAvailable: Boolean? = null

    // In-memory fallback map if keystore is unavailable (e.g. local unit tests or hardware failure)
    private val memoryFallback = ConcurrentHashMap<String, String>()

    // Flow cache for providers
    private val providerFlows = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    // Flow for Cloudflare Account ID
    private val cloudflareAccountIdFlowInternal = MutableStateFlow<String?>(null)
    override val cloudflareAccountIdFlow: Flow<String?> = cloudflareAccountIdFlowInternal.asStateFlow()

    init {
        // Seed any missing credentials from BuildConfig / secrets.properties
        seedFromBuildConfig()

        // Pre-warm flows for standard providers
        ModelProviders.ALL.forEach { provider ->
            getOrCreateFlow(provider).value = getApiKeys(provider)
        }

        // Initialize Cloudflare Account ID
        cloudflareAccountIdFlowInternal.value = getCloudflareAccountId()
    }

    private fun getOrCreateFlow(provider: String): MutableStateFlow<List<String>> {
        val norm = ModelProviders.normalize(provider)
        return providerFlows.computeIfAbsent(norm) {
            MutableStateFlow(emptyList())
        }
    }

    private fun prefKey(provider: String): String =
        PREF_PREFIX_PROVIDER_KEYS + ModelProviders.normalize(provider)

    override fun getApiKeys(provider: String): List<String> {
        val norm = ModelProviders.normalize(provider)
        val key = prefKey(norm)
        val encryptedData = readCredential(key)

        var keys = deserializeKeys(encryptedData)

        // Handle legacy single-key migration if needed
        if (keys.isEmpty()) {
            if (norm == ModelProviders.NVIDIA) {
                val legacy = readCredential(LEGACY_KEY_NVIDIA)
                if (legacy.isNotBlank()) {
                    keys = listOf(legacy)
                    setApiKeys(norm, keys)
                }
            } else if (norm == ModelProviders.QWEN) {
                val legacy = readCredential(LEGACY_KEY_QWEN)
                if (legacy.isNotBlank()) {
                    keys = listOf(legacy)
                    setApiKeys(norm, keys)
                }
            }
        }

        // If keys are STILL empty, seed from BuildConfig without overwriting existing
        if (keys.isEmpty()) {
            val buildConfigSeeds = getBuildConfigSeedKeys(norm)
            if (buildConfigSeeds.isNotEmpty()) {
                keys = buildConfigSeeds
                setApiKeys(norm, keys)
            }
        }

        getOrCreateFlow(norm).value = keys
        return keys
    }

    override fun setApiKeys(provider: String, keys: List<String>) {
        val norm = ModelProviders.normalize(provider)
        val sanitized = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val key = prefKey(norm)

        if (sanitized.isEmpty()) {
            writeCredential(key, "")
        } else {
            val serialized = serializeKeys(sanitized)
            writeCredential(key, serialized)
        }

        getOrCreateFlow(norm).value = sanitized
    }

    override fun addApiKey(provider: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        val current = getApiKeys(provider)
        if (!current.contains(trimmed)) {
            setApiKeys(provider, current + trimmed)
        }
    }

    override fun removeApiKey(provider: String, key: String) {
        val trimmed = key.trim()
        val current = getApiKeys(provider)
        setApiKeys(provider, current.filter { it != trimmed })
    }

    override fun getApiKeysFlow(provider: String): Flow<List<String>> {
        val norm = ModelProviders.normalize(provider)
        val flow = getOrCreateFlow(norm)
        flow.value = getApiKeys(norm)
        return flow.asStateFlow()
    }

    // ── Cloudflare Account ID Management ──────────────────────────────────────

    override fun getCloudflareAccountId(): String {
        val stored = readCredential(PREF_KEY_CLOUDFLARE_ACCOUNT_ID).trim()
        if (stored.isNotBlank()) {
            cloudflareAccountIdFlowInternal.value = stored
            return stored
        }

        val buildConfigFallback = getBuildConfigString("CLOUDFLARE_ACCOUNT_ID").trim()
        if (buildConfigFallback.isNotBlank()) {
            // Seed into secure storage so it is persisted
            writeCredential(PREF_KEY_CLOUDFLARE_ACCOUNT_ID, buildConfigFallback)
            cloudflareAccountIdFlowInternal.value = buildConfigFallback
            return buildConfigFallback
        }

        cloudflareAccountIdFlowInternal.value = ""
        return ""
    }

    override fun setCloudflareAccountId(accountId: String?) {
        val trimmed = accountId?.trim().orEmpty()
        writeCredential(PREF_KEY_CLOUDFLARE_ACCOUNT_ID, trimmed)
        cloudflareAccountIdFlowInternal.value = trimmed.ifBlank { getCloudflareAccountId() }
    }

    // ── BuildConfig Seeding ───────────────────────────────────────────────────

    private fun seedFromBuildConfig() {
        ModelProviders.ALL.forEach { provider ->
            val norm = ModelProviders.normalize(provider)
            val stored = readCredential(prefKey(norm))
            val existing = deserializeKeys(stored)
            if (existing.isEmpty()) {
                val seeds = getBuildConfigSeedKeys(norm)
                if (seeds.isNotEmpty()) {
                    setApiKeys(norm, seeds)
                }
            }
        }

        val currentCfId = readCredential(PREF_KEY_CLOUDFLARE_ACCOUNT_ID).trim()
        if (currentCfId.isBlank()) {
            val cfIdSeed = getBuildConfigString("CLOUDFLARE_ACCOUNT_ID").trim()
            if (cfIdSeed.isNotBlank()) {
                writeCredential(PREF_KEY_CLOUDFLARE_ACCOUNT_ID, cfIdSeed)
            }
        }
    }

    private fun getBuildConfigSeedKeys(provider: String): List<String> {
        val rawCandidates = when (ModelProviders.normalize(provider)) {
            ModelProviders.QWEN -> listOf(
                getBuildConfigString("QWEN_API_KEY"),
                getBuildConfigString("DASHSCOPE_API_KEY")
            )
            ModelProviders.GEMINI -> listOf(
                getBuildConfigString("GEMINI_API_KEY"),
                getBuildConfigString("GEMINI_API_KEY_2"),
                getBuildConfigString("GEMINI_API_KEY_V2")
            )
            ModelProviders.NVIDIA -> listOf(
                getBuildConfigString("NVIDIA_API_KEY")
            )
            ModelProviders.GROQ -> listOf(
                getBuildConfigString("GROQ_API_KEY"),
                getBuildConfigString("GROQ_API_KEY_2"),
                getBuildConfigString("GROQ_API_KEY_V2")
            )
            ModelProviders.OPENROUTER -> listOf(
                getBuildConfigString("OPENROUTER_API_KEY"),
                getBuildConfigString("OPENROUTER_API_KEY_V2")
            )
            ModelProviders.CLOUDFLARE -> listOf(
                getBuildConfigString("CLOUDFLARE_API_TOKEN")
            )
            ModelProviders.LIQUID -> listOf(
                getBuildConfigString("LIQUID_API_KEY"),
                getBuildConfigString("LIQUID_API_KEY_2"),
                getBuildConfigString("LIQUD_API_KEY"),
                getBuildConfigString("LIQUD_API_KEY_V2")
            )
            ModelProviders.NARA -> listOf(
                getBuildConfigString("NARA_ROUTER_API_KEY")
            )
            else -> emptyList()
        }

        return rawCandidates
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("placeholder", ignoreCase = true) }
            .distinct()
    }

    private fun getBuildConfigString(fieldName: String): String {
        return runCatching {
            val field = BuildConfig::class.java.getField(fieldName)
            (field.get(null) as? String)?.trim().orEmpty()
        }.getOrDefault("")
    }

    // ── Convenience backward-compatibility wrappers ───────────────────────────

    override fun getNvidiaApiKey(): String {
        return getApiKeys(ModelProviders.NVIDIA).firstOrNull().orEmpty()
    }

    override fun setNvidiaApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            setApiKeys(ModelProviders.NVIDIA, emptyList())
        } else {
            setApiKeys(ModelProviders.NVIDIA, listOf(trimmed))
        }
    }

    override fun getQwenApiKey(): String {
        return getApiKeys(ModelProviders.QWEN).firstOrNull().orEmpty()
    }

    override fun setQwenApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            setApiKeys(ModelProviders.QWEN, emptyList())
        } else {
            setApiKeys(ModelProviders.QWEN, listOf(trimmed))
        }
    }

    override val nvidiaApiKeyFlow: Flow<String?> =
        getApiKeysFlow(ModelProviders.NVIDIA).map { it.firstOrNull() }

    override val qwenApiKeyFlow: Flow<String?> =
        getApiKeysFlow(ModelProviders.QWEN).map { it.firstOrNull() }

    // ── Internal Keystore Encryption & Decryption ────────────────────────────

    private fun ensureKeystore(): Boolean {
        if (keystoreAvailable == true) return true
        val initialized = initKeystore()
        keystoreAvailable = initialized
        return initialized
    }

    private fun initKeystore(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS) || keyStore.getKey(KEY_ALIAS, null) == null) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "AndroidKeyStore is not available; falling back to memory store only: ${e.message}")
            false
        }
    }

    private fun writeCredential(prefKey: String, plainText: String) {
        if (plainText.isBlank()) {
            memoryFallback.remove(prefKey)
            try {
                prefs.edit().remove(prefKey).apply()
            } catch (_: Throwable) {}
            return
        }

        if (ensureKeystore()) {
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (secretKey != null) {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    val iv = cipher.iv
                    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

                    val combined = ByteArray(iv.size + encrypted.size)
                    System.arraycopy(iv, 0, combined, 0, iv.size)
                    System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

                    val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
                    prefs.edit().putString(prefKey, encoded).apply()
                    memoryFallback[prefKey] = plainText
                    return
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to encrypt credential with KeyStore: ${e.message}")
            }
        }

        // STRICT SECURITY: Never store plaintext or Base64 in persistent unencrypted SharedPreferences.
        // Fall back only to transient process memory.
        memoryFallback[prefKey] = plainText
    }

    private fun readCredential(prefKey: String): String {
        if (ensureKeystore()) {
            val storedBase64 = try {
                prefs.getString(prefKey, null)
            } catch (_: Throwable) {
                null
            }
            if (!storedBase64.isNullOrBlank()) {
                try {
                    val combined = Base64.decode(storedBase64, Base64.NO_WRAP)
                    if (combined.size > GCM_IV_LENGTH) {
                        val iv = ByteArray(GCM_IV_LENGTH)
                        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
                        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
                        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                        val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                        if (secretKey != null) {
                            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                            val decrypted = cipher.doFinal(cipherText)
                            val result = String(decrypted, Charsets.UTF_8)
                            memoryFallback[prefKey] = result
                            return result
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to decrypt credential with KeyStore: ${e.message}")
                }
            }
        }

        // Fallback read from secure transient memory
        return memoryFallback[prefKey].orEmpty()
    }

    private fun serializeKeys(keys: List<String>): String {
        return try {
            val array = JSONArray()
            keys.forEach { array.put(it) }
            val json = array.toString()
            if (!json.isNullOrBlank() && json != "null") json else keys.joinToString("\n")
        } catch (_: Throwable) {
            keys.joinToString("\n")
        }
    }

    private fun deserializeKeys(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val str = (array.optString(i) ?: array.opt(i)?.toString()).orEmpty().trim()
                if (str.isNotEmpty() && str != "null") list.add(str)
            }
            if (list.isNotEmpty()) list else raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            // Support newline-delimited or single string fallback
            raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
