package com.dhananjay.livecast.client.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.protocol.AppEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceDetailUiState(
    val events: List<AppEvent> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DeviceDetailViewModel(
    deviceId: String,
    groupId: String,
    events: AppEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceDetailUiState())
    val state: StateFlow<DeviceDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            events.observeRecent(deviceId, groupId)
                .catch { cause ->
                    val errorMsg = if (cause is GroupError.AccessLost) {
                        "You no longer have access"
                    } else {
                        cause.message ?: "Unknown error"
                    }
                    _state.update { it.copy(loading = false, error = errorMsg) }
                }
                .collect { list ->
                    _state.update { it.copy(loading = false, events = list, error = null) }
                }
        }
    }
}
