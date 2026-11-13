package com.dhananjay.livecast.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.Supervisor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun visibleRequests(
    pending: List<PairingRequest>,
    current: PairingCode?,
    nowEpochMs: Long
): List<PairingRequest> {
    if (current == null) return emptyList()
    if (nowEpochMs >= current.expiresAtEpochMs) return emptyList()
    return pending.filter {
        it.code == current.code && it.createdAtEpochMs >= current.createdAtEpochMs
    }
}

data class HostPairingUiState(
    val code: PairingCode? = null,
    val remainingMs: Long = 0,
    val requests: List<PairingRequest> = emptyList(),
    val supervisors: List<Supervisor> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false
)

class HostPairingViewModel(
    private val identity: DeviceIdentity,
    private val deviceInfo: HostDeviceInfoProvider,
    private val registry: DeviceRegistry,
    private val codes: PairingCodeIssuer,
    private val inbox: PairingRequestInbox,
    private val supervisors: SupervisorDirectory,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostPairingUiState())
    val uiState: StateFlow<HostPairingUiState> = _uiState.asStateFlow()

    private var rawPendingRequests: List<PairingRequest> = emptyList()

    init {
        val deviceId = identity.deviceId
        if (deviceId != null) {
            viewModelScope.launch {
                supervisors.observeSupervisors(deviceId)
                    .catch { e ->
                        _uiState.update { it.copy(error = e.message ?: "Failed to observe supervisors") }
                    }
                    .collect { supervisorsList ->
                        _uiState.update { it.copy(supervisors = supervisorsList) }
                    }
            }
            viewModelScope.launch {
                inbox.observePendingRequests(deviceId)
                    .catch { e ->
                        _uiState.update { it.copy(error = e.message ?: "Failed to observe pairing requests") }
                    }
                    .collect { pending ->
                        rawPendingRequests = pending
                        val currentCode = _uiState.value.code
                        val now = clock()
                        _uiState.update {
                            it.copy(requests = visibleRequests(pending, currentCode, now))
                        }
                    }
            }
        }
    }

    fun generateCode() {
        val deviceId = identity.deviceId ?: run {
            _uiState.update { it.copy(error = "Not authenticated") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            val device = deviceInfo.current(deviceId)
            val upsertResult = registry.upsertDevice(device)
            if (upsertResult.isFailure) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = upsertResult.exceptionOrNull()?.message ?: "Failed to upsert device"
                    )
                }
                return@launch
            }
            codes.createCode(deviceId).fold(
                onSuccess = { newCode ->
                    val now = clock()
                    val remaining = maxOf(0L, newCode.expiresAtEpochMs - now)
                    _uiState.update {
                        it.copy(
                            code = newCode,
                            remainingMs = remaining,
                            requests = visibleRequests(rawPendingRequests, newCode, now),
                            busy = false,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = err.message ?: "Failed to generate pairing code"
                        )
                    }
                }
            )
        }
    }

    fun approve(request: PairingRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            inbox.approve(request).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            code = null,
                            remainingMs = 0L,
                            requests = emptyList()
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = err.message ?: "Failed to approve request"
                        )
                    }
                }
            )
        }
    }

    fun deny(request: PairingRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            inbox.deny(request).fold(
                onSuccess = {
                    _uiState.update { it.copy(busy = false) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = err.message ?: "Failed to deny request"
                        )
                    }
                }
            )
        }
    }

    fun tick() {
        val code = _uiState.value.code ?: return
        val now = clock()
        val remaining = maxOf(0L, code.expiresAtEpochMs - now)
        _uiState.update {
            it.copy(
                remainingMs = remaining,
                requests = visibleRequests(rawPendingRequests, code, now)
            )
        }
    }
}
