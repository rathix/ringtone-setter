package com.kennyandries.ringtonesetter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kennyandries.ringtonesetter.contacts.ContactRingtoneAssigner
import com.kennyandries.ringtonesetter.ui.theme.Green700
import com.kennyandries.ringtonesetter.ui.theme.Red700
import com.kennyandries.ringtonesetter.viewmodel.ConfigStatus
import com.kennyandries.ringtonesetter.viewmodel.OperationPhase
import com.kennyandries.ringtonesetter.viewmodel.RingtoneSetterUiState
import com.kennyandries.ringtonesetter.viewmodel.RingtoneSetterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSetterScreen(
    viewModel: RingtoneSetterViewModel,
    onRequestPermissions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ringtone Setter") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConfigCard(state.configStatus)
            PermissionsCard(state.permissionsGranted, onRequestPermissions)

            if (state.error != null) {
                ErrorCard(state.error!!, onDismiss = { viewModel.dismissError() })
            }

            if (state.operationPhase != OperationPhase.Idle && state.operationPhase != OperationPhase.Done) {
                ProgressCard(state.operationPhase)
            }

            if (state.results != null) {
                ResultsCard(state.results!!)
            }

            ApplyButton(state, onApply = { viewModel.applyRingtone() }, onReset = { viewModel.reset() })
        }
    }
}

@Composable
private fun ConfigCard(status: ConfigStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            when (status) {
                is ConfigStatus.Loading -> Text("Loading configuration…")
                is ConfigStatus.Valid -> {
                    Text("Ringtone: ${status.displayName}")
                    Text("Contacts: ${status.phoneNumberCount} phone number(s)")
                }
                is ConfigStatus.Invalid -> {
                    status.errors.forEach { error ->
                        Text(error, color = Red700)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(granted: Boolean, onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (granted) {
                Text("All permissions granted", color = Green700)
            } else {
                Text("Contact permissions are required")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRequest) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun ProgressCard(phase: OperationPhase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(phase.label)
        }
    }
}

@Composable
private fun ResultsCard(results: List<ContactRingtoneAssigner.AssignmentResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Results", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            results.forEach { result ->
                val statusText = if (result.success) "OK" else "FAIL"
                val statusColor = if (result.success) Green700 else Red700
                val nameText = result.contactName ?: result.phoneNumber
                val detail = if (!result.success && result.error != null) " — ${result.error}" else ""
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("$nameText$detail", modifier = Modifier.weight(1f))
                    Text(statusText, color = statusColor)
                }
            }
        }
    }
}

@Composable
private fun ApplyButton(
    state: RingtoneSetterUiState,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val isConfigValid = state.configStatus is ConfigStatus.Valid
    val isIdle = state.operationPhase == OperationPhase.Idle
    val isDone = state.operationPhase == OperationPhase.Done
    val canApply = isConfigValid && state.permissionsGranted && isIdle

    if (isDone) {
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done — Tap to Reset")
        }
    } else {
        Button(
            onClick = onApply,
            enabled = canApply,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply Ringtone")
        }
    }
}
