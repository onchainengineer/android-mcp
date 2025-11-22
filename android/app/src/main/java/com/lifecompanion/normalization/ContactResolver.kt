package com.lifecompanion.normalization

import com.lifecompanion.data.*
import timber.log.Timber

/**
 * Resolves senders (from notifications, SMS) to unified contact records.
 * Handles fuzzy matching and creates new contacts when needed.
 */
class ContactResolver(private val db: LifeCompanionDatabase) {

    /**
     * Resolve app + sender name to a Contact
     */
    suspend fun resolve(app: String, senderName: String): ContactEntity? {
        // Skip system/promotional senders
        if (isSystemSender(senderName)) return null

        // Try exact match first
        db.contactDao().findByHandle(app, senderName)?.let { return it }

        // Try phone number extraction
        extractPhoneNumber(senderName)?.let { phone ->
            db.contactDao().findByPhone(phone)?.let { return it }
        }

        // Try fuzzy name matching
        val candidates = db.contactDao().findSimilarNames("%$senderName%")
        if (candidates.size == 1) return candidates.first()

        // No match - create new contact
        return createContact(app, senderName)
    }

    private suspend fun createContact(app: String, senderName: String): ContactEntity {
        val contact = ContactEntity(
            name = senderName,
            phone = extractPhoneNumber(senderName),
            email = null,
            photoUri = null,
            userTag = null,
            userPriority = 5,
            userNotes = null,
            createdAt = System.currentTimeMillis(),
            lastInteractionAt = System.currentTimeMillis()
        )

        val contactId = db.contactDao().insert(contact)
        Timber.d("Created new contact: $senderName (ID: $contactId)")

        // Add handle
        db.contactDao().insertHandle(
            ContactHandleEntity(
                contactId = contactId,
                app = app,
                handle = senderName,
                verifiedAt = null
            )
        )

        return contact.copy(id = contactId)
    }

    private fun isSystemSender(sender: String): Boolean {
        val systemPatterns = listOf(
            "^[A-Z]{2}-[A-Z]+$",  // AD-AMAZON, VM-HDFC
            "^\\d{5,6}$",          // Short codes
            "NOTIF",
            "UPDATE",
            "INFO"
        )
        return systemPatterns.any { Regex(it).matches(sender) }
    }

    private fun extractPhoneNumber(text: String): String? {
        val phonePattern = Regex("\\+?\\d{10,15}")
        return phonePattern.find(text)?.value
    }
}
