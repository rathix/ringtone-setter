package com.kennyandries.ringtonesetter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kennyandries.ringtonesetter.config.ManagedConfig
import com.kennyandries.ringtonesetter.config.ManagedConfigReader
import com.kennyandries.ringtonesetter.contacts.ContactRingtoneAssigner
import com.kennyandries.ringtonesetter.download.RingtoneDownloader
import com.kennyandries.ringtonesetter.ringtone.RingtoneRegistrar
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
) : ViewModel() {

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
                val (ringtoneUri, _) = withContext(Dispatchers.IO) {
                    // Prepare MediaStore entry (guess MIME type, will be used for the entry)
                    val prepared = registrar.prepare(config.ringtoneDisplayName, "audio/mpeg")

                    try {
                        _uiState.update { it.copy(operationPhase = OperationPhase.Downloading) }

                        // Download directly into MediaStore OutputStream
                        val downloadResult = prepared.outputStream.use { outputStream ->
                            downloader.download(config.ringtoneSasUrl, outputStream)
                        }

                        _uiState.update { it.copy(operationPhase = OperationPhase.Registering) }

                        // Finalize the MediaStore entry (clear IS_PENDING)
                        registrar.finalize(prepared.uri)

                        Pair(prepared.uri, downloadResult)
                    } catch (e: Exception) {
                        registrar.cleanup(prepared.uri)
                        throw e
                    }
                }

                _uiState.update { it.copy(operationPhase = OperationPhase.Assigning) }

                val results = withContext(Dispatchers.IO) {
                    assigner.assign(config.contactPhoneNumbers, ringtoneUri)
                }

                _uiState.update {
                    it.copy(
                        operationPhase = OperationPhase.Done,
                        results = results,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        operationPhase = OperationPhase.Idle,
                        error = e.message ?: "Unknown error occurred",
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
