package com.example.persona.features.auto_reply.data.diagnostics

// ─────────────────────────────────────────────────────────────────────────────
// KEEP THIS FILE.
//
// This diagnostics infrastructure exists for:
//   * WhatsApp clone detection
//   * UserHandle investigation
//   * OEM compatibility debugging
//   * notification forensics
//   * regression debugging
//
// DO NOT DELETE.
//
// Enable using:
//   DebugFlags.ENABLE_NOTIFICATION_DIAGNOSTICS = true
//
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.example.persona.core.debug.DebugFlags
import com.example.persona.core.logging.PersonaLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classification of a notification's source — used to distinguish between
 * WhatsApp Main, WhatsApp Business, and WhatsApp Clone / Dual App / Work Profile.
 */
enum class NotificationSourceClass {
    MAIN_APP,
    BUSINESS,
    CLONE,
    UNKNOWN
}

/**
 * Snapshot of all source-related metadata extracted from a [StatusBarNotification].
 *
 * Every field is safe to inspect: nullable fields are null when the system did
 * not provide them (e.g. null extras, missing channelId).
 */
data class NotificationSourceDiagnostic(
    val packageName: String,
    val uid: Int,
    val userIdentifier: Int,
    val userHashCode: Int,
    val notificationKey: String,
    val notificationId: Int,
    val tag: String?,
    val channelId: String?,
    val groupKey: String?,
    val sortKey: String?,
    val category: String?,
    val visibility: Int,
    val priority: Int,
    val postTime: Long,
    val captureTimestamp: Long,
    val contactTitle: String?,
    val extractedMessage: String?,
    val appLabel: String?,
    val classification: NotificationSourceClass
)

/**
 * Diagnostics layer for the NotificationListenerService pipeline.
 *
 * Responsibilities:
 *  - Extract all source-related fields from each incoming [StatusBarNotification]
 *  - Classify the source (MAIN_APP / BUSINESS / CLONE / UNKNOWN)
 *  - Emit a structured "NLS_DIAG" log entry on a background coroutine dispatcher
 *
 * This is diagnostics-only. It never modifies the notification, never throws,
 * and never blocks the caller. The NLS thread continues immediately after
 * scheduling the log emission.
 */
@Singleton
class NotificationSourceDiagnostics @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        const val TAG = "NLS_DIAG"

        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        private const val NULL_MARKER = "<null>"

        /**
         * Dedicated, app-lifetime scope used only for diagnostics logging.
         * A SupervisorJob ensures a single failed emission never cancels others,
         * and Dispatchers.IO is appropriate because the work is small and the
         * call site is a high-frequency Android service callback.
         */
        private val diagnosticsScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

        /**
         * Pure function: classify a package + user pair.
         * Exposed (internal) for unit tests.
         */
        internal fun classify(
            packageName: String?,
            userIdentifier: Int
        ): NotificationSourceClass = when {
            packageName == WHATSAPP_BUSINESS_PACKAGE -> NotificationSourceClass.BUSINESS
            packageName == WHATSAPP_PACKAGE && userIdentifier == 0 -> NotificationSourceClass.MAIN_APP
            packageName == WHATSAPP_PACKAGE && userIdentifier != 0 -> NotificationSourceClass.CLONE
            else -> NotificationSourceClass.UNKNOWN
        }
    }

    /**
     * Capture diagnostic information for the given notification and emit it
     * on a background dispatcher.
     *
     * Safe to call on the NLS main thread. Never throws. Never blocks.
     */
    fun captureAndLog(sbn: StatusBarNotification?) {
        if (!DebugFlags.ENABLE_NOTIFICATION_DIAGNOSTICS) return

        if (sbn == null) {
            // Defensive: should not happen, but guarantee no NPE ever escapes.
            diagnosticsScope.launch {
                runCatching { PersonaLogger.w(TAG, "NLS_DIAG: sbn was null") }
            }
            return
        }

        val diagnostic = runCatching { build(sbn) }.getOrElse { error ->
            diagnosticsScope.launch {
                runCatching {
                    PersonaLogger.w(
                        TAG,
                        "NLS_DIAG:\n" +
                            "package=${sbn.packageName ?: NULL_MARKER}\n" +
                            "key=${sbn.key ?: NULL_MARKER}\n" +
                            "extraction_error=${error.javaClass.simpleName}: ${error.message ?: NULL_MARKER}"
                    )
                }
            }
            return
        }

        diagnosticsScope.launch {
            runCatching { emit(diagnostic) }
        }
    }

    private fun build(sbn: StatusBarNotification): NotificationSourceDiagnostic {
        // Defensive read of fields that the framework may occasionally null
        // out or that may throw SecurityException on some OEM ROMs.
        // We use try/catch with explicit return types to remain compatible with
        // the project-wide "isReturnDefaultValues = true" stub mode, which can
        // otherwise erase certain framework property return types.
        val notification: Notification? = safeGet { sbn.notification }
        val extras: android.os.Bundle? = safeGet { notification?.extras }

        val title: String? = safeGet { extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
        val text: String? = safeGet { extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() }
        val channelId: String? = safeGet { notification?.channelId }
        val category: String? = safeGet { notification?.category }
        val visibility: Int = safeGetInt { notification?.visibility ?: -1 }
        val priority: Int = safeGetInt { notification?.priority ?: -1 }
        val groupKey: String? = safeGet { sbn.groupKey }
        val sortKey: String? = safeGet {
            sbn.javaClass.getMethod("getSortKey").invoke(sbn) as? String
        }

        val userHandle: android.os.UserHandle? = safeGet { sbn.user }
        val userIdentifier: Int = safeGetInt { reflectIdentifierOrZero(userHandle) }
        val userHash: Int = safeGetInt { userHandle?.hashCode() ?: 0 }

        return NotificationSourceDiagnostic(
            packageName = sbn.packageName ?: NULL_MARKER,
            uid = safeGetInt { sbn.uid },
            userIdentifier = userIdentifier,
            userHashCode = userHash,
            notificationKey = sbn.key ?: NULL_MARKER,
            notificationId = safeGetInt { sbn.id },
            tag = sbn.tag,
            channelId = channelId,
            groupKey = groupKey,
            sortKey = sortKey,
            category = category,
            visibility = visibility,
            priority = priority,
            postTime = sbn.postTime,
            captureTimestamp = System.currentTimeMillis(),
            contactTitle = title,
            extractedMessage = text,
            appLabel = resolveAppLabel(sbn.packageName),
            classification = classify(sbn.packageName, userIdentifier)
        )
    }

    private inline fun <T> safeGet(block: () -> T?): T? =
        try {
            block()
        } catch (_: Throwable) {
            null
        }

    private inline fun safeGetInt(block: () -> Int): Int =
        try {
            block()
        } catch (_: Throwable) {
            -1
        }

    /**
     * Reflectively reads `UserHandle.getIdentifier()` because the property
     * is not visible at compile time under `isReturnDefaultValues = true`.
     * Returns 0 on any failure path.
     */
    private fun reflectIdentifierOrZero(handle: android.os.UserHandle?): Int {
        if (handle == null) return 0
        return try {
            val raw = handle.javaClass.getMethod("getIdentifier").invoke(handle)
                ?: return 0
            (raw as? Int) ?: raw.toString().toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun resolveAppLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull()
    }

    private fun emit(d: NotificationSourceDiagnostic) {
        val sb = StringBuilder(512)
        sb.append("NLS_DIAG:\n")
        sb.append("package=").append(d.packageName).append('\n')
        sb.append("uid=").append(d.uid).append('\n')
        sb.append("user=").append(d.userIdentifier).append('\n')
        sb.append("userHash=").append(d.userHashCode).append('\n')
        sb.append("key=").append(d.notificationKey).append('\n')
        sb.append("notificationId=").append(d.notificationId).append('\n')
        sb.append("tag=").append(d.tag ?: NULL_MARKER).append('\n')
        sb.append("channel=").append(d.channelId ?: NULL_MARKER).append('\n')
        sb.append("group=").append(d.groupKey ?: NULL_MARKER).append('\n')
        sb.append("sortKey=").append(d.sortKey ?: NULL_MARKER).append('\n')
        sb.append("category=").append(d.category ?: NULL_MARKER).append('\n')
        sb.append("visibility=").append(d.visibility).append('\n')
        sb.append("priority=").append(d.priority).append('\n')
        sb.append("postTime=").append(d.postTime).append('\n')
        sb.append("captureTime=").append(d.captureTimestamp).append('\n')
        sb.append("appLabel=").append(d.appLabel ?: NULL_MARKER).append('\n')
        sb.append("title=").append(d.contactTitle ?: NULL_MARKER).append('\n')
        sb.append("message=").append(d.extractedMessage ?: NULL_MARKER).append('\n')
        sb.append("classification=").append(d.classification.name)

        PersonaLogger.d(TAG, sb.toString())
    }
}
