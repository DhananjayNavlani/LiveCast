package com.dhananjay.livecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.session.WebRtcSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class MainViewModel(private val signalingClient: SignalingClient): ViewModel(), KoinComponent {
    private val _onCallScreen = MutableStateFlow(false)
    val onCallScreen = _onCallScreen.asStateFlow()

    private val _isSubscriber = MutableStateFlow(false)
    val isSubscriber = _isSubscriber.asStateFlow()

    fun updateOnCallScreen(isOnCall: Boolean) {
        _onCallScreen.update { isOnCall }
    }

    fun updateIsSubscriber(isSub: Boolean) {
        _isSubscriber.update { isSub }
    }

    fun addDeviceOnline() {
        viewModelScope.launch(Dispatchers.IO) {
            signalingClient.addDeviceOnline()
        }
    }


    fun removeDeviceOnline(){
        viewModelScope.launch(Dispatchers.IO) {
            signalingClient.removeDeviceOnline()
        }
    }


}