package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppMediaExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient
) {
    internal var groqPrimaryApiKeyOverride: String? = null
    internal var groqFallbackApiKeyOverride: String? = null

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

    /**
     * Extracts an image from the status bar notification extras.
     * If notification extras do not yield a bitmap, falls back to disk monitoring.
     * Saves the resulting bitmap to app cache as high-quality JPEG and returns the file.
     */
    fun extractImage(sbn: StatusBarNotification, sourceClass: NotificationSourceClass): File? {
        val bitmap = extractBitmapFromNotification(sbn)
        if (bitmap != null) {
            val logMsg = "📸 Successfully extracted image bitmap from notification extras! (Width: ${bitmap.width}, Height: ${bitmap.height})"
            Log.i(TAG, logMsg)
            PersonaLogger.i(TAG, logMsg)
            val cachedFile = saveBitmapToCache(bitmap)
            if (cachedFile != null) return cachedFile
        }

        // Fallback: Query disk for the newly received image
        return findLatestImageOnDisk(sourceClass, sbn.postTime)
    }

    /**
     * Direct notification bundle inspection:
     * 1. extras.get(Notification.EXTRA_PICTURE) as? Bitmap
     * 2. extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
     * 3. extras.get(Notification.EXTRA_PICTURE_ICON) / "android.pictureIcon" (Icon -> Bitmap)
     * 4. MessagingStyle extras.getParcelableArray(Notification.EXTRA_MESSAGES) -> data_uri & data_mime_type
     */
    fun extractBitmapFromNotification(sbn: StatusBarNotification): Bitmap? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        // 1. Direct Bitmap from EXTRA_PICTURE
        val directBitmap = try {
            (extras.get(Notification.EXTRA_PICTURE) as? Bitmap)
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
                }
        } catch (_: Throwable) {
            null
        }
        if (directBitmap != null) {
            return directBitmap
        }

        // 2. EXTRA_PICTURE_ICON on Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pictureIcon = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable<Icon>("android.pictureIcon")
                } ?: (extras.get("android.pictureIcon") as? Icon)
            } catch (_: Throwable) {
                null
            }

            if (pictureIcon != null) {
                val iconBmp = iconToBitmap(pictureIcon)
                if (iconBmp != null) return iconBmp
            }
        }

        // 3. MessagingStyle EXTRA_MESSAGES data_uri
        try {
            val parcelableMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (parcelableMessages != null && parcelableMessages.isNotEmpty()) {
                for (i in parcelableMessages.indices.reversed()) {
                    val bundle = parcelableMessages[i] as? Bundle ?: continue
                    val mimeType = bundle.getString("data_mime_type")
                        ?: bundle.getCharSequence("data_mime_type")?.toString()
                    if (mimeType?.startsWith("image/", ignoreCase = true) == true) {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            bundle.getParcelable("data_uri", Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            bundle.getParcelable<Uri>("data_uri")
                        } ?: (bundle.get("data_uri") as? Uri)

                        if (uri != null) {
                            val stream = context.contentResolver.openInputStream(uri)
                            stream?.use {
                                val decoded = BitmapFactory.decodeStream(it)
                                if (decoded != null) return decoded
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to decode image from MessagingStyle URI: ${e.message}")
        }

        return null
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
            val file = File(context.cacheDir, "incoming_media_${System.currentTimeMillis()}.jpg")
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
     * within 5-10 seconds of notification postTime.
     */
    fun findLatestImageOnDisk(
        sourceClass: NotificationSourceClass,
        postTime: Long = System.currentTimeMillis()
    ): File? {
        val dirs = getWhatsAppImageDirectories(sourceClass)
        val now = if (postTime > 0) postTime else System.currentTimeMillis()

        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { f ->
                f.isFile && !f.name.startsWith(".") && f.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: continue

            // Filter by last modified timestamp within 5-10 seconds of the notification
            val matchedFile = files
                .filter { Math.abs(it.lastModified() - now) <= 10_000L }
                .maxByOrNull { it.lastModified() }

            if (matchedFile != null) {
                Log.i(TAG, "📸 Successfully recovered image from disk fallback: ${matchedFile.absolutePath}")
                PersonaLogger.i(TAG, "📸 Successfully recovered image from disk fallback: ${matchedFile.absolutePath}")
                return matchedFile
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 2: Voice Note (.opus) Interception & Whisper Transcription
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Captures the most recently written .opus audio file in WhatsApp Voice Notes directory.
     * WhatsApp partitions voice notes into dated subfolders (e.g. 202643/).
     */
    fun captureLatestVoiceNote(
        sourceClass: NotificationSourceClass,
        postTime: Long = System.currentTimeMillis()
    ): File? {
        val baseDirs = getWhatsAppVoiceNoteDirectories(sourceClass)
        val now = if (postTime > 0) postTime else System.currentTimeMillis()

        for (baseDir in baseDirs) {
            if (!baseDir.exists() || !baseDir.isDirectory) continue

            val subDirs = baseDir.listFiles { f -> f.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val allSearchDirs = subDirs + listOf(baseDir)

            for (dir in allSearchDirs) {
                val opusFiles = dir.listFiles { f ->
                    f.isFile && f.extension.equals("opus", ignoreCase = true)
                } ?: continue

                // Grab the most recently written .opus file (modified within 10-15 seconds)
                val candidate = opusFiles
                    .filter { Math.abs(it.lastModified() - now) <= 15_000L }
                    .maxByOrNull { it.lastModified() }

                if (candidate != null) {
                    val logMsg = "🎙️ Successfully captured voice note opus file: ${candidate.absolutePath}, transcribing..."
                    Log.i(TAG, logMsg)
                    PersonaLogger.i(TAG, logMsg)
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Transcribes an .opus voice note via Groq Audio API using `whisper-large-v3-turbo`.
     * Request: Multipart POST with the .opus file and `language: "ar"`.
     */
    suspend fun transcribeVoiceNote(audioFile: File): String? {
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
    }

    private suspend fun callGroqWhisper(audioFile: File, apiKey: String): String? {
        return try {
            val response = httpClient.submitFormWithBinaryData(
                url = "https://api.groq.com/openai/v1/audio/transcriptions",
                formData = formData {
                    append("model", "whisper-large-v3-turbo")
                    append("language", "ar")
                    append("file", audioFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "audio/ogg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${audioFile.name}\"")
                    })
                }
            ) {
                bearerAuth(apiKey)
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

    fun isVoiceMessageIndicator(text: String): Boolean {
        val voiceEmojis = listOf("🎤", "🎙️", "🎵")
        if (voiceEmojis.any { text.contains(it) }) return true
        val lower = text.lowercase()
        return lower.contains("voice message") ||
               lower.contains("voice note") ||
               lower.contains("رسالة صوتية") ||
               lower.contains("مقطع صوتي") ||
               lower.contains("audio")
    }

    fun isImageIndicator(text: String): Boolean {
        val imageEmojis = listOf("📷", "📸")
        if (imageEmojis.any { text.contains(it) }) return true
        val lower = text.lowercase()
        return lower.contains("photo") ||
               lower.contains("صورة") ||
               lower.contains("sent a photo")
    }

    fun hasNotificationImage(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        return extras.containsKey(Notification.EXTRA_PICTURE) ||
               extras.containsKey("android.pictureIcon") ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && extras.containsKey(Notification.EXTRA_PICTURE_ICON)) ||
               extras.containsKey(Notification.EXTRA_MESSAGES)
    }

    private fun getWhatsAppImageDirectories(sourceClass: NotificationSourceClass): List<File> {
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
        val dirs = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory()

        when (sourceClass) {
            NotificationSourceClass.BUSINESS -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Voice Notes"))
            }
            NotificationSourceClass.MAIN_APP -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Voice Notes"))
            }
            else -> {
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
                dirs.add(File(ext, "WhatsApp Business/Media/WhatsApp Business Voice Notes"))
                dirs.add(File(ext, "WhatsApp/Media/WhatsApp Voice Notes"))
            }
        }
        return dirs
    }
}
