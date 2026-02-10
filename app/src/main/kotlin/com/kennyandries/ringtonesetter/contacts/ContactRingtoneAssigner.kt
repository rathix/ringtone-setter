package com.kennyandries.ringtonesetter.contacts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

class ContactRingtoneAssigner(private val context: Context) {

    data class AssignmentResult(
        val phoneNumber: String,
        val success: Boolean,
        val contactName: String?,
        val error: String? = null,
    )

    /**
     * Assigns the ringtone at [ringtoneUri] to all contacts matching [phoneNumbers].
     * Each number is processed independently; one failure doesn't block others.
     */
    fun assign(phoneNumbers: List<String>, ringtoneUri: Uri): List<AssignmentResult> {
        return phoneNumbers.map { number ->
            try {
                assignToNumber(number, ringtoneUri)
            } catch (e: Exception) {
                AssignmentResult(
                    phoneNumber = number,
                    success = false,
                    contactName = null,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }

    private fun assignToNumber(phoneNumber: String, ringtoneUri: Uri): AssignmentResult {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
        )

        val cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
            ?: return AssignmentResult(phoneNumber, false, null, "Query returned null")

        cursor.use {
            if (!it.moveToFirst()) {
                return AssignmentResult(phoneNumber, false, null, "Contact not found")
            }

            val contactId = it.getLong(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
            val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))

            val contactUri = ContentValues().apply {
                put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
            }

            val updated = context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                contactUri,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
            )

            return if (updated > 0) {
                AssignmentResult(phoneNumber, true, displayName)
            } else {
                AssignmentResult(phoneNumber, false, displayName, "Failed to update contact")
            }
        }
    }
}
