package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppMediaExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient
) {
    internal var groqPrimaryApiKeyOverride: String? = null
    internal var groqFallbackApiKeyOverride: String? = null
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val groqPrimaryApiKey: String
        get() = groqPrimaryApiKeyOverride ?: BuildConfig.GROQ_API_KEY

    private val groqFallbackApiKey: String
        get() = groqFallbackApiKeyOverride ?: BuildConfig.GROQ_API_KEY_V2

    companion object {
        private const val TAG = "PersonaMedia"

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }

    internal constructor(
        context: Context,
        httpClient: HttpClient,
        groqPrimaryApiKey: String?,
        groqFallbackApiKey: String?
    ) : this(context, httpClient) {
        this.groqPrimaryApiKeyOverride = groqPrimaryApiKey
        this.groqFallbackApiKeyOverride = groqFallbackApiKey
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 1: Direct Image Extraction from Notification Extras
    // ──────────────────────────────────────────────────────────────────────────

    private val claimedDiskFiles = ConcurrentHashMap<String, Long>()

    fun clearClaimedDiskFiles() {
        claimedDiskFiles.clear()
    }

    fun isDiskFileClaimed(path: String): Boolean {
        return claimedDiskFiles.containsKey(path)
    }

    fun evictStaleClaimedDiskFiles(now: Long = System.currentTimeMillis()) {
        val tenMinutesMs = 10 * 60 * 1000L
        claimedDiskFiles.entries.removeIf { now - it.value > tenMinutesMs }
    }

    fun purgeStaleMediaCache(maxAgeMs: Long = 60 * 60 * 1000L) {
        try {
            val cacheFiles = context.cacheDir.listFiles { f ->
                f.isFile && (f.name.startsWith("incoming_voice_") || f.name.startsWith("incoming_media_"))
            } ?: return
            val now = System.currentTimeMillis()
            for (f in cacheFiles) {
                if (now - f.lastModified() > maxAgeMs) {
                    f.delete()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to purge stale media cache: ${e.message}")
        }
    }

    private val claimedVoiceFiles = ConcurrentHashMap<String, Long>()

    fun clearClaimedVoiceFiles() {
        claimedVoiceFiles.clear()
    }

    fun isVoiceFileClaimed(path: String): Boolean {
        return claimedVoiceFiles.containsKey(path)
    }

    private fun evictStaleClaimedVoiceFiles(now: Long = System.currentTimeMillis()) {
        val tenMinutesMs = 10 * 60 * 1000L
        claimedVoiceFiles.entries.removeIf { now - it.value > tenMinutesMs }
    }

    /**
     * Extracts an image from the status bar notification extras.
     * Uses a non-blocking staged micro-retry loop (50ms -> 100ms -> 200ms) on [ioDispatcher]
     * to allow asynchronous WhatsApp background services to populate the MessagingStyle Uri.
     * If notification extras do not yield a bitmap after ~350ms, falls back to disk monitoring.
     * Saves the resulting bitmap to app cache as high-quality JPEG and returns the file.
     */
    suspend fun extractImage(
        sbn: StatusBarNotification,
        sourceClass: NotificationSourceClass,
        listenerService: NotificationListenerService? = null,
        activeNotificationProvider: ((String) -> StatusBarNotification?)? = null,
        currentText: String? = null
    ): File? = withContext(ioDispatcher) {
        // Prevent stale conversation notification extras from triggering staged polling on pure text messages
        if (!hasNotificationImage(sbn, currentText)) {
            return@withContext null
        }

        // Attempt 0: Immediate direct extraction from notification extras
        var currentSbn = sbn
        val initialBitmap = extractBitmapFromNotification(currentSbn)
        if (initialBitmap != null) {
            val logMsg = "📸 Successfully extracted image bitmap from notification extras! (Width: ${initialBitmap.width}, Height: ${initialBitmap.height})"
            Log.i(TAG, logMsg)
            PersonaLogger.i(TAG, logMsg)
            val cachedFile = saveBitmapToCache(initialBitmap)
            if (cachedFile != null) return@withContext cachedFile
        }

        // Attempt 1..3: Non-blocking staged micro-retry polling loop (50ms -> 100ms -> 200ms)
        val getLatestSbn: (String) -> StatusBarNotification? = { key ->
            activeNotificationProvider?.invoke(key)
                ?: runCatching { listenerService?.activeNotifications?.firstOrNull { it.key == key } }.getOrNull()
        }

        val delays = listOf(50L, 100L, 200L)
        val startPollTime = System.currentTimeMillis()
        val startLog = "⏳ Media indicator detected without immediate URI. Starting staged polling (50ms, 100ms, 200ms)..."
        Log.i(TAG, startLog)
        PersonaLogger.i(TAG, startLog)

        for ((index, delayMs) in delays.withIndex()) {
            val attemptNumber = index + 1
            delay(delayMs)

            val updatedSbn = getLatestSbn(sbn.key)
            if (updatedSbn != null) {
                currentSbn = updatedSbn
            }

            if (!hasNotificationImage(currentSbn, currentText)) {
                Log.d(TAG, "Notification ${sbn.key} does not indicate an image turn on retry attempt #$attemptNumber; aborting.")
                return@withContext null
            }

            val retryBitmap = extractBitmapFromNotification(currentSbn)
            if (retryBitmap != null) {
                val elapsed = System.currentTimeMillis() - startPollTime
                val successLog = "📸 Successfully caught image URI on retry attempt #$attemptNumber (elapsed: ${elapsed} ms)!"
                Log.i(TAG, successLog)
                PersonaLogger.i(TAG, successLog)
                val cachedFile = saveBitmapToCache(retryBitmap)
                if (cachedFile != null) return@withContext cachedFile
            }
        }

        val timeoutLog = "⚠️ Micro-polling exhausted after 350ms without URI. Escalating to disk fallback."
        Log.w(TAG, timeoutLog)
        PersonaLogger.w(TAG, timeoutLog)

        // Fallback: Query disk for the newly received image
        return@withContext recoverImageFromDiskFallback(sbn, sourceClass)
    }

    /**
     * Safe final tier fallback for recovering images from disk.
     */
    fun recoverImageFromDiskFallback(
        sbn: StatusBarNotification,
        sourceClass: NotificationSourceClass = NotificationSourceClass.UNKNOWN
    ): File? {
        return findLatestImageOnDisk(sourceClass, sbn.postTime)
    }

    /**
     * Direct notification bundle inspection in order:
     * 1. MessagingStyle message bundles ("uri" / "type" and "data_uri" / "data_mime_type" across EXTRA_MESSAGES and EXTRA_HISTORIC_MESSAGES)
     * 2. Notification.EXTRA_PICTURE ("android.picture") as Bitmap or Icon
     * 3. Notification.EXTRA_PICTURE_ICON ("android.pictureIcon")
     * 4. Notification.EXTRA_LARGE_ICON_BIG ("android.largeIcon.big") as Bitmap or Icon
     */
    fun extractBitmapFromNotification(sbn: StatusBarNotification): Bitmap? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        logNotificationExtrasStructure(extras)

        // 1. MessagingStyle message bundles ("uri" / "type" and "data_uri" / "data_mime_type")
        try {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            val bmp = extractBitmapFromMessagesBundle(messages)
            if (bmp != null) return bmp

            val historicMessages = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)
            val historicBmp = extractBitmapFromMessagesBundle(historicMessages)
            if (historicBmp != null) return historicBmp
        } catch (e: Throwable) {
            Log.w(TAG, "Error inspecting MessagingStyle messages: ${e.message}")
        }

        // 2. Notification.EXTRA_PICTURE ("android.picture") as Bitmap or Icon
        try {
            val picObj = extras.get(Notification.EXTRA_PICTURE)
            if (picObj is Bitmap) return picObj
            if (picObj is Icon) {
                val bmp = iconToBitmap(picObj)
                if (bmp != null) return bmp
            }
            val parcelableBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
            }
            if (parcelableBmp != null) return parcelableBmp
        } catch (e: Throwable) {
            Log.w(TAG, "Error inspecting EXTRA_PICTURE: ${e.message}")
        }

        // 3. Notification.EXTRA_PICTURE_ICON ("android.pictureIcon") as Icon or Bitmap
        try {
            val picIconObj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                extras.get(Notification.EXTRA_PICTURE_ICON) ?: extras.get("android.pictureIcon")
            } else {
                extras.get("android.pictureIcon")
            }
            if (picIconObj is Icon) {
                val bmp = iconToBitmap(picIconObj)
                if (bmp != null) return bmp
            }
            if (picIconObj is Bitmap) return picIconObj
        } catch (e: Throwable) {
            Log.w(TAG, "Error inspecting EXTRA_PICTURE_ICON: ${e.message}")
        }

        // 4. Notification.EXTRA_LARGE_ICON_BIG ("android.largeIcon.big") as Bitmap or Icon
        try {
            val largeBigObj = extras.get(Notification.EXTRA_LARGE_ICON_BIG)
                ?: extras.get("android.largeIcon.big")
            if (largeBigObj is Bitmap) return largeBigObj
            if (largeBigObj is Icon) {
                val bmp = iconToBitmap(largeBigObj)
                if (bmp != null) return bmp
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error inspecting EXTRA_LARGE_ICON_BIG: ${e.message}")
        }

        return null
    }

    internal fun extractBitmapFromMessagesBundle(messagesArray: Array<*>?): Bitmap? {
        if (messagesArray == null || messagesArray.isEmpty()) return null
        for (i in messagesArray.indices.reversed()) {
            val bundle = messagesArray[i] as? Bundle ?: continue
            val mimeType = bundle.getString("type")
                ?: bundle.getString("data_mime_type")
                ?: bundle.getCharSequence("type")?.toString()
                ?: bundle.getCharSequence("data_mime_type")?.toString()

            val uri = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable("uri", Uri::class.java)
                        ?: bundle.getParcelable("data_uri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (bundle.getParcelable<Uri>("uri") ?: bundle.getParcelable<Uri>("data_uri"))
                }
            }.getOrNull() ?: (bundle.get("uri") as? Uri) ?: (bundle.get("data_uri") as? Uri)
              ?: bundle.getString("uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
              ?: bundle.getString("data_mime_type")?.let { runCatching { Uri.parse(it) }.getOrNull() }

            if (uri != null) {
                val isImage = mimeType?.startsWith("image/", ignoreCase = true) == true ||
                        uri.toString().contains("image", ignoreCase = true) ||
                        mimeType == null
                if (isImage) {
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                        stream?.use {
                            val decoded = BitmapFactory.decodeStream(it)
                            if (decoded != null) {
                                Log.i(TAG, "📸 Decoded image from MessagingStyle URI: $uri (mime: $mimeType)")
                                return decoded
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to open/decode stream from MessagingStyle URI $uri: ${e.message}")
                    }
                }
            }
        }
        return null
    }

    /**
     * Diagnostic logger that records all present keys and their runtime types in Logcat under [TAG].
     */
    fun logNotificationExtrasStructure(extras: Bundle) {
        try {
            val keys = extras.keySet()
            val sb = StringBuilder("📋 Notification Extras Structure (${keys.size} keys):\n")
            for (key in keys) {
                val value = extras.get(key)
                val typeName = value?.javaClass?.name ?: "null"
                sb.append("  - $key -> $typeName\n")
            }
            val structureStr = sb.toString().trimEnd()
            Log.d(TAG, structureStr)
            PersonaLogger.d(TAG, structureStr)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to log notification extras structure: ${e.message}")
        }
    }

    /**
     * Resolves an Android Icon to a Bitmap using reflection or Drawable rendering.
     */
    fun iconToBitmap(icon: Icon): Bitmap? {
        return try {
            // Fast path: reflectively extract Bitmap if already held in mObj1
            try {
                val method = icon.javaClass.getDeclaredMethod("getBitmap")
                method.isAccessible = true
                val bmp = method.invoke(icon) as? Bitmap
                if (bmp != null) return bmp
            } catch (_: Throwable) {
                // Reflection failed, proceed to loadDrawable
            }

            val drawable = icon.loadDrawable(context) ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }

            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to resolve Icon to Bitmap: ${e.message}")
            null
        }
    }

    /**
     * Persists the given bitmap to application cache as a high-quality JPEG.
     */
    fun saveBitmapToCache(bitmap: Bitmap): File? {
        return try {
            val file = File(context.cacheDir, "incoming_media_${System.currentTimeMillis()}_${System.nanoTime()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Fallback for Images (Disk Monitoring):
     * Queries the latest modified image in WhatsApp media directory
     * within 5-10 seconds of notification postTime, enforcing anti-collision claim.
     */
    fun findLatestImageOnDisk(
        sourceClass: NotificationSourceClass,
        postTime: Long = System.currentTimeMillis()
    ): File? {
        val dirs = getWhatsAppImageDirectories(sourceClass)
        val now = if (postTime > 0) postTime else System.currentTimeMillis()
        evictStaleClaimedDiskFiles(now)

        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { f ->
                f.isFile && !f.name.startsWith(".") && f.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: continue

            // Filter by last modified timestamp within 5-10 seconds of the notification
            // and exclude files already claimed by concurrent turns
            val candidates = files
                .filter {
                    val delta = Math.abs(it.lastModified() - now)
                    delta <= 10_000L && !claimedDiskFiles.containsKey(it.absolutePath)
                }
                .sortedByDescending { it.lastModified() }

            for (candidate in candidates) {
                val delta = Math.abs(candidate.lastModified() - now)
                val claimed = claimedDiskFiles.putIfAbsent(candidate.absolutePath, now) == null
                if (claimed) {
                    val logMsg = "📸 Successfully recovered image from disk fallback: ${candidate.absolutePath} (delta: ${delta}ms)"
                    Log.i(TAG, logMsg)
                    PersonaLogger.i(TAG, logMsg)
                    return candidate
                } else {
                    Log.w(TAG, "Disk file collision avoided: ${candidate.absolutePath} was already claimed by another turn")
                }
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 2: Voice Note (.opus / .ogg) Interception & Whisper Transcription
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inspects notification extras for direct audio streams in MessagingStyle messages.
     * If an audio URI is present, opens the stream directly, copies to a cached .ogg file,
     * and returns the file, completely bypassing disk scanning.
     */
    /**
     * Inspects notification extras for direct audio streams in MessagingStyle messages.
     * Only inspects the latest message bundle and ignores historical messages.
     */
    fun extractAudioFromNotification(sbn: StatusBarNotification): File? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        try {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            return extractAudioFromMessagesBundle(messages)
        } catch (e: Throwable) {
            Log.w(TAG, "Error inspecting MessagingStyle audio messages: ${e.message}")
        }
        return null
    }

    /**
     * 2-stage micro-polling (75ms, 150ms) to allow WhatsApp background service
     * to populate and attach the audio URI before escalating to disk fallback.
     */
    suspend fun extractAudioWithMicroPolling(
        sbn: StatusBarNotification,
        listenerService: NotificationListenerService? = null,
        activeNotificationProvider: ((String) -> StatusBarNotification?)? = null
    ): File? = withContext(ioDispatcher) {
        var currentSbn = sbn
        val immediateFile = extractAudioFromNotification(currentSbn)
        if (immediateFile != null) return@withContext immediateFile

        val delays = listOf(75L, 150L)
        for (delayMs in delays) {
            delay(delayMs)
            val refreshedSbn = activeNotificationProvider?.invoke(sbn.key)
                ?: runCatching { listenerService?.activeNotifications?.firstOrNull { it.key == sbn.key } }.getOrNull()
            if (refreshedSbn != null) {
                currentSbn = refreshedSbn
            }
            val retryFile = extractAudioFromNotification(currentSbn)
            if (retryFile != null) {
                Log.i(TAG, "🎙️ Audio URI caught on micro-retry delay ($delayMs ms)")
                return@withContext retryFile
            }
        }
        return@withContext null
    }

    internal fun extractAudioFromMessagesBundle(messagesArray: Array<*>?): File? {
        if (messagesArray == null || messagesArray.isEmpty()) return null
        val bundle = messagesArray.lastOrNull() as? Bundle ?: return null
        val mimeType = bundle.getString("type")
            ?: bundle.getString("data_mime_type")
            ?: bundle.getCharSequence("type")?.toString()
            ?: bundle.getCharSequence("data_mime_type")?.toString()

        val uri = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("uri", Uri::class.java)
                    ?: bundle.getParcelable("data_uri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                (bundle.getParcelable<Uri>("uri") ?: bundle.getParcelable<Uri>("data_uri"))
            }
        }.getOrNull() ?: (bundle.get("uri") as? Uri) ?: (bundle.get("data_uri") as? Uri)
          ?: bundle.getString("uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
          ?: bundle.getString("data_uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }

        if (uri != null) {
            val isAudio = mimeType?.startsWith("audio/", ignoreCase = true) == true ||
                    mimeType?.contains("ogg", ignoreCase = true) == true ||
                    mimeType?.contains("opus", ignoreCase = true) == true ||
                    mimeType?.contains("audio", ignoreCase = true) == true ||
                    uri.toString().contains("audio", ignoreCase = true) ||
                    uri.toString().contains("opus", ignoreCase = true) ||
                    uri.toString().contains("voice", ignoreCase = true)

            if (isAudio) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    stream?.use { input ->
                        // Crucial Filename Extension Invariant: always cache with .ogg extension
                        val cachedFile = File(context.cacheDir, "incoming_voice_${System.currentTimeMillis()}_${System.nanoTime()}.ogg")
                        FileOutputStream(cachedFile).use { out ->
                            input.copyTo(out)
                            out.flush()
                        }
                        if (cachedFile.exists() && cachedFile.length() > 0L) {
                            val logMsg = "🎙️ Successfully extracted audio file from MessagingStyle URI: $uri (size: ${cachedFile.length()} bytes)"
                            Log.i(TAG, logMsg)
                            PersonaLogger.i(TAG, logMsg)
                            return cachedFile
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to open/copy stream from MessagingStyle audio URI $uri: ${e.message}")
                }
            }
        }
        return null
    }

    internal fun extractAudioDurationMs(file: File): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    internal var audioDurationProvider: (File) -> Long? = { extractAudioDurationMs(it) }

    /**
     * Captures the most recently written .opus or .ogg audio file in WhatsApp Voice Notes directory.
     * Enforces Duration-Aware Proximity Anti-Collision against concurrent customer turns.
     */
    fun captureLatestVoiceNote(
        sourceClass: NotificationSourceClass,
        postTime: Long = System.currentTimeMillis(),
        expectedDurationSeconds: Int? = null
    ): File? {
        val baseDirs = getWhatsAppVoiceNoteDirectories(sourceClass)
        val now = if (postTime > 0) postTime else System.currentTimeMillis()
        evictStaleClaimedVoiceFiles(now)

        val expectedDurationMs = expectedDurationSeconds?.let { it * 1000L }

        for (baseDir in baseDirs) {
            if (!baseDir.exists() || !baseDir.isDirectory) continue

            val subDirs = baseDir.listFiles { f -> f.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val allSearchDirs = subDirs + listOf(baseDir)

            for (dir in allSearchDirs) {
                val opusFiles = dir.listFiles { f ->
                    f.isFile && (
                        f.extension.equals("opus", ignoreCase = true) ||
                        f.extension.equals("ogg", ignoreCase = true) ||
                        f.extension.equals("m4a", ignoreCase = true) ||
                        f.extension.equals("mp3", ignoreCase = true)
                    )
                } ?: continue

                // Grab candidates modified within 15 seconds of postTime and not yet claimed
                val candidates = opusFiles
                    .filter {
                        val delta = Math.abs(it.lastModified() - now)
                        delta <= 15_000L && !claimedVoiceFiles.containsKey(it.absolutePath)
                    }

                if (candidates.isEmpty()) continue

                // Duration + Proximity Anti-Collision:
                var targetCandidate: File? = null

                if (expectedDurationMs != null) {
                    val matchingCandidates = candidates.filter { candidate ->
                        val durationMs = audioDurationProvider(candidate)
                        durationMs != null && Math.abs(durationMs - expectedDurationMs) <= 1000L
                    }

                    if (matchingCandidates.isNotEmpty()) {
                        // Break ties by selecting file closest to postTime
                        targetCandidate = matchingCandidates.minByOrNull { Math.abs(it.lastModified() - now) }
                        if (targetCandidate != null) {
                            val durationMs = audioDurationProvider(targetCandidate)
                            val logMsg = "🎯 Duration match found for voice note: ${targetCandidate.absolutePath} (expected: ${expectedDurationMs}ms, actual: ${durationMs}ms, delta: ${Math.abs(targetCandidate.lastModified() - now)}ms)"
                            Log.i(TAG, logMsg)
                            PersonaLogger.i(TAG, logMsg)
                        }
                    }
                }

                // If duration matching didn't yield a match or duration was not provided, fallback to the file closest to postTime
                if (targetCandidate == null) {
                    targetCandidate = candidates.minByOrNull { Math.abs(it.lastModified() - now) }
                }

                if (targetCandidate != null) {
                    val claimed = claimedVoiceFiles.putIfAbsent(targetCandidate.absolutePath, now) == null
                    if (claimed) {
                        val logMsg = "🎙️ Successfully captured voice note file: ${targetCandidate.absolutePath}, transcribing..."
                        Log.i(TAG, logMsg)
                        PersonaLogger.i(TAG, logMsg)
                        return targetCandidate
                    } else {
                        Log.w(TAG, "Voice note file collision avoided: ${targetCandidate.absolutePath} was already claimed by another turn")
                    }
                }
            }
        }
        return null
    }

    /**
     * Transcribes an audio voice note via Groq Audio API using `whisper-large-v3-turbo`.
     * Request: Multipart POST with the audio file (.ogg upload format) and `language: "ar"`.
     */
    suspend fun transcribeVoiceNote(audioFile: File): String? {
        try {
            val primaryKey = groqPrimaryApiKey.trim()
            val fallbackKey = groqFallbackApiKey.trim()

            if (primaryKey.isNotBlank()) {
                val result = callGroqWhisper(audioFile, primaryKey)
                if (!result.isNullOrBlank()) {
                    val logMsg = "🎙️ Whisper transcription completed: '$result'"
                    Log.i(TAG, logMsg)
                    PersonaLogger.i(TAG, logMsg)
                    return result
                }
            }

            if (fallbackKey.isNotBlank() && fallbackKey != primaryKey) {
                Log.w(TAG, "Primary Groq key failed for Whisper. Falling back to GROQ_API_KEY_V2...")
                val fallbackResult = callGroqWhisper(audioFile, fallbackKey)
                if (!fallbackResult.isNullOrBlank()) {
                    val logMsg = "🎙️ Whisper transcription completed via fallback: '$fallbackResult'"
                    Log.i(TAG, logMsg)
                    PersonaLogger.i(TAG, logMsg)
                    return fallbackResult
                }
            }

            Log.w(TAG, "Groq Whisper transcription failed on all keys for ${audioFile.name}")
            return null
        } finally {
            if (audioFile.name.startsWith("incoming_voice_") && audioFile.name.endsWith(".ogg")) {
                runCatching {
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun callGroqWhisper(audioFile: File, apiKey: String): String? {
        return try {
            if (!audioFile.exists() || audioFile.length() <= 500L) {
                delay(150L)
            }
            if (!audioFile.exists() || audioFile.length() <= 500L) {
                Log.w(TAG, "Audio file does not exist or has insufficient bytes (< 500B): ${audioFile.absolutePath} (${if (audioFile.exists()) audioFile.length() else 0} bytes)")
                return null
            }
            val audioBytes = audioFile.readBytes()
            val uploadFileName = "${audioFile.nameWithoutExtension}.ogg"

            val response = httpClient.submitFormWithBinaryData(
                url = "https://api.groq.com/openai/v1/audio/transcriptions",
                formData = formData {
                    append("model", "whisper-large-v3-turbo")
                    append("response_format", "json")
                    append("language", "ar")
                    append("file", audioBytes, Headers.build {
                        append(HttpHeaders.ContentType, "audio/ogg")
                        append(HttpHeaders.ContentDisposition, "filename=\"$uploadFileName\"")
                    })
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val parsed = jsonParser.parseToJsonElement(responseText).jsonObject
                parsed["text"]?.jsonPrimitive?.content?.trim()
            } else {
                val status = response.status.value
                val errorBody = response.bodyAsText()
                Log.w(TAG, "Groq Audio API returned error code $status: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Groq Whisper call: ${e.message}", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers & Indicator Detection
    // ──────────────────────────────────────────────────────────────────────────

    fun normalizeEasternArabicNumerals(input: String): String {
        val eastern = "٠١٢٣٤٥٦٧٨٩"
        val western = "0123456789"
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = eastern.indexOf(ch)
            if (idx >= 0) {
                sb.append(western[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun extractDurationSeconds(text: String): Int? {
        val normalized = normalizeEasternArabicNumerals(text)
        val regex = Regex("""(?:\(|\b)(\d{1,2}):(\d{2})(?:\)|\b)""")
        val match = regex.find(normalized) ?: return null
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val seconds = match.groupValues[2].toIntOrNull() ?: return null
        return minutes * 60 + seconds
    }

    fun isVoiceMessageIndicator(text: String): Boolean {
        val voiceEmojis = listOf("🎤", "🎙️", "🎵")
        if (voiceEmojis.any { text.contains(it) }) return true
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
            .removePrefix("محولة:")
            .removePrefix("محوله:")
            .removePrefix("forwarded:")
            .removePrefix("forwarded")
            .trim()

        return lower == "voice message" ||
               lower.startsWith("voice message") ||
               lower == "voice note" ||
               lower.startsWith("voice note") ||
               lower == "رسالة صوتية" ||
               lower.startsWith("رسالة صوتية") ||
               lower == "مقطع صوتي" ||
               lower.startsWith("مقطع صوتي") ||
               lower == "تسجيل صوتي" ||
               lower.startsWith("تسجيل صوتي") ||
               lower == "audio" ||
               lower.startsWith("audio") ||
               lower.contains("voice message") ||
               lower.contains("voice note") ||
               lower.contains("رسالة صوتية") ||
               lower.contains("مقطع صوتي") ||
               lower.contains("تسجيل صوتي")
    }

    fun isImageIndicator(text: String): Boolean {
        val imageEmojis = listOf("📷", "📸")
        if (imageEmojis.any { text.contains(it) }) return true
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        return lower == "photo" ||
               lower == "صورة" ||
               lower.startsWith("sent a photo") ||
               lower.contains("sent a photo")
    }

    fun hasAudioIndicator(sbn: StatusBarNotification, currentText: String? = null): Boolean {
        val textToCheck = currentText
            ?: sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        if (isVoiceMessageIndicator(textToCheck)) return true

        val messagesArray = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messagesArray != null && messagesArray.isNotEmpty()) {
            val lastMsgBundle = messagesArray.lastOrNull() as? Bundle
            if (lastMsgBundle != null) {
                val mimeType = lastMsgBundle.getString("type")
                    ?: lastMsgBundle.getString("data_mime_type")
                    ?: lastMsgBundle.getCharSequence("type")?.toString()
                    ?: lastMsgBundle.getCharSequence("data_mime_type")?.toString()

                val uri = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lastMsgBundle.getParcelable("uri", Uri::class.java)
                            ?: lastMsgBundle.getParcelable("data_uri", Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        (lastMsgBundle.getParcelable<Uri>("uri") ?: lastMsgBundle.getParcelable<Uri>("data_uri"))
                    }
                }.getOrNull() ?: (lastMsgBundle.get("uri") as? Uri)
                  ?: (lastMsgBundle.get("data_uri") as? Uri)
                  ?: lastMsgBundle.getString("uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
                  ?: lastMsgBundle.getString("data_uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }

                if (mimeType?.startsWith("audio/", ignoreCase = true) == true ||
                    mimeType?.contains("ogg", ignoreCase = true) == true ||
                    mimeType?.contains("opus", ignoreCase = true) == true ||
                    mimeType?.contains("audio", ignoreCase = true) == true ||
                    uri?.toString()?.contains("audio", ignoreCase = true) == true ||
                    uri?.toString()?.contains("opus", ignoreCase = true) == true ||
                    uri?.toString()?.contains("voice", ignoreCase = true) == true) {
                    return true
                }
            }
        }
        return false
    }

    fun hasNotificationImage(sbn: StatusBarNotification, currentText: String? = null): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false

        // 1. Check if the incoming message text has media indicators
        val textToCheck = currentText
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        // If this notification is a voice message, it is strictly NOT an image!
        if (isVoiceMessageIndicator(textToCheck) || hasAudioIndicator(sbn, textToCheck)) {
            return false
        }

        if (isImageIndicator(textToCheck)) return true

        // 2. In MessagingStyle notifications, check if the latest incoming message has an image URI/type
        val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messagesArray != null && messagesArray.isNotEmpty()) {
            val lastMsgBundle = messagesArray.lastOrNull() as? Bundle
            if (lastMsgBundle != null) {
                val mimeType = lastMsgBundle.getString("type")
                    ?: lastMsgBundle.getString("data_mime_type")
                    ?: lastMsgBundle.getCharSequence("type")?.toString()
                    ?: lastMsgBundle.getCharSequence("data_mime_type")?.toString()

                val uri = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lastMsgBundle.getParcelable("uri", Uri::class.java)
                            ?: lastMsgBundle.getParcelable("data_uri", Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        (lastMsgBundle.getParcelable<Uri>("uri") ?: lastMsgBundle.getParcelable<Uri>("data_uri"))
                    }
                }.getOrNull() ?: (lastMsgBundle.get("uri") as? Uri)
                  ?: (lastMsgBundle.get("data_uri") as? Uri)
                  ?: lastMsgBundle.getString("uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
                  ?: lastMsgBundle.getString("data_mime_type")?.let { runCatching { Uri.parse(it) }.getOrNull() }

                val lastMsgText = lastMsgBundle.getCharSequence("text")?.toString() ?: ""
                if (isImageIndicator(lastMsgText)) return true

                if (uri != null || mimeType?.startsWith("image/", ignoreCase = true) == true) {
                    return true
                }
            }
            // An active WhatsApp conversation thread retains historic notification extras.
            // If the latest message in EXTRA_MESSAGES is pure text without indicators or URI,
            // this turn is NOT an image!
            return false
        }

        // 3. Fallback for non-MessagingStyle notifications: inspect direct picture slots
        return extras.containsKey(Notification.EXTRA_PICTURE) ||
               extras.containsKey(Notification.EXTRA_PICTURE_ICON) ||
               extras.containsKey("android.pictureIcon") ||
               extras.containsKey(Notification.EXTRA_LARGE_ICON_BIG) ||
               extras.containsKey("android.largeIcon.big")
    }

    internal var imageDirectoriesOverride: List<File>? = null
    internal var voiceNoteDirectoriesOverride: List<File>? = null

    private fun getWhatsAppImageDirectories(sourceClass: NotificationSourceClass): List<File> {
        imageDirectoriesOverride?.let { return it }
        val dirs = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory()

        when (sourceClass) {
            NotificationSourceClass.BUSINESS -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Images"))
            }
            NotificationSourceClass.MAIN_APP -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Images"))
            }
            else -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Images"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Images"))
            }
        }
        return dirs
    }

    private fun getWhatsAppVoiceNoteDirectories(sourceClass: NotificationSourceClass): List<File> {
        voiceNoteDirectoriesOverride?.let { return it }
        val dirs = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory()

        when (sourceClass) {
            NotificationSourceClass.BUSINESS -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Audio"))
            }
            NotificationSourceClass.MAIN_APP -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Audio"))
            }
            else -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Audio"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Audio"))
            }
        }
        return dirs
    }
}
