package com.kennyandries.ringtonesetter.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kennyandries.ringtonesetter.config.ManagedConfig
import com.kennyandries.ringtonesetter.config.ManagedConfigReader
import com.kennyandries.ringtonesetter.contacts.ContactRingtoneAssigner
import com.kennyandries.ringtonesetter.download.RingtoneDownloader
import com.kennyandries.ringtonesetter.ringtone.RingtoneRegistrar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RingtoneSetterViewModel(
    private val configReader: ManagedConfigReader,
    private val downloader: RingtoneDownloader,
    private val registrar: RingtoneRegistrar,
    private val assigner: ContactRingtoneAssigner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    companion object {
        private const val TAG = "RingtoneSetterVM"
    }

    private val _uiState = MutableStateFlow(RingtoneSetterUiState())
    val uiState: StateFlow<RingtoneSetterUiState> = _uiState.asStateFlow()

    fun refreshConfig() {
        val result = configReader.read()
        _uiState.update { state ->
            state.copy(
                configStatus = when (result) {
                    is ManagedConfig.Result.Valid -> ConfigStatus.Valid(
                        displayName = result.config.ringtoneDisplayName,
                        phoneNumberCount = result.config.contactPhoneNumbers.size,
                    )
                    is ManagedConfig.Result.Invalid -> ConfigStatus.Invalid(result.errors)
                },
                error = null,
                results = null,
            )
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = granted) }
    }

    fun applyRingtone() {
        val configResult = configReader.read()
        val config = when (configResult) {
            is ManagedConfig.Result.Valid -> configResult.config
            is ManagedConfig.Result.Invalid -> {
                _uiState.update {
                    it.copy(
                        configStatus = ConfigStatus.Invalid(configResult.errors),
                        error = "Configuration is invalid",
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operationPhase = OperationPhase.Downloading,
                    error = null,
                    results = null,
                )
            }

            try {
                val ringtoneUri = withContext(ioDispatcher) {
                    // Check disk space before starting
                    registrar.checkAvailableDiskSpace()

                    // Prepare MediaStore entry (guess MIME type, will be used for the entry)
                    val prepared = registrar.prepare(config.ringtoneDisplayName, "audio/mpeg")

                    try {
                        _uiState.update { it.copy(operationPhase = OperationPhase.Downloading) }

                        Log.d(TAG, "Starting ringtone download for '${config.ringtoneDisplayName}'")

                        // Download directly into MediaStore OutputStream
                        val downloadResult = prepared.outputStream.use { outputStream ->
                            downloader.download(config.ringtoneSasUrl, outputStream)
                        }

                        _uiState.update { it.copy(operationPhase = OperationPhase.Registering) }

                        // Finalize the MediaStore entry (clear IS_PENDING) before
                        // updating MIME type so the entry is no longer pending
                        registrar.finalize(prepared.uri)
                        registrar.updateMimeType(prepared.uri, downloadResult.mimeType)

                        Log.d(TAG, "Ringtone registered: ${downloadResult.bytesWritten} bytes, type=${downloadResult.mimeType}")

                        prepared.uri
                    } catch (e: Exception) {
                        registrar.cleanup(prepared.uri)
                        throw e
                    }
                }

                _uiState.update { it.copy(operationPhase = OperationPhase.Assigning) }

                val results = withContext(ioDispatcher) {
                    assigner.assign(config.contactPhoneNumbers, ringtoneUri)
                }

                val successCount = results.count { it.success }
                val failCount = results.size - successCount
                Log.d(TAG, "Assignment complete: $successCount succeeded, $failCount failed")

                val allFailed = results.all { !it.success }
                _uiState.update {
                    it.copy(
                        operationPhase = OperationPhase.Done,
                        results = results,
                        error = if (allFailed) {
                            "Ringtone registered but could not be assigned to any contacts"
                        } else null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Apply ringtone failed", e)
                _uiState.update {
                    it.copy(
                        operationPhase = OperationPhase.Idle,
                        error = sanitizeErrorMessage(e),
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                operationPhase = OperationPhase.Idle,
                error = null,
                results = null,
            )
        }
    }
}

/**
 * Strips potential SAS tokens and URL parameters from error messages
 * to prevent credential leakage in the UI.
 */
internal fun sanitizeErrorMessage(e: Exception): String {
    val message = e.message ?: return "Unknown error occurred"
    // Remove anything that looks like a URL query string (SAS tokens are query params)
    return message.replace(Regex("https?://[^\\s]+"), "[URL redacted]")
}
