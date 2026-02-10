package com.kennyandries.ringtonesetter.config

import android.content.Context
import android.content.RestrictionsManager

class ManagedConfigReader(private val context: Context) {

    fun read(): ManagedConfig.Result {
        val manager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val restrictions = manager.applicationRestrictions

        val sasUrl = restrictions.getString("ringtone_sas_url")
        val phoneNumbers = restrictions.getString("contact_phone_numbers")
        val displayName = restrictions.getString("ringtone_display_name")

        return ManagedConfig.validate(sasUrl, phoneNumbers, displayName)
    }
}
