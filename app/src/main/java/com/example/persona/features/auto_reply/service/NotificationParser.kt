package com.example.persona.features.auto_reply.service

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.persona.features.contacts.data.resolver.ContactPhoneResolver
import com.example.persona.features.orders.domain.EgyptianOrderUtils

data class ParsedNotificationMetadata(
    val sbnTag: String?,
    val shortcutId: String?,
    val personUri: String?,
    val resolvedPhone: String?
)

object NotificationParser {

    private const val TAG = "NotificationParser"

    fun parseMetadata(
        context: Context,
        sbn: StatusBarNotification,
        title: String
    ): ParsedNotificationMetadata {
        val extras = sbn.notification?.extras
        val tag = sbn.tag
        val shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sbn.notification?.shortcutId
        } else null

        var personUri: String? = null
        var personKey: String? = null
        val subText = extras?.getString("android.subText") ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && extras != null) {
            val parcelableMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (parcelableMessages != null) {
                for (parcelable in parcelableMessages) {
                    if (parcelable is android.os.Bundle) {
                        val person = parcelable.getParcelable<android.app.Person>("sender_person")
                        if (person != null) {
                            if (person.key != null) personKey = person.key
                            if (person.uri != null) personUri = person.uri
                        }
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Parsed notification metadata: sbn.tag=$tag, shortcutId=$shortcutId, subText=$subText, Person.key=$personKey, Person.uri=$personUri"
        )

        // 1. Try resolving directly from Person.uri if present
        var resolvedPhone: String? = null
        if (!personUri.isNullOrBlank() && personUri.startsWith("content://com.android.contacts/")) {
            resolvedPhone = ContactPhoneResolver.resolvePhoneNumberFromUri(context, personUri)
        }

        // 2. If not found, try title as phone number (e.g. "+20 10 09251246" or "0100 925 1246")
        if (resolvedPhone.isNullOrBlank()) {
            resolvedPhone = ContactPhoneResolver.extractPhoneFromTitle(title)
        }

        // 3. If not found, check other tokens (tag, subText, personKey)
        if (resolvedPhone.isNullOrBlank()) {
            val candidates = listOfNotNull(tag, subText, personKey)
            for (candidate in candidates) {
                val candidatePhone = extractPhoneFromToken(candidate)
                if (!candidatePhone.isNullOrBlank()) {
                    resolvedPhone = candidatePhone
                    break
                }
            }
        }

        return ParsedNotificationMetadata(
            sbnTag = tag,
            shortcutId = shortcutId,
            personUri = personUri,
            resolvedPhone = resolvedPhone
        )
    }

    internal fun extractPhoneFromToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        if (token.contains("@lid", ignoreCase = true)) return null
        if (token.startsWith("content:", ignoreCase = true)) return null

        // WhatsApp JID format
        val jidMatch = Regex("""(\d+)@(s\.whatsapp\.net|c\.us)""").find(token)
        if (jidMatch != null) {
            val rawDigits = jidMatch.groupValues[1]
            return EgyptianOrderUtils.extractEgyptianMobilePhone(rawDigits) ?: rawDigits
        }

        // tel: URI
        if (token.startsWith("tel:", ignoreCase = true)) {
            val telPart = token.substring(4)
            return EgyptianOrderUtils.extractEgyptianMobilePhone(telPart)
                ?: telPart.filter { it.isDigit() || it == '+' }
        }

        return EgyptianOrderUtils.extractEgyptianMobilePhone(token)
    }
}
