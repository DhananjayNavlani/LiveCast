package com.dhananjay.livecast.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HostPresenceViewModel(
    identity: DeviceIdentity,
    heartbeat: PresenceHeartbeat
) : ViewModel() {

    init {
        val deviceId = identity.deviceId
        if (deviceId != null) {
            viewModelScope.launch {
                heartbeat.run(deviceId)
            }
        }
    }
}
