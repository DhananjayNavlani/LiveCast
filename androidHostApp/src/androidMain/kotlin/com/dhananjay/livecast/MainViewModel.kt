package com.dhananjay.livecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.AuthRepository
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val remoteDataSource: RemoteDataSource,
    private val authRepository: AuthRepository
) : ViewModel() {

    fun addUser(user: LiveCastUser) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveUser(user)
            remoteDataSource.addUser(user)
            preferencesRepository.saveLoginStatus(true)
        }
    }

    fun getLoginStatus() = preferencesRepository.getLoginStatus()
    fun logout() {
        authRepository.signOut()
        viewModelScope.launch {
            preferencesRepository.clearUserInfo()
        }
    }


}