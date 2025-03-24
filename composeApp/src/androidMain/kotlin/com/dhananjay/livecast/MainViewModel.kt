package com.dhananjay.livecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(private val signalingClient: SignalingClient): ViewModel() {


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