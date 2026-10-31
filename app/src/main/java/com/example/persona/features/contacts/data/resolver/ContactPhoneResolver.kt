package com.example.persona.features.contacts.data.resolver

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.persona.features.orders.domain.EgyptianOrderUtils

object ContactPhoneResolver {

    private const val TAG = "ContactPhoneResolver"

    /**
     * Attempts to resolve a phone number from an Android contact URI (such as Person.uri in notifications).
     * Supports both lookup URIs (content://com.android.contacts/contacts/lookup/...) and direct content URIs.
     */
    fun resolvePhoneNumberFromUri(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            resolvePhoneNumberFromUri(context, Uri.parse(uriString))
        }.getOrNull()
    }

    fun resolvePhoneNumberFromUri(context: Context, contactUri: Uri): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_CONTACTS permission not granted, attempting ContentResolver query with caller URI grant")
        }

        return runCatching {
            // Strategy 1: Direct DATA content directory on the contact lookup URI
            val phoneUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.Data.MIMETYPE} = ?"
            val selectionArgs = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)

            context.contentResolver.query(phoneUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val rawNumber = cursor.getString(0)
                    if (!rawNumber.isNullOrBlank()) {
                        val normalized = normalizePhoneNumber(rawNumber)
                        if (!normalized.isNullOrBlank()) {
                            Log.d(TAG, "Resolved phone via Strategy 1 (Data dir): raw='$rawNumber' -> normalized='$normalized'")
                            return@runCatching normalized
                        }
                    }
                }
            }

            // Strategy 2: Extract Contact ID and query CommonDataKinds.Phone.CONTENT_URI
            val lastSegment = contactUri.lastPathSegment
            val contactId = if (lastSegment?.toLongOrNull() != null) {
                lastSegment
            } else {
                context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }

            if (contactId != null) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val rawNumber = cursor.getString(0)
                        if (!rawNumber.isNullOrBlank()) {
                            val normalized = normalizePhoneNumber(rawNumber)
                            if (!normalized.isNullOrBlank()) {
                                Log.d(TAG, "Resolved phone via Strategy 2 (Phone URI): raw='$rawNumber' -> normalized='$normalized'")
                                return@runCatching normalized
                            }
                        }
                    }
                }
            }

            null
        }.onFailure { e ->
            Log.w(TAG, "Failed to resolve phone from contactUri $contactUri: ${e.message}")
        }.getOrNull()
    }

    /**
     * Normalizes a phone number to local Egyptian 11-digit format (01xxxxxxxxx)
     * or standard clean numeric format.
     */
    fun normalizePhoneNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val egyptian = EgyptianOrderUtils.extractEgyptianMobilePhone(raw)
        if (egyptian != null) return egyptian
        val digits = raw.filter { it.isDigit() || it == '+' }
        return digits.ifBlank { null }
    }

    /**
     * Checks if a title string is a phone number (e.g., "+20 10 09251246" or "0100 925 1246").
     */
    fun extractPhoneFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        return EgyptianOrderUtils.extractEgyptianMobilePhone(title)
    }
}
