package com.example.persona.features.contacts.data.resolver

import android.util.Log
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedThreadIdentity(
    val unifiedContactName: String,
    val resolvedPhoneNumber: String?,
    val wasMerged: Boolean,
    val previousContactName: String? = null
)

@Singleton
class ThreadIdentityResolver @Inject constructor(
    private val contactDao: ContactDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val chatHistoryDao: ChatHistoryDao,
    private val orderDao: OrderDao
) {

    private val tag = "ThreadIdentityResolver"

    suspend fun resolveAndMerge(notification: WhatsAppNotification): ResolvedThreadIdentity {
        val incomingTitle = notification.title.trim()
        val sourceClassStr = notification.sourceClass.name
        val resolvedPhone = notification.resolvedPhoneNumber
            ?: ContactPhoneResolver.extractPhoneFromTitle(incomingTitle)
        val sbnTag = notification.sbnTag

        var matchedContactName: String? = null
        var matchReason: String? = null

        // ── Tier 1: Match by Normalized Phone Number ──────────────────────────
        if (!resolvedPhone.isNullOrBlank()) {
            // a. Check contacts_table by phone
            val contactByPhone = contactDao.getContactByPhone(resolvedPhone)
                ?: contactDao.getContactByPhoneContaining(resolvedPhone.takeLast(9))
            if (contactByPhone != null && !contactByPhone.contactName.equals(incomingTitle, ignoreCase = true)) {
                matchedContactName = contactByPhone.contactName
                matchReason = "Tier 1: contacts_table phone match ($resolvedPhone)"
            }

            // b. Check if an existing conversation's contact_name normalizes to this phone
            if (matchedContactName == null && !sbnTag.isNullOrBlank()) {
                val matchingConv = conversationMetadataDao.getConversationByNotificationTag(sbnTag)
                if (matchingConv != null && !matchingConv.contactName.equals(incomingTitle, ignoreCase = true)) {
                    val convPhone = ContactPhoneResolver.extractPhoneFromTitle(matchingConv.contactName)
                    if (convPhone == resolvedPhone) {
                        matchedContactName = matchingConv.contactName
                        matchReason = "Tier 1: conversation_metadata phone match (${matchingConv.contactName} -> $resolvedPhone)"
                    }
                }
            }
        }

        // ── Tier 2: Match by WhatsApp Thread Tag (sbn.tag) ─────────────────────
        if (matchedContactName == null && !sbnTag.isNullOrBlank()) {
            val convByTag = conversationMetadataDao.getConversationByNotificationTag(sbnTag)
            if (convByTag != null && !convByTag.contactName.equals(incomingTitle, ignoreCase = true)) {
                matchedContactName = convByTag.contactName
                matchReason = "Tier 2: WhatsApp sbn.tag match ($sbnTag)"
            }
        }

        // ── Merge Decision & Execution ─────────────────────────────────────────
        if (matchedContactName != null && !matchedContactName.equals(incomingTitle, ignoreCase = true)) {
            val oldName = matchedContactName
            // Determine the better unified name:
            // If oldName was a phone number and incomingTitle is a friendly name, prefer incomingTitle
            val oldIsPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(oldName) != null
            val newIsPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(incomingTitle) != null

            val unifiedName = if (oldIsPhone && !newIsPhone) {
                incomingTitle
            } else if (!oldIsPhone && newIsPhone) {
                oldName
            } else {
                incomingTitle
            }

            val retiringName = if (unifiedName == incomingTitle) oldName else incomingTitle

            Log.i(
                tag,
                "🔗 Merging fragmented thread: retiring='$retiringName' -> unified='$unifiedName' via $matchReason"
            )

            // 1. Reassign Chat History messages
            val messagesMoved = chatHistoryDao.reassignContactMessages(retiringName, unifiedName)

            // 2. Reassign Orders
            val ordersMoved = orderDao.reassignContactOrders(retiringName, unifiedName)

            // 3. Merge Conversation Metadata
            conversationMetadataDao.mergeConversationContacts(retiringName, unifiedName, sourceClassStr)

            // 4. Update / Reassign Contact in contacts_table
            val oldContact = contactDao.getContactByName(retiringName)
            val newContact = contactDao.getContactByName(unifiedName)
            val finalPhone = resolvedPhone ?: oldContact?.phoneNumber?.ifBlank { null } ?: ""

            if (oldContact != null && newContact == null) {
                contactDao.deleteContactByName(retiringName)
                contactDao.upsert(
                    oldContact.copy(
                        contactName = unifiedName,
                        phoneNumber = finalPhone,
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                )
            } else if (newContact != null) {
                if (finalPhone.isNotBlank()) {
                    contactDao.updatePhoneByName(unifiedName, finalPhone)
                }
                if (oldContact != null) {
                    contactDao.deleteContactByName(retiringName)
                }
            } else {
                contactDao.upsert(
                    ContactEntity(
                        contactName = unifiedName,
                        phoneNumber = finalPhone,
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                )
            }

            // 5. Update Notification Action Cache alias
            NotificationActionCache.aliasContact(sourceClassStr, retiringName, unifiedName)

            Log.i(
                tag,
                "✅ Merge complete: $messagesMoved messages and $ordersMoved orders reassigned to '$unifiedName'"
            )

            return ResolvedThreadIdentity(
                unifiedContactName = unifiedName,
                resolvedPhoneNumber = finalPhone.ifBlank { null },
                wasMerged = true,
                previousContactName = retiringName
            )
        }

        // No merge needed; ensure phone is recorded in contacts_table
        if (!resolvedPhone.isNullOrBlank()) {
            val existing = contactDao.getContactByName(incomingTitle)
            if (existing != null) {
                if (existing.phoneNumber.isBlank()) {
                    contactDao.updatePhoneByName(incomingTitle, resolvedPhone)
                }
            } else {
                contactDao.upsert(
                    ContactEntity(
                        contactName = incomingTitle,
                        phoneNumber = resolvedPhone,
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        return ResolvedThreadIdentity(
            unifiedContactName = incomingTitle,
            resolvedPhoneNumber = resolvedPhone,
            wasMerged = false
        )
    }
}
