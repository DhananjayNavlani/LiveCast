package com.dhananjay.livecast.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.protocol.HostDevice
import com.dhananjay.livecast.protocol.PairingCode
import com.dhananjay.livecast.protocol.PairingRequest
import com.dhananjay.livecast.protocol.Supervisor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val repo: HostPairingRepository,
    private val auth: FirebaseAuth? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val deviceIdProvider: () -> String? = { auth?.currentUser?.uid },
    private val deviceProvider: (deviceId: String) -> HostDevice = { id ->
        val model = runCatching { Build.MODEL }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android"
        HostDevice(
            id = id,
            displayName = model,
            model = model
        )
    }
) : ViewModel() {

    constructor(
        repo: HostPairingRepository,
        auth: FirebaseAuth,
        clock: () -> Long = System::currentTimeMillis
    ) : this(
        repo = repo,
        auth = auth as FirebaseAuth?,
        clock = clock,
        deviceIdProvider = { auth.currentUser?.uid }
    )

    constructor(
        repo: HostPairingRepository,
        auth: FirebaseAuth,
        deviceProvider: (deviceId: String) -> HostDevice
    ) : this(
        repo = repo,
        auth = auth as FirebaseAuth?,
        clock = { System.currentTimeMillis() },
        deviceIdProvider = { auth.currentUser?.uid },
        deviceProvider = deviceProvider
    )

    private val _uiState = MutableStateFlow(HostPairingUiState())
    val uiState: StateFlow<HostPairingUiState> = _uiState.asStateFlow()

    private var rawPendingRequests: List<PairingRequest> = emptyList()

    init {
        val deviceId = deviceIdProvider()
        if (deviceId != null) {
            viewModelScope.launch {
                repo.observeSupervisors(deviceId).collect { supervisors ->
                    _uiState.update { it.copy(supervisors = supervisors) }
                }
            }
            viewModelScope.launch {
                repo.observePendingRequests(deviceId).collect { pending ->
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
        val deviceId = deviceIdProvider() ?: run {
            _uiState.update { it.copy(error = "Not authenticated") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            val device = deviceProvider(deviceId)
            val upsertResult = repo.upsertDevice(device)
            if (upsertResult.isFailure) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = upsertResult.exceptionOrNull()?.message ?: "Failed to upsert device"
                    )
                }
                return@launch
            }
            repo.createCode(deviceId).fold(
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
            repo.approve(request).fold(
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
            repo.deny(request).fold(
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
