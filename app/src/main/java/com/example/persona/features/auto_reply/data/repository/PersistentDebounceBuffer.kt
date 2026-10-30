package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedNotificationDto(
    val title: String,
    val text: String,
    val postTime: Long,
    val notificationKey: String,
    val sourceClass: String,
    val mediaFilePath: String? = null,
    val isVoiceTranscriptionFailed: Boolean = false,
    val isPrePersisted: Boolean = false,
    val burstMessageIds: List<Long> = emptyList()
) {
    fun toDomain(): WhatsAppNotification = WhatsAppNotification(
        title = title,
        text = text,
        postTime = postTime,
        notificationKey = notificationKey,
        extraMessages = emptyList(),
        sourceClass = runCatching { NotificationSourceClass.valueOf(sourceClass) }.getOrDefault(NotificationSourceClass.UNKNOWN),
        mediaFilePath = mediaFilePath,
        isVoiceTranscriptionFailed = isVoiceTranscriptionFailed,
        isPrePersisted = isPrePersisted,
        burstMessageIds = burstMessageIds
    )
}

fun WhatsAppNotification.toDto(): PersistedNotificationDto = PersistedNotificationDto(
    title = title,
    text = text,
    postTime = postTime,
    notificationKey = notificationKey,
    sourceClass = sourceClass.name,
    mediaFilePath = mediaFilePath,
    isVoiceTranscriptionFailed = isVoiceTranscriptionFailed,
    isPrePersisted = isPrePersisted,
    burstMessageIds = burstMessageIds
)

@Serializable
data class PersistedDebounceTurn(
    val contactKey: String,
    val notifications: List<PersistedNotificationDto>,
    val insertedIds: List<Long>,
    val targetDeadlineMs: Long
)

/**
 * File-backed persistence for active debounce turns to survive Android OS process death.
 */
@Singleton
class PersistentDebounceBuffer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PersistentDebounceBuffer"
        private const val FILE_NAME = "inbound_debounce_queue.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    private val lock = Any()

    fun persistTurn(
        contactKey: String,
        notifications: List<WhatsAppNotification>,
        insertedIds: List<Long>,
        targetDeadlineMs: Long
    ) {
        synchronized(lock) {
            try {
                val current = loadAllInternal().toMutableMap()
                current[contactKey] = PersistedDebounceTurn(
                    contactKey = contactKey,
                    notifications = notifications.map { it.toDto() },
                    insertedIds = insertedIds,
                    targetDeadlineMs = targetDeadlineMs
                )
                saveInternal(current)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist debounce turn for $contactKey", e)
            }
        }
    }

    fun removeTurn(contactKey: String) {
        synchronized(lock) {
            try {
                val current = loadAllInternal().toMutableMap()
                if (current.remove(contactKey) != null) {
                    saveInternal(current)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove debounce turn for $contactKey", e)
            }
            Unit
        }
    }

    fun recoverAllTurns(): List<PersistedDebounceTurn> {
        return synchronized(lock) {
            try {
                val map = loadAllInternal()
                if (map.isNotEmpty()) {
                    Log.i(TAG, "Recovering ${map.size} pending debounce turns after process restart")
                    if (file.exists()) {
                        file.delete()
                    }
                    val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
                map.values.toList()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recover turns from disk", e)
                emptyList()
            }
        }
    }

    fun recoverPendingTurns(): Map<String, WhatsAppNotification> {
        val turns = recoverAllTurns()
        return turns.associate { turn ->
            val domainNotifications = turn.notifications.map { it.toDomain() }
            val coalesced = if (domainNotifications.size == 1) {
                domainNotifications[0].copy(
                    isPrePersisted = true,
                    burstMessageIds = turn.insertedIds
                )
            } else {
                val latest = domainNotifications.last()
                val combinedText = domainNotifications.map { it.text.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
                val media = domainNotifications.mapNotNull { it.mediaFilePath }.firstOrNull { it.isNotBlank() }
                latest.copy(
                    text = combinedText,
                    mediaFilePath = media,
                    isPrePersisted = true,
                    burstMessageIds = turn.insertedIds
                )
            }
            turn.contactKey to coalesced
        }
    }

    private fun loadAllInternal(): Map<String, PersistedDebounceTurn> {
        val target = if (file.exists()) {
            file
        } else {
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
            if (tempFile.exists()) tempFile else return emptyMap()
        }
        return try {
            val content = target.readText()
            if (content.isBlank()) return emptyMap()
            json.decodeFromString<Map<String, PersistedDebounceTurn>>(content)
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt debounce queue file on disk, resetting", e)
            emptyMap()
        }
    }

    private fun saveInternal(map: Map<String, PersistedDebounceTurn>) {
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        if (map.isEmpty()) {
            if (file.exists()) file.delete()
            if (tempFile.exists()) tempFile.delete()
            return
        }
        val encoded = json.encodeToString(map)
        try {
            val bytes = encoded.toByteArray(Charsets.UTF_8)
            java.io.FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {
                    // Ignore sync error on emulated file descriptors
                }
            }
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to atomically save debounce queue", e)
        }
    }
}
