package com.kennyandries.ringtonesetter.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedConfigTest {

    @Test
    fun `validate returns invalid when sas url missing`() {
        val result = ManagedConfig.validate(
            sasUrl = null,
            phoneNumbers = "+14155552671",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.contains("Ringtone SAS URL is not configured"))
    }

    @Test
    fun `validate returns invalid when sas url uses http`() {
        val result = ManagedConfig.validate(
            sasUrl = "http://myaccount.blob.core.windows.net/container/file.mp3?sv=2020",
            phoneNumbers = "+14155552671",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.contains("Ringtone SAS URL must use HTTPS"))
    }

    @Test
    fun `validate returns invalid when sas url is not azure domain`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://evil.example.com/ringtone.mp3",
            phoneNumbers = "+14155552671",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.any { it.contains("Azure Blob Storage") })
    }

    @Test
    fun `validate returns invalid for malformed url`() {
        val result = ManagedConfig.validate(
            sasUrl = "not a url at all",
            phoneNumbers = "+14155552671",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.any { it.contains("not a valid URL") })
    }

    @Test
    fun `validate returns invalid when phone numbers missing`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://myaccount.blob.core.windows.net/container/file.mp3",
            phoneNumbers = " ",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.contains("No contact phone numbers configured"))
    }

    @Test
    fun `validate returns invalid when phone numbers are not e164`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://myaccount.blob.core.windows.net/container/file.mp3",
            phoneNumbers = "+14155552671,12345",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.any { it.startsWith("Invalid E.164 phone numbers:") })
    }

    @Test
    fun `validate returns valid config with azure https url and default display name`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://myaccount.blob.core.windows.net/container/file.mp3?sv=2020&sig=abc",
            phoneNumbers = "+14155552671,+12125551234",
            displayName = "",
        )

        val valid = result as ManagedConfig.Result.Valid
        assertEquals(
            "https://myaccount.blob.core.windows.net/container/file.mp3?sv=2020&sig=abc",
            valid.config.ringtoneSasUrl,
        )
        assertEquals(listOf("+14155552671", "+12125551234"), valid.config.contactPhoneNumbers)
        assertEquals("Enterprise Ringtone", valid.config.ringtoneDisplayName)
    }
}
