package com.example.ringtonesetter.viewmodel

import com.example.ringtonesetter.contacts.ContactRingtoneAssigner

data class RingtoneSetterUiState(
    val configStatus: ConfigStatus = ConfigStatus.Loading,
    val permissionsGranted: Boolean = false,
    val operationPhase: OperationPhase = OperationPhase.Idle,
    val error: String? = null,
    val results: List<ContactRingtoneAssigner.AssignmentResult>? = null,
)

sealed interface ConfigStatus {
    data object Loading : ConfigStatus
    data class Valid(
        val displayName: String,
        val phoneNumberCount: Int,
    ) : ConfigStatus
    data class Invalid(val errors: List<String>) : ConfigStatus
}

enum class OperationPhase(val label: String) {
    Idle(""),
    Downloading("Downloading ringtone…"),
    Registering("Registering ringtone…"),
    Assigning("Assigning to contacts…"),
    Done("Complete"),
}
