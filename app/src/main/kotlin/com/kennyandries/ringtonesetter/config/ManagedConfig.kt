package com.kennyandries.ringtonesetter.config

import java.net.URI

data class ManagedConfig(
    val ringtoneSasUrl: String,
    val contactPhoneNumbers: List<String>,
    val ringtoneDisplayName: String,
) {
    companion object {
        private val ALLOWED_HOSTS = listOf(".blob.core.windows.net")

        fun validate(
            sasUrl: String?,
            phoneNumbers: String?,
            displayName: String?,
        ): Result {
            val errors = mutableListOf<String>()

            if (sasUrl.isNullOrBlank()) {
                errors += "Ringtone SAS URL is not configured"
            } else {
                try {
                    val uri = URI(sasUrl)
                    if (uri.scheme?.lowercase() != "https") {
                        errors += "Ringtone SAS URL must use HTTPS"
                    }
                    val host = uri.host?.lowercase()
                    if (host == null || ALLOWED_HOSTS.none { host.endsWith(it) }) {
                        errors += "Ringtone SAS URL must point to Azure Blob Storage (*.blob.core.windows.net)"
                    }
                } catch (_: Exception) {
                    errors += "Ringtone SAS URL is not a valid URL"
                }
            }

            val numbers = phoneNumbers
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            if (numbers.isEmpty()) {
                errors += "No contact phone numbers configured"
            }

            val invalidNumbers = numbers.filter { !it.matches(Regex("^\\+[1-9]\\d{1,14}$")) }
            if (invalidNumbers.isNotEmpty()) {
                errors += "Invalid E.164 phone numbers: ${invalidNumbers.joinToString()}"
            }

            return if (errors.isNotEmpty()) {
                Result.Invalid(errors)
            } else {
                Result.Valid(
                    ManagedConfig(
                        ringtoneSasUrl = sasUrl!!,
                        contactPhoneNumbers = numbers,
                        ringtoneDisplayName = displayName?.takeIf { it.isNotBlank() }
                            ?: "Enterprise Ringtone",
                    )
                )
            }
        }
    }

    sealed interface Result {
        data class Valid(val config: ManagedConfig) : Result
        data class Invalid(val errors: List<String>) : Result
    }
}
