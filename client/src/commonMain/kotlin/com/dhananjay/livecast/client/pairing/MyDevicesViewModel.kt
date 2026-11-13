package com.dhananjay.livecast.client.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MyDevicesViewModel(
    private val devicesRepository: SupervisedDeviceRepository,
    private val session: SessionProvider
) : ViewModel() {

    private val _devices = MutableStateFlow<List<HostDevice>>(emptyList())
    val devices: StateFlow<List<HostDevice>> = _devices.asStateFlow()

    private val _devicesError = MutableStateFlow<String?>(null)
    val devicesError: StateFlow<String?> = _devicesError.asStateFlow()

    init {
        viewModelScope.launch {
            session.authState.flatMapLatest { authState ->
                _devicesError.value = null
                if (authState is AuthState.Authenticated) {
                    devicesRepository.observeMyDevices()
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

    fun remove(deviceId: String) {
        viewModelScope.launch {
            devicesRepository.removeDevice(deviceId).onFailure { e ->
                _devicesError.value = e.message ?: "Failed to remove device"
            }
        }
    }
}
