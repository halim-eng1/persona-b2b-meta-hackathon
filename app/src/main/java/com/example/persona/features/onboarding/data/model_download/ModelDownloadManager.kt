package com.example.persona.features.onboarding.data.model_download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.example.persona.core.di.IoDispatcher
import com.example.persona.features.onboarding.domain.model_download.DownloadProgress
import com.example.persona.features.onboarding.domain.model_download.ModelAssetSpec
import com.example.persona.features.onboarding.domain.model_download.ModelDownloadState
import com.example.persona.features.onboarding.domain.model_download.ModelValidator
import com.example.persona.features.onboarding.domain.model_download.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelValidator: ModelValidator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "ModelDownloadManager"
        const val REQUIRED_STORAGE_BYTES = 300L * 1024L * 1024L // 300 MB

        val MODEL_SPEC = ModelAssetSpec(
            fileName = "model_qint8_arm64.onnx",
            url = "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_qint8_arm64.onnx?download=true",
            expectedBytes = 118_412_398L,
            expectedSha256 = "783fea82d71a58179b830a4dbd2d58447e640609e98eedf9ffa12622d375a672",
            relativePath = "models/model_qint8_arm64.onnx"
        )

        val TOKENIZER_SPEC = ModelAssetSpec(
            fileName = "tokenizer.json",
            url = "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json?download=true",
            expectedBytes = 9_081_518L,
            expectedSha256 = "2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8",
            relativePath = "models/tokenizer.json"
        )

        val ALL_SPECS = listOf(MODEL_SPEC, TOKENIZER_SPEC)
        val TOTAL_EXPECTED_BYTES = ALL_SPECS.sumOf { it.expectedBytes }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var allowCellularDownload = false

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Entry-point to initiate or resume preparation.
     */
    fun startDownload(forceCellular: Boolean = false) {
        if (forceCellular) {
            allowCellularDownload = true
        }

        if (downloadJob?.isActive == true) {
            Log.d(TAG, "Download job already active; skipping duplicate trigger.")
            return
        }

        downloadJob = applicationScope.launch {
            executeDownloadPipeline()
        }
    }

    /**
     * User explicitly confirmed downloading via mobile data bundle.
     */
    fun proceedOnCellular() {
        allowCellularDownload = true
        startDownload(forceCellular = true)
    }

    /**
     * Retry from failure or disconnection.
     */
    fun retry() {
        _state.value = ModelDownloadState.Idle
        startDownload()
    }

    /**
     * Cancel ongoing download job.
     */
    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = ModelDownloadState.Idle
    }

    private suspend fun executeDownloadPipeline() = withContext(ioDispatcher) {
        // 1. Storage Pre-flight Check
        _state.value = ModelDownloadState.CheckingStorage
        val usableSpace = getUsableStorageBytes()
        if (usableSpace < REQUIRED_STORAGE_BYTES) {
            Log.w(TAG, "Insufficient storage: usable=$usableSpace bytes, required=$REQUIRED_STORAGE_BYTES bytes")
            _state.value = ModelDownloadState.InsufficientStorage(
                requiredBytes = REQUIRED_STORAGE_BYTES,
                availableBytes = usableSpace
            )
            return@withContext
        }

        // 2. Network Pre-flight Check (Egyptian Mobile Quota Safeguard)
        val isCellular = isConnectedToCellular()
        val isWifi = isConnectedToWifi()
        if (isCellular && !isWifi && !allowCellularDownload) {
            Log.i(TAG, "Connected via cellular; requesting user confirmation before downloading 127MB.")
            _state.value = ModelDownloadState.CellularConfirmationRequired(TOTAL_EXPECTED_BYTES)
            return@withContext
        }

        // 3. Check if all target files already exist and match SHA-256
        val allValid = ALL_SPECS.all { spec ->
            val targetFile = File(context.filesDir, spec.relativePath)
            targetFile.exists() && targetFile.length() == spec.expectedBytes && verifySha256(targetFile, spec.expectedSha256)
        }

        if (allValid) {
            Log.i(TAG, "All model assets already downloaded and verified. Running dry-run validation.")
            runValidation()
            return@withContext
        }

        // 4. Start ModelDownloadService in foreground for ongoing notification resilience
        try {
            ModelDownloadService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start ModelDownloadService in foreground: ${e.message}")
        }

        // 5. Download each asset sequentially with resumption & backoff
        for (spec in ALL_SPECS) {
            val targetFile = File(context.filesDir, spec.relativePath)
            if (targetFile.exists() && targetFile.length() == spec.expectedBytes && verifySha256(targetFile, spec.expectedSha256)) {
                Log.d(TAG, "Asset ${spec.fileName} already present and valid.")
                continue
            }

            var success = false
            var retryCount = 0
            val maxRetries = 3

            while (!success && retryCount <= maxRetries) {
                try {
                    downloadSingleAsset(spec)
                    success = true
                } catch (e: Exception) {
                    retryCount++
                    Log.e(TAG, "Error downloading ${spec.fileName} (attempt $retryCount/$maxRetries): ${e.message}", e)
                    if (retryCount <= maxRetries) {
                        delay(1000L * (1 shl retryCount))
                    } else {
                        val isNetAvailable = isConnectedToWifi() || isConnectedToCellular()
                        if (!isNetAvailable) {
                            val currentProg = (_state.value as? ModelDownloadState.Downloading)?.progress ?: DownloadProgress()
                            _state.value = ModelDownloadState.NetworkDisconnected(currentProg)
                        } else {
                            _state.value = ModelDownloadState.Failed(
                                errorMessage = e.message ?: "Failed to download ${spec.fileName}",
                                canRetry = true
                            )
                        }
                        ModelDownloadService.stop(context)
                        return@withContext
                    }
                }
            }
        }

        // 6. Execute Headless Dry-Run Validation
        runValidation()
        ModelDownloadService.stop(context)
    }

    private suspend fun downloadSingleAsset(spec: ModelAssetSpec) = withContext(ioDispatcher) {
        val targetFile = File(context.filesDir, spec.relativePath)
        targetFile.parentFile?.mkdirs()
        val tmpFile = File(context.filesDir, "${spec.relativePath}.tmp")

        var existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L

        // If tmpFile is somehow larger than expected, purge and restart
        if (existingBytes > spec.expectedBytes) {
            tmpFile.delete()
            existingBytes = 0L
        }

        val requestBuilder = Request.Builder()
            .url(spec.url)
            .addHeader("User-Agent", "Persona-B2B-Android-Client/1.0")

        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            Log.i(TAG, "Requesting resumption for ${spec.fileName} from byte $existingBytes")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206 && response.code != 416) {
            throw IOException("Unexpected HTTP response code: ${response.code} for ${spec.url}")
        }

        if (response.code == 416) {
            // Range not satisfiable -> reset tmp file and retry from byte 0
            tmpFile.delete()
            existingBytes = 0L
            val freshRequest = Request.Builder()
                .url(spec.url)
                .addHeader("User-Agent", "Persona-B2B-Android-Client/1.0")
                .build()
            val freshResponse = okHttpClient.newCall(freshRequest).execute()
            if (!freshResponse.isSuccessful) {
                throw IOException("HTTP error on restart: ${freshResponse.code}")
            }
            streamResponseToFile(freshResponse, tmpFile, spec, 0L)
        } else {
            val appendMode = (response.code == 206)
            streamResponseToFile(response, tmpFile, spec, if (appendMode) existingBytes else 0L)
        }

        // Verify SHA-256
        _state.value = ModelDownloadState.Verifying(spec.fileName)
        Log.i(TAG, "Verifying SHA-256 for ${spec.fileName}...")
        val hashMatches = verifySha256(tmpFile, spec.expectedSha256)
        if (!hashMatches) {
            tmpFile.delete()
            throw IOException("SHA-256 checksum mismatch for ${spec.fileName}. Expected ${spec.expectedSha256}")
        }

        // Atomic rename .tmp -> target
        if (targetFile.exists()) targetFile.delete()
        val renamed = tmpFile.renameTo(targetFile)
        if (!renamed) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        Log.i(TAG, "Successfully downloaded, verified, and placed ${spec.fileName}")
    }

    private fun streamResponseToFile(
        response: okhttp3.Response,
        file: File,
        spec: ModelAssetSpec,
        initialBytes: Long
    ) {
        val body = response.body ?: throw IOException("Empty response body for ${spec.fileName}")
        val appendMode = initialBytes > 0L
        var totalDownloadedForFile = initialBytes

        var lastLogTime = System.currentTimeMillis()
        var bytesReadSinceLastLog = 0L

        body.byteStream().use { input ->
            FileOutputStream(file, appendMode).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalDownloadedForFile += bytesRead
                    bytesReadSinceLastLog += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastLogTime
                    if (timeDelta >= 200L) {
                        val speedBytesPerSec = if (timeDelta > 0) (bytesReadSinceLastLog * 1000L) / timeDelta else 0L
                        val totalDownloadedCombined = computeTotalDownloadedBytes(spec, totalDownloadedForFile)
                        val remainingBytes = (TOTAL_EXPECTED_BYTES - totalDownloadedCombined).coerceAtLeast(0L)
                        val etaSeconds = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0L

                        val progress = DownloadProgress(
                            downloadedBytes = totalDownloadedCombined,
                            totalBytes = TOTAL_EXPECTED_BYTES,
                            speedBytesPerSec = speedBytesPerSec,
                            etaSeconds = etaSeconds,
                            currentFileName = spec.fileName
                        )
                        _state.value = ModelDownloadState.Downloading(progress)
                        lastLogTime = now
                        bytesReadSinceLastLog = 0L
                    }
                }
            }
        }
    }

    private fun computeTotalDownloadedBytes(currentSpec: ModelAssetSpec, currentSpecDownloaded: Long): Long {
        var sum = 0L
        for (spec in ALL_SPECS) {
            if (spec.fileName == currentSpec.fileName) {
                sum += currentSpecDownloaded
            } else {
                val file = File(context.filesDir, spec.relativePath)
                if (file.exists()) {
                    sum += file.length()
                }
            }
        }
        return sum.coerceAtMost(TOTAL_EXPECTED_BYTES)
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val calculated = digest.digest().joinToString("") { "%02x".format(it) }
            calculated.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256 calculation error on ${file.name}", e)
            false
        }
    }

    private suspend fun runValidation() {
        _state.value = ModelDownloadState.Verifying("جاري تشغيل محرك الذكاء الاصطناعي...")
        val modelsDir = File(context.filesDir, "models")
        when (val result = modelValidator.validate(modelsDir)) {
            is ValidationResult.Success -> {
                Log.i(TAG, "Validation passed successfully!")
                _state.value = ModelDownloadState.Success
            }
            is ValidationResult.Failed -> {
                Log.e(TAG, "Validation failed: ${result.reason}")
                _state.value = ModelDownloadState.Failed(result.reason, canRetry = true)
            }
        }
    }

    internal var storageBytesProvider: (() -> Long)? = null

    fun getUsableStorageBytes(): Long {
        storageBytesProvider?.let { return it.invoke() }
        return try {
            StatFs(context.filesDir.absolutePath).availableBytes
        } catch (e: Exception) {
            Log.w(TAG, "StatFs availableBytes check failed: ${e.message}")
            0L
        }
    }

    fun isConnectedToWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isConnectedToCellular(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
