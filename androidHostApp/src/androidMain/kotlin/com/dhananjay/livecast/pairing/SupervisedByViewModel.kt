package com.dhananjay.livecast.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.protocol.Supervisor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupervisedByViewModel(
    identity: DeviceIdentity,
    private val supervisorsDirectory: SupervisorDirectory
) : ViewModel() {

    private val _supervisors = MutableStateFlow<List<Supervisor>>(emptyList())
    val supervisors: StateFlow<List<Supervisor>> = _supervisors.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val supervisorNames: StateFlow<List<String>> = _supervisors
        .map { list -> list.map { it.name.ifEmpty { it.email ?: it.uid } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        val deviceId = identity.deviceId
        if (deviceId != null) {
            viewModelScope.launch {
                supervisorsDirectory.observeSupervisors(deviceId)
                    .catch { e ->
                        _error.value = e.message ?: "Failed to observe supervisors"
                        emit(emptyList())
                    }
                    .collect { list ->
                        _supervisors.value = list
                    }
            }
        }
    }
}
