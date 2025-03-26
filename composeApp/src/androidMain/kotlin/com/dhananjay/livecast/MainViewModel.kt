package com.dhananjay.livecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.AuthRepository
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(
    private val signalingClient: SignalingClient,
    private val preferencesRepository: PreferencesRepository,
    private val remoteDataSource: RemoteDataSource,
    private val authRepository: AuthRepository
) : ViewModel() {


    fun addDeviceOnline() {
        viewModelScope.launch(Dispatchers.IO) {
            signalingClient.addDeviceOnline()
        }
    }

    fun removeDeviceOnline() {
        viewModelScope.launch(Dispatchers.IO) {
            signalingClient.removeDeviceOnline()
        }
    }

    fun addUser(user: LiveCastUser) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveUser(user)
            remoteDataSource.addUser(user)
            preferencesRepository.saveLoginStatus(true)
        }
    }

    fun getLoginStatus() = preferencesRepository.getLoginStatus()


}