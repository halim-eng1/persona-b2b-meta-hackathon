package com.example.persona.features.auto_reply.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.persona.core.logging.PersonaLogTags
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImagePayloadPreprocessor @Inject constructor() {

    companion object {
        private const val TAG = PersonaLogTags.LLM
        val AUDIO_EXTENSIONS = setOf("opus", "ogg", "mp3", "m4a", "wav", "aac", "m4p", "amr")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    // Thread-safe in-memory LRU cache keyed by filePath:lastModified:length
    private val base64Cache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > 50
            }
        }
    )

    fun compressAndScaleImage(
        filePath: String,
        maxDimension: Int = 1024,
        quality: Int = 80
    ): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null

        val ext = file.extension.lowercase()
        if (AUDIO_EXTENSIONS.contains(ext) || !IMAGE_EXTENSIONS.contains(ext)) {
            Log.d(TAG, "compressAndScaleImage: Skipping non-image file with extension .$ext ($filePath)")
            return null
        }

        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight

            if (origWidth > 0 && origHeight > 0) {
                var sampleSize = 1
                val maxDim = maxOf(origWidth, origHeight)
                while ((maxDim / (sampleSize * 2)) >= maxDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                if (decoded != null) {
                    val w = decoded.width
                    val h = decoded.height
                    val finalBitmap = if (w > maxDimension || h > maxDimension) {
                        val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
                        val targetW = (w * ratio).toInt().coerceAtLeast(1)
                        val targetH = (h * ratio).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                        if (scaled != decoded) {
                            decoded.recycle()
                        }
                        scaled
                    } else {
                        decoded
                    }

                    try {
                        val stream = ByteArrayOutputStream()
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                        val compressed = stream.toByteArray()
                        Log.d(TAG, "Image scaled and compressed to ${compressed.size} bytes (target quality=$quality)")
                        compressed
                    } finally {
                        finalBitmap.recycle()
                    }
                } else {
                    file.readBytes()
                }
            } else {
                file.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image decoding/scaling failed for $filePath, falling back to raw file: ${e.message}")
            try { file.readBytes() } catch (_: Exception) { null }
        }
    }

    fun resolveImageUri(filePathOrUri: String?): String? {
        if (filePathOrUri.isNullOrBlank()) return null
        if (filePathOrUri.startsWith("data:image/") || filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://")) {
            return filePathOrUri
        }
        val file = File(filePathOrUri)
        val ext = file.extension.lowercase()
        if (AUDIO_EXTENSIONS.contains(ext) || !IMAGE_EXTENSIONS.contains(ext)) {
            Log.d(TAG, "resolveImageUri: Ignoring non-image media file path: $filePathOrUri")
            return null
        }
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        base64Cache[cacheKey]?.let { return it }

        return try {
            val bytes = compressAndScaleImage(filePathOrUri, maxDimension = 1024, quality = 80)
            if (bytes != null && bytes.isNotEmpty()) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val uri = "data:image/jpeg;base64,$base64"
                base64Cache[cacheKey] = uri
                uri
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode media image to Base64: ${e.message}")
            null
        }
    }
}
