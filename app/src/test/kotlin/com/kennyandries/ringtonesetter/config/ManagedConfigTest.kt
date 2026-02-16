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
    fun `validate returns invalid when phone numbers missing`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://example.com/test.mp3",
            phoneNumbers = " ",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.contains("No contact phone numbers configured"))
    }

    @Test
    fun `validate returns invalid when phone numbers are not e164`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://example.com/test.mp3",
            phoneNumbers = "+14155552671,12345",
            displayName = "Test Ringtone",
        )

        val invalid = result as ManagedConfig.Result.Invalid
        assertTrue(invalid.errors.any { it.startsWith("Invalid E.164 phone numbers:") })
    }

    @Test
    fun `validate returns valid config and default display name`() {
        val result = ManagedConfig.validate(
            sasUrl = "https://example.com/test.mp3",
            phoneNumbers = "+14155552671,+12125551234",
            displayName = "",
        )

        val valid = result as ManagedConfig.Result.Valid
        assertEquals("https://example.com/test.mp3", valid.config.ringtoneSasUrl)
        assertEquals(listOf("+14155552671", "+12125551234"), valid.config.contactPhoneNumbers)
        assertEquals("Enterprise Ringtone", valid.config.ringtoneDisplayName)
    }
}
