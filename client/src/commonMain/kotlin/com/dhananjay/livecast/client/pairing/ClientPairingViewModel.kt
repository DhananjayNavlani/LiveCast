package com.dhananjay.livecast.client.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.AuthService
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.PairingRules
import com.dhananjay.livecast.protocol.PairingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

sealed interface AddDeviceState {
    data class Entering(val code: String = "", val error: String? = null) : AddDeviceState
    data object Submitting : AddDeviceState
    data class Waiting(val request: PairingRequest, val expiresAtEpochMs: Long) : AddDeviceState
    data class Approved(val deviceId: String) : AddDeviceState
    data object Denied : AddDeviceState
    data object Expired : AddDeviceState
}

@OptIn(kotlin.time.ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class ClientPairingViewModel(
    private val repo: ClientPairingRepository,
    private val auth: AuthService,
    private val clock: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private val _addState = MutableStateFlow<AddDeviceState>(AddDeviceState.Entering())
    val addState: StateFlow<AddDeviceState> = _addState.asStateFlow()

    private val _devices = MutableStateFlow<List<HostDevice>>(emptyList())
    val devices: StateFlow<List<HostDevice>> = _devices.asStateFlow()

    private val _devicesError = MutableStateFlow<String?>(null)
    val devicesError: StateFlow<String?> = _devicesError.asStateFlow()

    private var waitingJob: Job? = null

    init {
        viewModelScope.launch {
            auth.authState.flatMapLatest { authState ->
                _devicesError.value = null
                if (authState is AuthState.Authenticated) {
                    repo.observeMyDevices()
                        .catch { e ->
                            _devices.value = emptyList()
                            _devicesError.value = e.message ?: "Failed to load devices"
                        }
                } else {
                    flowOf(emptyList())
                }
            }.collect { list ->
                _devices.value = list
            }
        }
    }

    fun onCodeChanged(input: String) {
        val digits = input.filter { it.isDigit() }.take(6)
        _addState.value = AddDeviceState.Entering(code = digits, error = null)
    }

    fun submit() {
        val current = _addState.value
        if (current is AddDeviceState.Submitting || current is AddDeviceState.Waiting) {
            return
        }

        val code = when (current) {
            is AddDeviceState.Entering -> current.code
            else -> ""
        }

        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            _addState.value = AddDeviceState.Entering(
                code = code,
                error = "Sign in with an account to add a device"
            )
            return
        }

        if (!PairingRules.isValidCode(code)) {
            _addState.value = AddDeviceState.Entering(
                code = code,
                error = "Code invalid or expired"
            )
            return
        }

        _addState.value = AddDeviceState.Submitting

        viewModelScope.launch {
            repo.submitCode(code).fold(
                onSuccess = { submitted ->
                    _addState.value = AddDeviceState.Waiting(
                        request = submitted.request,
                        expiresAtEpochMs = submitted.codeExpiresAtEpochMs
                    )
                    startWaiting(submitted.request, submitted.codeExpiresAtEpochMs)
                },
                onFailure = { err ->
                    _addState.value = AddDeviceState.Entering(
                        code = code,
                        error = err.message ?: "Code invalid or expired"
                    )
                }
            )
        }
    }

    private fun startWaiting(request: PairingRequest, expiresAtEpochMs: Long) {
        waitingJob?.cancel()
        waitingJob = viewModelScope.launch {
            launch {
                val delayMs = (expiresAtEpochMs - clock()).coerceAtLeast(0L)
                delay(delayMs)
                _addState.value = AddDeviceState.Expired
                waitingJob?.cancel()
            }

            launch {
                repo.observeRequest(request.id)
                    .catch { e ->
                        _addState.value = AddDeviceState.Entering(
                            code = request.code,
                            error = e.message ?: "Request failed"
                        )
                        waitingJob?.cancel()
                    }
                    .collect { updated ->
                        if (updated != null) {
                            when (updated.status) {
                                PairingStatus.APPROVED -> {
                                    _addState.value = AddDeviceState.Approved(updated.deviceId)
                                    waitingJob?.cancel()
                                }
                                PairingStatus.DENIED -> {
                                    _addState.value = AddDeviceState.Denied
                                    waitingJob?.cancel()
                                }
                                PairingStatus.PENDING -> {
                                    // Continue waiting
                                }
                            }
                        }
                    }
            }
        }
    }

    fun reset() {
        waitingJob?.cancel()
        waitingJob = null
        _addState.value = AddDeviceState.Entering()
    }

    fun remove(deviceId: String) {
        viewModelScope.launch {
            repo.removeDevice(deviceId).onFailure { e ->
                _devicesError.value = e.message ?: "Failed to remove device"
            }
        }
    }
}
