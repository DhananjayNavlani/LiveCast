package com.dhananjay.livecast

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel: ViewModel() {
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


}